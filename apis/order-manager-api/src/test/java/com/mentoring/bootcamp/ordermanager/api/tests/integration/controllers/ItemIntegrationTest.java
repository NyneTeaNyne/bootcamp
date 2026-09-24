package com.mentoring.bootcamp.ordermanager.api.tests.integration.controllers;

import com.mentoring.bootcamp.ordermanager.api.controllers.dtos.CreateItemRequest;
import com.mentoring.bootcamp.ordermanager.api.controllers.dtos.UpdateItemRequest;
import com.mentoring.bootcamp.ordermanager.api.entities.ItemEntity;
import com.mentoring.bootcamp.ordermanager.api.entities.repositories.ItemRepository;
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
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ContextConfiguration(initializers = {PostgresContextInitializer.class, SmtpContextInitializer.class,
        ValkeyContextInitializer.class})
@AutoConfigureMockMvc
@WithMockUser(username = "admin", roles = {"USER", "ADMIN"})
@EnabledIf("com.mentoring.bootcamp.ordermanager.api.tests.integration.config.PostgresContextInitializer#isDockerAvailable")
@Transactional // Every test is rolled back, so the shared database stays clean
class ItemIntegrationTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ItemRepository itemRepository;
    @Autowired
    private ObjectMapper objectMapper; // For JSON conversion

    @Test
    void should_create_an_item() throws Exception {
        mockMvc.perform(post("/items")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest("Integration kayak", 349.90))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.productName").value("Integration kayak"));

        assertThat(itemRepository.existsByProductName("Integration kayak")).isTrue();
    }

    @Test
    void should_return_400_when_product_name_already_exists() throws Exception {
        item("Integration kayak", 349.90);

        mockMvc.perform(post("/items")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest("Integration kayak", 10.0))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Product name already exists"));
    }

    @Test
    void should_return_400_when_price_is_not_positive() throws Exception {
        mockMvc.perform(post("/items")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest("Free kayak", 0.0))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Item should have a valid positive price"));

        assertThat(itemRepository.existsByProductName("Free kayak")).isFalse();
    }

    @Test
    void should_return_400_when_price_is_missing() throws Exception {
        mockMvc.perform(post("/items")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest("Kayak without price", null))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Item should have a valid positive price"));
    }

    @Test
    void should_return_item_by_id() throws Exception {
        ItemEntity kayak = item("Integration kayak", 349.90);

        mockMvc.perform(get("/items/" + kayak.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.productName").value("Integration kayak"))
                .andExpect(jsonPath("$.price").value(349.90));
    }

    @Test
    void should_update_an_item() throws Exception {
        ItemEntity kayak = item("Integration kayak", 349.90);

        mockMvc.perform(put("/items/" + kayak.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                UpdateItemRequest.builder().productName("Integration kayak XL").price(399.90).build())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(kayak.getId()))
                .andExpect(jsonPath("$.productName").value("Integration kayak XL"));

        assertThat(itemRepository.findById(kayak.getId()))
                .get()
                .extracting(ItemEntity::getPrice)
                .isEqualTo(399.90);
    }

    @Test
    void should_delete_an_item() throws Exception {
        ItemEntity kayak = item("Integration kayak", 349.90);

        mockMvc.perform(delete("/items/" + kayak.getId()))
                .andExpect(status().isNoContent());

        assertThat(itemRepository.findById(kayak.getId())).isEmpty();
    }

    @Test
    void should_return_404_when_item_does_not_exist() throws Exception {
        mockMvc.perform(get("/items/999999"))
                .andExpect(status().isNotFound());
        mockMvc.perform(delete("/items/999999"))
                .andExpect(status().isNotFound());
    }

    private CreateItemRequest createRequest(String productName, Double price) {
        return CreateItemRequest.builder().productName(productName).price(price).build();
    }

    private ItemEntity item(String productName, double price) {
        ItemEntity item = new ItemEntity();
        item.setProductName(productName);
        item.setPrice(price);
        return itemRepository.saveAndFlush(item);
    }
}
