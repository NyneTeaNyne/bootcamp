package com.mentoring.bootcamp.ordermanager.api.entities.repositories;

import com.mentoring.bootcamp.ordermanager.api.entities.ItemEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ItemRepository extends JpaRepository<ItemEntity, Integer> {
    boolean existsByProductName(String productName);

    boolean existsByProductNameAndIdNot(String productName, Integer id);
}
