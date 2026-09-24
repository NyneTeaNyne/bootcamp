package com.mentoring.bootcamp.ordermanager.api.tests.integration.controllers;

import com.mentoring.bootcamp.ordermanager.api.controllers.dtos.CreateOrderRequest.IdReference;
import com.mentoring.bootcamp.ordermanager.api.controllers.dtos.CreateOrderRequest.ItemLine;
import com.mentoring.bootcamp.ordermanager.api.controllers.dtos.CreateOrderRequest;
import com.mentoring.bootcamp.ordermanager.api.entities.StatusTypeEnum;
import com.mentoring.bootcamp.ordermanager.api.tests.integration.config.JwtTestTokens;
import com.mentoring.bootcamp.ordermanager.api.tests.integration.config.PostgresContextInitializer;
import com.mentoring.bootcamp.ordermanager.api.tests.integration.config.ValkeyContextInitializer;
import com.mentoring.bootcamp.ordermanager.common.mail.MailUseCase;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Named;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.cache.CacheManager;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.MailSendException;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;

@EnabledIf("com.mentoring.bootcamp.ordermanager.api.tests.integration.config.PostgresContextInitializer#isDockerAvailable")
@ContextConfiguration(initializers = {PostgresContextInitializer.class, ValkeyContextInitializer.class})
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.flyway.enabled=true",
        "spring.flyway.baseline-on-migrate=false",
        "spring.jpa.open-in-view=false",
        "spring.jpa.show-sql=false"
})
class OrderDatabaseTests {
    @LocalServerPort
    private int port;

    @Value("${server.servlet.context-path}")
    private String contextPath;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private CacheManager cacheManager;

    @Autowired
    private JWKSource<SecurityContext> jwkSource;

    @Autowired
    private ObjectMapper objectMapper;

    // No real SMTP server here: the mail is faked so we can simulate a failure
    @MockitoBean
    private MailUseCase mailUseCase;

    @BeforeEach
    void prepareIsolatedDatabase() {
        assertEquals("orders_test", jdbc.queryForObject("SELECT current_database()", String.class));
        jdbc.execute("TRUNCATE order_items, orders, items, users RESTART IDENTITY");
        // Tables are emptied behind the application's back: cached orders would be stale
        cacheManager.getCacheNames().forEach(name -> cacheManager.getCache(name).clear());
        jdbc.update("INSERT INTO users(username, email) VALUES ('alice', 'alice@decathlon.com')");
        jdbc.update("INSERT INTO items(product_name, price) VALUES ('First item', 10.10), ('Second item', 20.20)");
    }

    @Test
    void createsAndReadsOrderWithFullDetailsAndCalculatedTotal() throws Exception {
        HttpResponse<String> created = create(1, 1, 2);
        assertEquals(201, created.statusCode(), created.body());
        JsonNode body = objectMapper.readTree(created.body());
        int id = body.path("id").asInt();
        assertTrue(id > 0);
        assertEquals(1, body.path("customer").path("id").asInt());
        assertEquals("alice", body.path("customer").path("username").asString());
        assertEquals("alice@decathlon.com", body.path("customer").path("email").asString());
        assertEquals(2, body.path("items").size());
        assertEquals("First item", body.path("items").get(0).path("productName").asString());
        assertEquals(1, body.path("items").get(0).path("quantity").asInt());
        assertEquals(1, body.path("items").get(1).path("quantity").asInt());
        assertEquals("PENDING", body.path("status").asString());
        assertFalse(body.path("orderDate").isNull());
        assertEquals(0, new BigDecimal("30.30").compareTo(new BigDecimal(body.path("totalAmount").asString())));
        assertEquals(2, count("order_items"));
        assertEquals(2, jdbc.queryForObject(
                "SELECT count(*) FROM order_items WHERE order_id = ? AND quantity = 1", Integer.class, id));

        HttpResponse<String> fetched = request("GET", "/orders/" + id, null);
        assertEquals(200, fetched.statusCode(), fetched.body());
        assertEquals(1, objectMapper.readTree(fetched.body()).path("items").get(0).path("quantity").asInt());
        assertEquals(0, new BigDecimal("30.30").compareTo(
                new BigDecimal(objectMapper.readTree(fetched.body()).path("totalAmount").asString())));

        HttpResponse<String> list = request("GET", "/orders", null);
        assertEquals(200, list.statusCode());
        JsonNode orders = objectMapper.readTree(list.body());
        assertEquals(1, orders.size());
        assertEquals(id, orders.get(0).path("id").asInt());
        assertEquals(1, orders.get(0).path("items").get(0).path("quantity").asInt());
    }

