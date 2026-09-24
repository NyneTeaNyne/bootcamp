package com.mentoring.bootcamp.ordermanager.api.tests.integration.controllers;

import com.mentoring.bootcamp.ordermanager.api.controllers.dtos.CreateOrderRequest.IdReference;
import com.mentoring.bootcamp.ordermanager.api.controllers.dtos.CreateOrderRequest.ItemLine;
import com.mentoring.bootcamp.ordermanager.api.controllers.dtos.CreateOrderRequest;
import com.mentoring.bootcamp.ordermanager.api.entities.ItemEntity;
import com.mentoring.bootcamp.ordermanager.api.entities.OrderEntity;
import com.mentoring.bootcamp.ordermanager.api.entities.UserEntity;
import com.mentoring.bootcamp.ordermanager.api.entities.repositories.ItemRepository;
import com.mentoring.bootcamp.ordermanager.api.entities.repositories.OrderRepository;
import com.mentoring.bootcamp.ordermanager.api.entities.repositories.UserRepository;
import com.mentoring.bootcamp.ordermanager.api.tests.integration.config.PostgresContextInitializer;
import com.mentoring.bootcamp.ordermanager.api.tests.integration.config.SmtpContextInitializer;
import com.mentoring.bootcamp.ordermanager.api.tests.integration.config.ValkeyContextInitializer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ContextConfiguration(initializers = {PostgresContextInitializer.class, SmtpContextInitializer.class,
        ValkeyContextInitializer.class})
