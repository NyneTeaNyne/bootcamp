package com.mentoring.bootcamp.ordermanager.api.tests.unit.models;

import com.mentoring.bootcamp.ordermanager.api.models.Item;
import com.mentoring.bootcamp.ordermanager.api.models.OrderItem;
import com.mentoring.bootcamp.ordermanager.common.exception.InvalidDataException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OrderItemModelTest {

    @Test
    void should_accept_a_line_referencing_an_item_by_id_only() {
        // No name or price: on creation the item is only a reference to the catalog
        assertThatCode(() -> line(10, 1).validate()).doesNotThrowAnyException();
    }

    @Test
    void should_reject_a_line_without_item() {
        OrderItem line = new OrderItem();

        assertThatThrownBy(line::validate)
                .isInstanceOf(InvalidDataException.class)
                .hasMessage("Each item must have a valid id");
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(ints = {0, -1})
    void should_reject_an_invalid_item_id(Integer id) {
        assertThatThrownBy(() -> line(id, 1).validate())
                .isInstanceOf(InvalidDataException.class)
                .hasMessage("Each item must have a valid id");
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1})
    void should_reject_a_quantity_below_1(int quantity) {
        assertThatThrownBy(() -> line(10, quantity).validate())
                .isInstanceOf(InvalidDataException.class)
                .hasMessage("Each item must have a quantity of at least 1");
    }

    private OrderItem line(Integer itemId, int quantity) {
        Item item = new Item();
        item.setId(itemId);
        OrderItem line = new OrderItem();
        line.setItem(item);
        line.setQuantity(quantity);
        return line;
    }
}
