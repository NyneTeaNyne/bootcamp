package com.mentoring.bootcamp.ordermanager.api.tests.unit.models;

import com.mentoring.bootcamp.ordermanager.api.models.Item;
import com.mentoring.bootcamp.ordermanager.common.exception.InvalidDataException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ItemModelTest {

    @Test
    void should_accept_a_reference_with_only_an_id() {
        Item reference = new Item();
        reference.setId(10);

        // No name nor price: a reference is not a full item, so validate() would reject it
        assertThatCode(reference::validateReference).doesNotThrowAnyException();
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(ints = {0, -1})
    void should_reject_a_reference_with_an_invalid_id(Integer id) {
        Item reference = new Item();
        reference.setId(id);

        assertThatThrownBy(reference::validateReference)
                .isInstanceOf(InvalidDataException.class)
                .hasMessage("Each item must have a valid id");
    }
}
