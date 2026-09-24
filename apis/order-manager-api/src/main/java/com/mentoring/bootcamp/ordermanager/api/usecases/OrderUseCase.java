package com.mentoring.bootcamp.ordermanager.api.usecases;

import com.mentoring.bootcamp.ordermanager.api.models.Order;

import java.util.List;
import java.util.Optional;

public interface OrderUseCase {
    Order createOrder(Order order);

    List<Order> getOrders();

    Optional<Order> getOrder(Integer id);

    boolean deleteOrder(Integer id);
}
