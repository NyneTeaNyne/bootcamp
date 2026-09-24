package com.mentoring.bootcamp.ordermanager.api.entities.repositories;

import com.mentoring.bootcamp.ordermanager.api.entities.OrderEntity;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface OrderRepository extends JpaRepository<OrderEntity, Integer> {
    @Override
    @EntityGraph(attributePaths = {"customer", "items", "items.item"})
    List<OrderEntity> findAll();

    @Override
    @EntityGraph(attributePaths = {"customer", "items", "items.item"})
    Optional<OrderEntity> findById(Integer id);
}