    @Test
    void responsesUseStoredQuantityRatherThanAConstant() throws Exception {
        HttpResponse<String> created = create(1, 1);
        assertEquals(201, created.statusCode(), created.body());
        int id = objectMapper.readTree(created.body()).path("id").asInt();
        jdbc.update("UPDATE order_items SET quantity = 3 WHERE order_id = ? AND item_id = 1", id);

        for (String path : new String[]{"/orders/" + id, "/orders"}) {
            HttpResponse<String> response = request("GET", path, null);
            assertEquals(200, response.statusCode(), response.body());
            JsonNode body = objectMapper.readTree(response.body());
            JsonNode order = body.isArray() ? body.get(0) : body;
            JsonNode item = order.path("items").get(0);
            assertEquals(1, item.path("id").asInt());
            assertEquals("First item", item.path("productName").asString());
            assertEquals(3, item.path("quantity").asInt());
            assertEquals(0, new BigDecimal("10.10").compareTo(new BigDecimal(item.path("price").asString())));
            assertEquals(0, new BigDecimal("30.30").compareTo(
                    new BigDecimal(order.path("totalAmount").asString())));
        }

        HttpResponse<String> catalog = request("GET", "/items/1", null);
        assertEquals(200, catalog.statusCode());
        assertFalse(objectMapper.readTree(catalog.body()).has("quantity"));
    }

    @Test
    void deletionRemovesOnlyOrderAndItsLinks() throws Exception {
        HttpResponse<String> created = create(1, 1, 2);
        assertEquals(201, created.statusCode(), created.body());
        int id = objectMapper.readTree(created.body()).path("id").asInt();

        HttpResponse<String> deleted = request("DELETE", "/orders/" + id, null);
        assertEquals(204, deleted.statusCode(), deleted.body());
        assertEquals("", deleted.body());
        assertEquals(0, count("orders"));
        assertEquals(0, count("order_items"));
        assertEquals(1, count("users"));
        assertEquals(2, count("items"));
        assertEquals(404, request("GET", "/orders/" + id, null).statusCode());
        assertEquals(404, request("DELETE", "/orders/" + id, null).statusCode());
    }

    @Test
    void missingReferencesDoNotCreatePartialOrders() throws Exception {
        assertEquals(400, create(999, 1).statusCode());
        assertEquals(400, create(1, 1, 999).statusCode());
        assertEquals(0, count("orders"));
        assertEquals(0, count("order_items"));
    }

    @ParameterizedTest
    @MethodSource("invalidOrderRequests")
    void invalidRequestsReturnSafeBusinessErrors(CreateOrderRequest invalid) throws Exception {
        HttpResponse<String> response = request("POST", "/orders", objectMapper.writeValueAsString(invalid));
        assertEquals(400, response.statusCode(), response.body());
        assertFalse(objectMapper.readTree(response.body()).has("trace"));
        assertEquals(0, count("orders"));
        assertEquals(0, count("order_items"));
    }

    @Test
    void rollsBackEntireOrderWhenLinePersistenceFails() throws Exception {
        jdbc.execute("ALTER TABLE order_items ADD CONSTRAINT test_reject_second_item CHECK (item_id <> 2)");
        try {
            HttpResponse<String> response = create(1, 1, 2);
            assertEquals(409, response.statusCode(), response.body());
            assertFalse(response.body().contains("test_reject_second_item"));
            assertFalse(objectMapper.readTree(response.body()).has("trace"));
            assertEquals(0, count("orders"));
            assertEquals(0, count("order_items"));
        } finally {
            jdbc.execute("ALTER TABLE order_items DROP CONSTRAINT test_reject_second_item");
        }
    }

    @Test
    void cancelsOrderWhenConfirmationMailCannotBeSent() throws Exception {
        doThrow(new MailSendException("private SMTP details"))
                .when(mailUseCase).sendMail(anyString(), anyString(), anyString());

        HttpResponse<String> response = create(1, 1, 2);

        assertEquals(500, response.statusCode(), response.body());
        assertFalse(response.body().contains("private SMTP details"));
        assertEquals(0, count("orders"));
        assertEquals(0, count("order_items"));
    }

