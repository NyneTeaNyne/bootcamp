package com.mentoring.bootcamp.ordermanager.api.mappers;

import com.mentoring.bootcamp.ordermanager.api.controllers.dtos.CreateOrderRequest;
import com.mentoring.bootcamp.ordermanager.api.controllers.dtos.OrderItemResponse;
import com.mentoring.bootcamp.ordermanager.api.controllers.dtos.OrderResponse;
import com.mentoring.bootcamp.ordermanager.api.entities.OrderEntity;
import com.mentoring.bootcamp.ordermanager.api.entities.OrderItemEntity;
import com.mentoring.bootcamp.ordermanager.api.entities.UserEntity;
import com.mentoring.bootcamp.ordermanager.api.models.Order;
import com.mentoring.bootcamp.ordermanager.api.models.OrderItem;
import com.mentoring.bootcamp.ordermanager.api.models.User;
import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;
import org.mapstruct.ReportingPolicy;

import java.util.List;

@Mapper(componentModel = "spring", uses = {UserMapper.class, ItemMapper.class},
        unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface OrderMapper {
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "orderDate", ignore = true)
    @Mapping(target = "status", ignore = true)
    Order toModel(CreateOrderRequest request);

    @BeanMapping(ignoreByDefault = true)
    @Mapping(target = "id", source = "id")
    User toUser(CreateOrderRequest.IdReference reference);

    @BeanMapping(ignoreByDefault = true)
    @Mapping(target = "item.id", source = "id")
    @Mapping(target = "quantity", source = "quantity", defaultValue = "1")
    OrderItem toItem(CreateOrderRequest.ItemLine line);

    @Mapping(target = "customer", qualifiedByName = "customerWithoutPassword")
    Order toModel(OrderEntity entity);

    /**
     * An order only needs to show who the customer is: never copy the password hash
     * (orders are cached in Valkey).
     */
    @Named("customerWithoutPassword")
    @Mapping(target = "password", ignore = true)
    User toCustomer(UserEntity entity);

    OrderItem toItem(OrderItemEntity entity);

    List<Order> toModel(List<OrderEntity> entities);

    OrderResponse toResponse(Order order);

    @Mapping(target = ".", source = "item")
    OrderItemResponse toResponse(OrderItem item);

    List<OrderResponse> toResponse(List<Order> orders);
}
