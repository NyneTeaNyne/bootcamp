package com.mentoring.bootcamp.ordermanager.api.tests.unit.services;

import com.mentoring.bootcamp.ordermanager.api.entities.ItemEntity;
import com.mentoring.bootcamp.ordermanager.api.entities.repositories.ItemRepository;
import com.mentoring.bootcamp.ordermanager.api.mappers.ItemMapper;
import com.mentoring.bootcamp.ordermanager.api.models.Item;
import com.mentoring.bootcamp.ordermanager.api.services.ItemService;
import com.mentoring.bootcamp.ordermanager.common.exception.AlreadyExistsException;
import com.mentoring.bootcamp.ordermanager.common.exception.BusinessException;
import com.mentoring.bootcamp.ordermanager.common.exception.InvalidDataException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ItemUnitaryTest {

    @Mock
    private ItemRepository itemRepository;
    @Mock
    private ItemMapper itemMapper;

    @InjectMocks
    private ItemService itemService;

    @Test
    void should_accept_a_positive_price() {
        assertThatCode(() -> item("Tent", 0.01).validate()).doesNotThrowAnyException();
    }

    @ParameterizedTest
    @ValueSource(doubles = {0.0, -1.0})
    void should_reject_a_price_that_is_not_positive(double price) {
        assertThatThrownBy(() -> item("Tent", price).validate())
                .isInstanceOf(InvalidDataException.class)
                .hasMessage("Item should have a valid positive price");
    }

    @Test
    void should_reject_a_missing_price_instead_of_crashing() {
        Item item = item("Tent", 1.0);
        item.setPrice(null);

        assertThatThrownBy(item::validate)
                .isInstanceOf(InvalidDataException.class)
                .hasMessage("Item should have a valid positive price");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    void should_reject_a_blank_product_name(String productName) {
        assertThatThrownBy(() -> item(productName, 1.0).validate())
                .isInstanceOf(InvalidDataException.class)
                .hasMessage("Product name must contain between 1 and 100 characters");
    }

    @Test
    void should_accept_product_name_length_boundaries() {
        assertThatCode(() -> item("a", 1.0).validate()).doesNotThrowAnyException();
        assertThatCode(() -> item("a".repeat(100), 1.0).validate()).doesNotThrowAnyException();
        assertThatThrownBy(() -> item("a".repeat(101), 1.0).validate())
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void should_create_item_when_product_name_is_free() {
        Item item = item("Tent", 49.99);
        ItemEntity entity = new ItemEntity();
        ItemEntity saved = new ItemEntity();
        Item created = item("Tent", 49.99);
        created.setId(1);
        when(itemRepository.existsByProductName("Tent")).thenReturn(false);
        when(itemMapper.toEntity(item)).thenReturn(entity);
        when(itemRepository.save(entity)).thenReturn(saved);
        when(itemMapper.swallowToModel(saved)).thenReturn(created);

        assertThat(itemService.createItem(item)).isSameAs(created);
    }

    @Test
    void should_reject_creation_when_product_name_already_exists() {
        when(itemRepository.existsByProductName("Tent")).thenReturn(true);

        assertThatThrownBy(() -> itemService.createItem(item("Tent", 49.99)))
                .isInstanceOf(AlreadyExistsException.class)
                .hasMessage("Product name already exists");

        verify(itemRepository, never()).save(any());
    }

    @Test
    void should_reject_creation_without_touching_database_when_price_is_invalid() {
        assertThatThrownBy(() -> itemService.createItem(item("Tent", 0.0)))
                .isInstanceOf(BusinessException.class);

        verifyNoInteractions(itemRepository, itemMapper);
    }

    @Test
    void should_get_all_items() {
        List<ItemEntity> entities = List.of(new ItemEntity(), new ItemEntity());
        List<Item> items = List.of(item("Tent", 1.0), item("Bike", 2.0));
        when(itemRepository.findAll()).thenReturn(entities);
        when(itemMapper.swallowToModel(entities)).thenReturn(items);

        assertThat(itemService.getItems()).isSameAs(items);
    }

    @Test
    void should_return_item_when_it_exists() {
        ItemEntity entity = new ItemEntity();
        Item item = item("Tent", 1.0);
        when(itemRepository.findById(1)).thenReturn(Optional.of(entity));
        when(itemMapper.swallowToModel(entity)).thenReturn(item);

        assertThat(itemService.getItem(1)).containsSame(item);
    }

    @Test
    void should_return_empty_when_item_does_not_exist() {
        when(itemRepository.findById(404)).thenReturn(Optional.empty());

        assertThat(itemService.getItem(404)).isEmpty();
    }

    @Test
    void should_update_name_and_price_when_item_exists() {
        ItemEntity entity = new ItemEntity();
        entity.setId(1);
        entity.setProductName("Tent");
        entity.setPrice(10.0);
        Item updated = item("Big tent", 20.0);
        when(itemRepository.findById(1)).thenReturn(Optional.of(entity));
        when(itemRepository.existsByProductNameAndIdNot("Big tent", 1)).thenReturn(false);
        when(itemRepository.save(entity)).thenReturn(entity);
        when(itemMapper.swallowToModel(entity)).thenReturn(updated);

        assertThat(itemService.updateItem(1, item("Big tent", 20.0))).containsSame(updated);
        assertThat(entity.getId()).isEqualTo(1);
        assertThat(entity.getProductName()).isEqualTo("Big tent");
        assertThat(entity.getPrice()).isEqualTo(20.0);
    }

    @Test
    void should_return_empty_when_updating_missing_item() {
        when(itemRepository.findById(404)).thenReturn(Optional.empty());

        assertThat(itemService.updateItem(404, item("Tent", 1.0))).isEmpty();
        verify(itemRepository, never()).save(any());
    }

    @Test
    void should_reject_update_when_product_name_is_used_by_another_item() {
        ItemEntity entity = new ItemEntity();
        entity.setProductName("Tent");
        when(itemRepository.findById(1)).thenReturn(Optional.of(entity));
        when(itemRepository.existsByProductNameAndIdNot("Bike", 1)).thenReturn(true);

        assertThatThrownBy(() -> itemService.updateItem(1, item("Bike", 1.0)))
                .isInstanceOf(AlreadyExistsException.class)
                .hasMessage("Product name already exists");

        assertThat(entity.getProductName()).isEqualTo("Tent");
        verify(itemRepository, never()).save(any());
    }

    @Test
    void should_reject_update_without_touching_database_when_price_is_invalid() {
        assertThatThrownBy(() -> itemService.updateItem(1, item("Tent", -5.0)))
                .isInstanceOf(BusinessException.class);

        verifyNoInteractions(itemRepository, itemMapper);
    }

    @Test
    void should_delete_item_when_it_exists() {
        ItemEntity entity = new ItemEntity();
        when(itemRepository.findById(1)).thenReturn(Optional.of(entity));

        assertThat(itemService.deleteItem(1)).isTrue();
        verify(itemRepository).delete(entity);
    }

    @Test
    void should_return_false_when_deleting_missing_item() {
        when(itemRepository.findById(1)).thenReturn(Optional.empty());

        assertThat(itemService.deleteItem(1)).isFalse();
        verify(itemRepository, never()).delete(any());
    }

    private Item item(String productName, double price) {
        Item item = new Item();
        item.setProductName(productName);
        item.setPrice(price);
        return item;
    }
}
