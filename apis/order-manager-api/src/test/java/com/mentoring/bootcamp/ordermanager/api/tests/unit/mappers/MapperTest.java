package com.mentoring.bootcamp.ordermanager.api.tests.unit.mappers;

import com.mentoring.bootcamp.ordermanager.api.controllers.dtos.CreateOrderRequest;
import com.mentoring.bootcamp.ordermanager.api.entities.ItemEntity;
import com.mentoring.bootcamp.ordermanager.api.entities.OrderEntity;
import com.mentoring.bootcamp.ordermanager.api.entities.OrderItemEntity;
import com.mentoring.bootcamp.ordermanager.api.entities.StatusTypeEnum;
import com.mentoring.bootcamp.ordermanager.api.entities.UserEntity;
import com.mentoring.bootcamp.ordermanager.api.mappers.ItemMapper;
import com.mentoring.bootcamp.ordermanager.api.mappers.ItemMapperImpl;
import com.mentoring.bootcamp.ordermanager.api.mappers.OrderMapperImpl;
import com.mentoring.bootcamp.ordermanager.api.mappers.UserMapper;
import com.mentoring.bootcamp.ordermanager.api.mappers.UserMapperImpl;
import com.mentoring.bootcamp.ordermanager.api.models.Item;
import com.mentoring.bootcamp.ordermanager.api.models.Order;
import com.mentoring.bootcamp.ordermanager.api.models.OrderItem;
import com.mentoring.bootcamp.ordermanager.api.models.User;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Date;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

class MapperTest {

    private final UserMapper userMapper = new UserMapperImpl();
    private final ItemMapper itemMapper = new ItemMapperImpl();
    private final OrderMapperImpl orderMapper = new OrderMapperImpl();

    MapperTest() {
        // The generated OrderMapperImpl uses field injection, normally done by Spring
        ReflectionTestUtils.setField(orderMapper, "userMapper", userMapper);
        ReflectionTestUtils.setField(orderMapper, "itemMapper", itemMapper);
    }

    @Test
    void should_map_user_model_to_entity_and_back() {
        User user = new User();
        user.setId(1);
        user.setUsername("alice");
        user.setEmail("alice@decathlon.com");

        UserEntity entity = userMapper.toEntity(user);

        assertThat(entity.getUsername()).isEqualTo("alice");
        assertThat(entity.getEmail()).isEqualTo("alice@decathlon.com");
        assertThat(userMapper.swallowToModel(List.of(entity)))
                .singleElement()
                .extracting(User::getUsername)
                .isEqualTo("alice");
    }

    @Test
    void should_map_item_model_to_entity_and_back() {
        Item item = new Item();
        item.setId(3);
        item.setProductName("Tent");
        item.setPrice(49.99);

        ItemEntity entity = itemMapper.toEntity(item);

        assertThat(entity.getId()).isEqualTo(3);
        assertThat(entity.getProductName()).isEqualTo("Tent");
        assertThat(entity.getPrice()).isEqualTo(49.99);
        Item back = itemMapper.swallowToModel(entity);
        assertThat(back.getProductName()).isEqualTo("Tent");
        assertThat(itemMapper.swallowToModel(List.of(entity))).hasSize(1);
        assertThat(itemMapper.toResponse(List.of(back))).singleElement()
                .satisfies(response -> assertThat(response.getPrice()).isEqualTo(49.99));
    }

    @Test
    void should_map_order_entity_with_lines_to_model() {
        OrderEntity entity = orderEntity();

        Order order = orderMapper.toModel(entity);

        assertThat(order.getId()).isEqualTo(5);
        assertThat(order.getStatus()).isEqualTo(StatusTypeEnum.SHIPPED);
        assertThat(order.getOrderDate()).isEqualTo(entity.getOrderDate());
        assertThat(order.getCustomer().getUsername()).isEqualTo("alice");
        // Orders are cached: the customer's password hash must never be copied into them
        assertThat(order.getCustomer().getPassword()).isNull();
        assertThat(order.getItems()).singleElement().satisfies(line -> {
            assertThat(line.getItem().getProductName()).isEqualTo("Tent");
            assertThat(line.getQuantity()).isEqualTo(3);
        });
        assertThat(order.getTotalAmount()).isEqualByComparingTo("30.00");
        assertThat(orderMapper.toModel(List.of(entity))).hasSize(1);
    }

    @Test
    void should_map_create_request_quantity_and_default_it_to_1() {
        CreateOrderRequest.IdReference customer = new CreateOrderRequest.IdReference();
        customer.setId(1);
        CreateOrderRequest.ItemLine withQuantity = new CreateOrderRequest.ItemLine();
        withQuantity.setId(10);
        withQuantity.setQuantity(4);
        CreateOrderRequest.ItemLine withoutQuantity = new CreateOrderRequest.ItemLine();
        withoutQuantity.setId(20);
        CreateOrderRequest request = new CreateOrderRequest();
        request.setCustomer(customer);
        request.setItems(List.of(withQuantity, withoutQuantity));

        Order order = orderMapper.toModel(request);

        assertThat(order.getCustomer().getId()).isEqualTo(1);
        assertThat(order.getItems())
                .extracting(line -> line.getItem().getId(), OrderItem::getQuantity)
                .containsExactly(tuple(10, 4), tuple(20, 1));
    }

    @Test
    void should_map_nulls_to_nulls() {
        assertThat(orderMapper.toModel((OrderEntity) null)).isNull();
        assertThat(orderMapper.toModel((List<OrderEntity>) null)).isNull();
        assertThat(orderMapper.toResponse((Order) null)).isNull();
        assertThat(itemMapper.toEntity(null)).isNull();
        assertThat(userMapper.toEntity(null)).isNull();
    }

    private OrderEntity orderEntity() {
        UserEntity customer = new UserEntity();
        customer.setId(1);
        customer.setUsername("alice");
        customer.setPassword("$2a$10$hash");
        ItemEntity tent = new ItemEntity();
        tent.setId(10);
        tent.setProductName("Tent");
        tent.setPrice(10.0);
        OrderEntity order = new OrderEntity();
        order.setId(5);
        order.setCustomer(customer);
        order.setStatus(StatusTypeEnum.SHIPPED);
        order.setOrderDate(new Date());
        OrderItemEntity line = new OrderItemEntity();
        line.setOrder(order);
        line.setItem(tent);
        line.setQuantity(3);
        order.getItems().add(line);
        return order;
    }
}
