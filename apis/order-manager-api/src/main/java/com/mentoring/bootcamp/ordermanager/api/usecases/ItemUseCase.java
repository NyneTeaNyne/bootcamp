package com.mentoring.bootcamp.ordermanager.api.usecases;

import com.mentoring.bootcamp.ordermanager.api.models.Item;

import java.util.List;
import java.util.Optional;

public interface ItemUseCase {
    Item createItem(Item item);

    List<Item> getItems();

    Optional<Item> getItem(Integer id);

    Optional<Item> updateItem(Integer id, Item item);

    boolean deleteItem(Integer id);
}