@AutoConfigureMockMvc
@WithMockUser(username = "admin", roles = {"USER", "ADMIN"})
@EnabledIf("com.mentoring.bootcamp.ordermanager.api.tests.integration.config.PostgresContextInitializer#isDockerAvailable")
@Transactional // Every test is rolled back, so the shared database stays clean
class OrderIntegrationTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private OrderRepository orderRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private ItemRepository itemRepository;
    @Autowired
    private ObjectMapper objectMapper; // For JSON conversion

    @Test
    void should_create_an_order_and_return_201() throws Exception {
        UserEntity customer = customer();
        ItemEntity tent = item("Integration tent", 49.99);
        long ordersBefore = orderRepository.count();

        mockMvc.perform(post("/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(orderRequest(customer.getId(),
                                ItemLine.builder().id(tent.getId()).build()))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.orderDate").isNotEmpty())
                .andExpect(jsonPath("$.customer.username").value("integration-order-customer"))
                .andExpect(jsonPath("$.items[0].productName").value("Integration tent"))
                .andExpect(jsonPath("$.totalAmount").value(49.99));

        assertThat(orderRepository.count()).isEqualTo(ordersBefore + 1);
    }

    @Test
    void should_email_a_confirmation_to_the_customer() throws Exception {
        UserEntity customer = customer();
        ItemEntity tent = item("Integration tent", 49.99);

        String response = mockMvc.perform(post("/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(orderRequest(customer.getId(),
                                ItemLine.builder().id(tent.getId()).build()))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        int orderId = objectMapper.readTree(response).path("id").asInt();

        assertThat(mailsReceivedBy(customer.getEmail()))
                .anySatisfy(mail -> {
                    assertThat(mail.path("Subject").asString()).isEqualTo("Order Confirmation");
                    assertThat(mail.path("From").path("Address").asString()).isEqualTo("no-reply@decathlon.com");
                    assertThat(mail.path("Snippet").asString())
                            .isEqualTo("Your order number : " + orderId + " has been created");
                });
    }

    @Test
    void should_store_requested_quantities_and_default_missing_ones_to_1() throws Exception {
        UserEntity customer = customer();
        ItemEntity tent = item("Integration tent", 50.0);
        ItemEntity bottle = item("Integration bottle", 5.0);

        mockMvc.perform(post("/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(orderRequest(customer.getId(),
                                ItemLine.builder().id(tent.getId()).quantity(3).build(),
                                ItemLine.builder().id(bottle.getId()).build()))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.items[0].quantity").value(3))
                .andExpect(jsonPath("$.items[1].quantity").value(1))
                .andExpect(jsonPath("$.totalAmount").value(155.0));
    }

    @Test
    void should_return_400_and_create_nothing_when_quantity_is_not_positive() throws Exception {
        UserEntity customer = customer();
        ItemEntity tent = item("Integration tent", 50.0);
        long ordersBefore = orderRepository.count();

        mockMvc.perform(post("/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(orderRequest(customer.getId(),
                                ItemLine.builder().id(tent.getId()).quantity(0).build()))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Each item must have a quantity of at least 1"));

        assertThat(orderRepository.count()).isEqualTo(ordersBefore);
    }

    @Test
    void should_return_400_and_create_nothing_when_customer_does_not_exist() throws Exception {
        ItemEntity tent = item("Integration tent", 49.99);
        long ordersBefore = orderRepository.count();

        mockMvc.perform(post("/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(orderRequest(999999,
                                ItemLine.builder().id(tent.getId()).build()))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Customer not found"));

        assertThat(orderRepository.count()).isEqualTo(ordersBefore);
    }

    @Test
    void should_return_order_by_id() throws Exception {
        OrderEntity order = existingOrder();

        mockMvc.perform(get("/orders/" + order.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(order.getId()))
                .andExpect(jsonPath("$.items.length()").value(1));
    }

    @Test
    void should_return_404_when_order_does_not_exist() throws Exception {
        mockMvc.perform(get("/orders/999999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Order not found"));
    }

    @Test
    void should_delete_order_but_keep_customer_and_items() throws Exception {
        OrderEntity order = existingOrder();

        mockMvc.perform(delete("/orders/" + order.getId()))
                .andExpect(status().isNoContent());

        assertThat(orderRepository.findById(order.getId())).isEmpty();
        assertThat(userRepository.existsById(order.getCustomer().getId())).isTrue();
        assertThat(itemRepository.findAll())
                .extracting(ItemEntity::getProductName)
                .contains("Integration tent");
    }

    private OrderEntity existingOrder() throws Exception {
        UserEntity customer = customer();
        ItemEntity tent = item("Integration tent", 49.99);
        mockMvc.perform(post("/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(orderRequest(customer.getId(),
                                ItemLine.builder().id(tent.getId()).build()))))
                .andExpect(status().isCreated());
        return orderRepository.findAll().stream()
                .filter(order -> order.getCustomer().getId().equals(customer.getId()))
                .findFirst()
                .orElseThrow();
    }

    /**
     * Asks Mailpit (the fake SMTP server) for the emails sent to this address.
     */
    private List<JsonNode> mailsReceivedBy(String address) throws Exception {
        String query = URLEncoder.encode("to:" + address, StandardCharsets.UTF_8);
        HttpRequest request = HttpRequest.newBuilder(
                URI.create(SmtpContextInitializer.mailpitApiUrl() + "/search?query=" + query)).GET().build();
        try (HttpClient client = HttpClient.newHttpClient()) {
            String body = client.send(request, HttpResponse.BodyHandlers.ofString()).body();
            return objectMapper.readTree(body).path("messages").valueStream().toList();
        }
    }

    private CreateOrderRequest orderRequest(int customerId, ItemLine... lines) {
        return CreateOrderRequest.builder()
                .customer(IdReference.builder().id(customerId).build())
                .items(List.of(lines))
                .build();
    }

    private UserEntity customer() {
        UserEntity user = new UserEntity();
        user.setUsername("integration-order-customer");
        user.setEmail("integration-order-customer@decathlon.com");
        user.setPassword("not-used-by-these-tests");
        return userRepository.saveAndFlush(user);
    }

    private ItemEntity item(String productName, double price) {
        ItemEntity item = new ItemEntity();
        item.setProductName(productName);
        item.setPrice(price);
        return itemRepository.saveAndFlush(item);
    }
}
