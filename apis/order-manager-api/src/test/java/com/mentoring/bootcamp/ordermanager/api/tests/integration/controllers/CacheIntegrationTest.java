package com.mentoring.bootcamp.ordermanager.api.tests.integration.controllers;

import com.mentoring.bootcamp.ordermanager.api.controllers.dtos.CreateOrderRequest;
import com.mentoring.bootcamp.ordermanager.api.controllers.dtos.CreateOrderRequest.IdReference;
import com.mentoring.bootcamp.ordermanager.api.controllers.dtos.CreateOrderRequest.ItemLine;
import com.mentoring.bootcamp.ordermanager.api.controllers.dtos.UpdateItemRequest;
import com.mentoring.bootcamp.ordermanager.api.controllers.dtos.UpdateUserRequest;
import com.mentoring.bootcamp.ordermanager.api.tests.integration.config.PostgresContextInitializer;
import com.mentoring.bootcamp.ordermanager.api.tests.integration.config.SmtpContextInitializer;
import com.mentoring.bootcamp.ordermanager.api.tests.integration.config.ValkeyContextInitializer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.cache.CacheManager;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.databind.ObjectMapper;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Not @Transactional on purpose: the cache is only written once a transaction commits,
 * so these tests commit for real and clean the database themselves.
 */
@SpringBootTest
@ContextConfiguration(initializers = {PostgresContextInitializer.class, SmtpContextInitializer.class,
        ValkeyContextInitializer.class})
@AutoConfigureMockMvc
@WithMockUser(username = "admin", roles = {"USER", "ADMIN"})
@EnabledIf("com.mentoring.bootcamp.ordermanager.api.tests.integration.config.PostgresContextInitializer#isDockerAvailable")
@Disabled
class CacheIntegrationTest {

    private static final int CUSTOMER_ID = 1;
    private static final int ITEM_ID = 1;

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private CacheManager cacheManager;
    @Autowired
    private StringRedisTemplate redis;

    @BeforeEach
    void freshDataAndEmptyCache() {
        jdbc.execute("TRUNCATE order_items, orders, items, users RESTART IDENTITY");
        cacheManager.getCacheNames().forEach(name -> cacheManager.getCache(name).clear());
        jdbc.update("INSERT INTO users(username, email, password) VALUES ('cache-customer', "
                + "'cache-customer@decathlon.com', '$2a$10$aFakeBcryptHashForTheTests')");
        jdbc.update("INSERT INTO items(product_name, price) VALUES ('Cache tent', 10.00)");
    }

    @Test
    void should_serve_an_order_from_the_cache_until_its_item_changes() throws Exception {
        int orderId = createOrder();
        getOrder(orderId).andExpect(jsonPath("$.items[0].price").value(10.0));
        assertThat(redis.hasKey("order::" + orderId)).isTrue();

        // Changed behind the application's back: the cached copy is still served
        jdbc.update("UPDATE items SET price = 99.00 WHERE id = ?", ITEM_ID);
        getOrder(orderId).andExpect(jsonPath("$.items[0].price").value(10.0));

        // Changed through the API: cached orders are dropped, the new price shows up
        mockMvc.perform(put("/items/" + ITEM_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                UpdateItemRequest.builder().productName("Cache tent").price(20.0).build())))
                .andExpect(status().isOk());
        assertThat(redis.hasKey("order::" + orderId)).isFalse();
        getOrder(orderId).andExpect(jsonPath("$.items[0].price").value(20.0));
    }

    @Test
    void should_refresh_the_order_list_when_an_order_is_created_or_deleted() throws Exception {
        mockMvc.perform(get("/orders")).andExpect(jsonPath("$.length()").value(0));
        assertThat(redis.hasKey("orders::all")).isTrue();

        int orderId = createOrder();
        mockMvc.perform(get("/orders")).andExpect(jsonPath("$.length()").value(1));

        mockMvc.perform(delete("/orders/" + orderId)).andExpect(status().isNoContent());
        mockMvc.perform(get("/orders")).andExpect(jsonPath("$.length()").value(0));
        getOrder(orderId).andExpect(status().isNotFound());
    }

    @Test
    void should_refresh_cached_orders_when_the_customer_is_renamed() throws Exception {
        int orderId = createOrder();
        getOrder(orderId).andExpect(jsonPath("$.customer.username").value("cache-customer"));

        mockMvc.perform(put("/users/" + CUSTOMER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                UpdateUserRequest.builder().username("renamed-customer").build())))
                .andExpect(status().isOk());

        getOrder(orderId).andExpect(jsonPath("$.customer.username").value("renamed-customer"));
    }

    @Test
    void should_not_cache_missing_orders() throws Exception {
        getOrder(999).andExpect(status().isNotFound());

        assertThat(redis.hasKey("order::999")).isFalse();
    }

    @Test
    void should_never_store_the_password_hash_in_the_cache() throws Exception {
        int orderId = createOrder();
        getOrder(orderId).andExpect(status().isOk());

        String cached = redis.opsForValue().get("order::" + orderId);
        assertThat(cached).contains("cache-customer").doesNotContain("$2a$");
    }

    @Test
    void should_read_from_the_database_when_the_cache_is_unreadable() throws Exception {
        int orderId = createOrder();
        redis.opsForValue().set("order::" + orderId, "this is not an order");

        getOrder(orderId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(orderId));
    }

    private int createOrder() throws Exception {
        CreateOrderRequest request = CreateOrderRequest.builder()
                .customer(IdReference.builder().id(CUSTOMER_ID).build())
                .items(List.of(ItemLine.builder().id(ITEM_ID).build()))
                .build();
        String response = mockMvc.perform(post("/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).path("id").asInt();
    }

    private ResultActions getOrder(int id) throws Exception {
        return mockMvc.perform(get("/orders/" + id));
    }
}
