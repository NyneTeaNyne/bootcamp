package com.mentoring.bootcamp.ordermanager.api.tests.unit.models;

import com.mentoring.bootcamp.ordermanager.api.models.Item;
import com.mentoring.bootcamp.ordermanager.api.models.Order;
import com.mentoring.bootcamp.ordermanager.api.models.OrderItem;
import com.mentoring.bootcamp.ordermanager.api.models.User;
import com.mentoring.bootcamp.ordermanager.common.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class OrderModelTests {
    @Test
    void calculatesTotalWithoutBinaryFloatingPointAddition() {
        Order order = order();
        order.getItems().getFirst().getItem().setPrice(0.10);
        order.getItems().getLast().getItem().setPrice(0.20);

        assertEquals(0, new BigDecimal("0.30").compareTo(order.getTotalAmount()));
    }

    @Test
    void totalUsesPersistedLineQuantities() {
        Order order = order();
        order.getItems().getFirst().getItem().setPrice(0.10);
        order.getItems().getFirst().setQuantity(3);
        order.getItems().getLast().getItem().setPrice(0.20);
        order.getItems().getLast().setQuantity(2);

        assertEquals(0, new BigDecimal("0.70").compareTo(order.getTotalAmount()));
    }

    @Test
    void acceptsDistinctItemReferences() {
        assertDoesNotThrow(() -> order().validate());
    }

    @Test
    void rejectsMissingCustomer() {
        Order order = order();
        order.setCustomer(null);
        assertThrows(BusinessException.class, order::validate);
    }

    @Test
    void rejectsMissingCustomerId() {
        Order order = order();
        order.getCustomer().setId(null);
        assertThrows(BusinessException.class, order::validate);
    }

    @Test
    void rejectsEmptyOrMissingItems() {
        Order order = order();
        order.setItems(List.of());
        assertThrows(BusinessException.class, order::validate);
        order.setItems(null);
        assertThrows(BusinessException.class, order::validate);
    }

    @Test
    void rejectsNullItemsAndInvalidIds() {
        Order order = order();
        order.setItems(Arrays.asList((OrderItem) null));
        assertThrows(BusinessException.class, order::validate);
        OrderItem line = new OrderItem();
        order.setItems(List.of(line));
        assertThrows(BusinessException.class, order::validate);
        Item item = new Item();
        line.setItem(item);
        assertThrows(BusinessException.class, order::validate);
        item.setId(0);
        assertThrows(BusinessException.class, order::validate);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1})
    void rejectsQuantityBelowOne(int quantity) {
        Order order = order();
        order.getItems().getFirst().setQuantity(quantity);
        BusinessException exception = assertThrows(BusinessException.class, order::validate);
        assertEquals("Each item must have a quantity of at least 1", exception.getMessage());
    }

    @Test
    void acceptsQuantityGreaterThanOne() {
        Order order = order();
        order.getItems().getFirst().setQuantity(5);
        assertDoesNotThrow(order::validate);
    }

    @Test
    void rejectsDuplicateItemsInsteadOfInventingQuantities() {
        Order order = order();
        order.getItems().getLast().getItem().setId(order.getItems().getFirst().getItem().getId());
        assertThrows(BusinessException.class, order::validate);
    }

    private Order order() {
        User customer = new User();
        customer.setId(1);
        Item first = new Item();
        first.setId(10);
        Item second = new Item();
        second.setId(20);
        OrderItem firstLine = new OrderItem();
        firstLine.setItem(first);
        OrderItem secondLine = new OrderItem();
        secondLine.setItem(second);
        Order order = new Order();
        order.setCustomer(customer);
        order.setItems(List.of(firstLine, secondLine));
        return order;
    }
}
