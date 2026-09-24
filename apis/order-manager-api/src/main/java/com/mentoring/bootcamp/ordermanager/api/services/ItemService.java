package com.mentoring.bootcamp.ordermanager.api.services;

import com.mentoring.bootcamp.ordermanager.api.config.CacheConfig;
import com.mentoring.bootcamp.ordermanager.api.entities.repositories.ItemRepository;
import com.mentoring.bootcamp.ordermanager.api.mappers.ItemMapper;
import com.mentoring.bootcamp.ordermanager.api.models.Item;
import com.mentoring.bootcamp.ordermanager.api.usecases.ItemUseCase;
import com.mentoring.bootcamp.ordermanager.common.exception.AlreadyExistsException;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
public class ItemService implements ItemUseCase {
    private final ItemRepository itemRepository;
    private final ItemMapper itemMapper;

    public ItemService(ItemRepository itemRepository, ItemMapper itemMapper) {
        this.itemRepository = itemRepository;
        this.itemMapper = itemMapper;
    }

    @Override
    public Item createItem(Item item) {
        item.validate();

        if (itemRepository.existsByProductName(item.getProductName())) {
            throw new AlreadyExistsException("Product name already exists");
        }
        return itemMapper.swallowToModel(itemRepository.save(itemMapper.toEntity(item)));
    }

    @Override
    public List<Item> getItems() {
        return itemMapper.swallowToModel(itemRepository.findAll());
    }

    @Override
    public Optional<Item> getItem(Integer id) {
        return itemRepository.findById(id).map(itemMapper::swallowToModel);
    }

    @Override
    // Cached orders show this data (customer name, item name and price): drop them
    @CacheEvict(cacheNames = {CacheConfig.ORDER, CacheConfig.ORDERS}, allEntries = true)
    public Optional<Item> updateItem(Integer id, Item item) {
        item.validate();

        return itemRepository.findById(id).map(entity -> {
            if (itemRepository.existsByProductNameAndIdNot(item.getProductName(), id)) {
                throw new AlreadyExistsException("Product name already exists");
            }
            entity.setProductName(item.getProductName());
            entity.setPrice(item.getPrice());
            return itemMapper.swallowToModel(itemRepository.save(entity));
        });
    }

    @Override
    @Transactional
    // Cached orders show this data (customer name, item name and price): drop them
    @CacheEvict(cacheNames = {CacheConfig.ORDER, CacheConfig.ORDERS}, allEntries = true)
    public boolean deleteItem(Integer id) {
        return itemRepository.findById(id).map(entity -> {
            itemRepository.delete(entity);
            return true;
        }).orElse(false);
    }
}