    @ParameterizedTest
    @EnumSource(StatusTypeEnum.class)
    void readsPostgresEnumValues(StatusTypeEnum status) throws Exception {
        HttpResponse<String> created = create(1, 1);
        assertEquals(201, created.statusCode(), created.body());
        int id = objectMapper.readTree(created.body()).path("id").asInt();
        jdbc.update("UPDATE orders SET status = CAST(? AS status_type) WHERE id = ?", status.name(), id);

        HttpResponse<String> response = request("GET", "/orders/" + id, null);
        assertEquals(200, response.statusCode(), response.body());
        assertEquals(status.name(), objectMapper.readTree(response.body()).path("status").asString());
    }

    @Test
    void emptyListAndMissingOrderHaveCorrectStatuses() throws Exception {
        HttpResponse<String> empty = request("GET", "/orders", null);
        assertEquals(200, empty.statusCode());
        assertEquals(0, objectMapper.readTree(empty.body()).size());
        for (String method : new String[]{"GET", "DELETE"}) {
            HttpResponse<String> response = request(method, "/orders/999?trace=true", null);
            assertEquals(404, response.statusCode());
            JsonNode body = objectMapper.readTree(response.body());
            assertEquals("Order not found", body.path("detail").asString());
            assertFalse(body.has("trace"));
        }
        assertEquals(400, request("GET", "/orders/invalid", null).statusCode());
    }

    @Test
    void clientCannotChooseStatusOrOverrideCatalogPrices() throws Exception {
        // A Map lets us send fields that CreateOrderRequest deliberately does not have
        Map<String, Object> forged = Map.of(
                "id", 999,
                "customer", Map.of("id", 1, "username", "forged"),
                "items", List.of(Map.of("id", 1, "price", 0.01)),
                "status", "DELIVERED",
                "totalAmount", 0.01);
        HttpResponse<String> response = request("POST", "/orders", objectMapper.writeValueAsString(forged));
        assertEquals(201, response.statusCode(), response.body());
        JsonNode body = objectMapper.readTree(response.body());
        assertEquals("PENDING", body.path("status").asString());
        assertEquals("alice", body.path("customer").path("username").asString());
        assertEquals(0, new BigDecimal("10.10").compareTo(new BigDecimal(body.path("totalAmount").asString())));
    }

    private HttpResponse<String> create(int customerId, int... itemIds) throws Exception {
        List<ItemLine> items = Arrays.stream(itemIds)
                .mapToObj(id -> ItemLine.builder().id(id).build())
                .toList();
        return request("POST", "/orders", objectMapper.writeValueAsString(orderRequest(customerId, items)));
    }

    static Stream<Named<CreateOrderRequest>> invalidOrderRequests() {
        return Stream.of(
                Named.of("empty body", new CreateOrderRequest()),
                Named.of("no items", orderRequest(1, List.of())),
                Named.of("null item", orderRequest(1, Arrays.asList((ItemLine) null))),
                Named.of("item without id", orderRequest(1, List.of(new ItemLine()))),
                Named.of("item id 0", orderRequest(1, List.of(ItemLine.builder().id(0).build()))),
                Named.of("duplicate item", orderRequest(1, List.of(
                        ItemLine.builder().id(1).build(), ItemLine.builder().id(1).build()))),
                Named.of("customer id 0", orderRequest(0, List.of(ItemLine.builder().id(1).build()))));
    }

    private static CreateOrderRequest orderRequest(int customerId, List<ItemLine> items) {
        return CreateOrderRequest.builder()
                .customer(IdReference.builder().id(customerId).build())
                .items(items)
                .build();
    }

    private HttpResponse<String> request(String method, String path, String body) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(
                        URI.create("http://localhost:" + port + contextPath + path))
                .timeout(Duration.ofSeconds(10))
                .header("Accept", "application/json")
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + JwtTestTokens.adminToken(jwkSource))
                .method(method, body == null ? HttpRequest.BodyPublishers.noBody()
                        : HttpRequest.BodyPublishers.ofString(body));
        try (HttpClient client = HttpClient.newHttpClient()) {
            return client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
        }
    }

    private int count(String table) {
        return jdbc.queryForObject("SELECT count(*) FROM " + table, Integer.class);
    }
}
