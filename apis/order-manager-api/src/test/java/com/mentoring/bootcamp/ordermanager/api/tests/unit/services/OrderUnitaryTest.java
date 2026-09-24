package com.mentoring.bootcamp.ordermanager.api.tests.unit.services;

import com.mentoring.bootcamp.ordermanager.api.entities.ItemEntity;
import com.mentoring.bootcamp.ordermanager.api.entities.OrderEntity;
import com.mentoring.bootcamp.ordermanager.api.entities.StatusTypeEnum;
import com.mentoring.bootcamp.ordermanager.api.entities.UserEntity;
import com.mentoring.bootcamp.ordermanager.api.entities.repositories.ItemRepository;
import com.mentoring.bootcamp.ordermanager.api.entities.repositories.OrderRepository;
import com.mentoring.bootcamp.ordermanager.api.entities.repositories.UserRepository;
import com.mentoring.bootcamp.ordermanager.api.mappers.OrderMapper;
import com.mentoring.bootcamp.ordermanager.api.models.Item;
import com.mentoring.bootcamp.ordermanager.api.models.Order;
import com.mentoring.bootcamp.ordermanager.api.models.OrderItem;
import com.mentoring.bootcamp.ordermanager.api.models.User;
import com.mentoring.bootcamp.ordermanager.api.services.OrderService;
import com.mentoring.bootcamp.ordermanager.common.exception.InvalidDataException;
import com.mentoring.bootcamp.ordermanager.common.exception.ReferenceNotFoundException;
import com.mentoring.bootcamp.ordermanager.common.mail.MailUseCase;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.MailSendException;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderUnitaryTest {

    @Mock
    private OrderRepository orderRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private ItemRepository itemRepository;
    @Mock
    private OrderMapper orderMapper;
    @Mock
    private MailUseCase mailUseCase; // Fake mail server

    @InjectMocks
    private OrderService orderService;

    @Test
    void should_get_all_orders() {
        OrderEntity entity = orderEntity(1);
        Order model = new Order();
        model.setId(1);
        when(orderRepository.findAll()).thenReturn(List.of(entity));
        when(orderMapper.toModel(List.of(entity))).thenReturn(List.of(model));

        List<Order> result = orderService.getOrders();

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().getId()).isEqualTo(1);
    }

    @Test
    void should_return_order_when_it_exists() {
        OrderEntity entity = orderEntity(7);
        Order model = new Order();
        model.setId(7);
        when(orderRepository.findById(7)).thenReturn(Optional.of(entity));
        when(orderMapper.toModel(entity)).thenReturn(model);

        assertThat(orderService.getOrder(7)).containsSame(model);
    }

    @Test
    void should_return_empty_when_order_does_not_exist() {
        when(orderRepository.findById(404)).thenReturn(Optional.empty());

        assertThat(orderService.getOrder(404)).isEmpty();
        verifyNoInteractions(orderMapper);
    }

    @Test
    void should_create_order_with_one_pending_line_per_item() {
        UserEntity customer = userEntity(1);
        ItemEntity tent = itemEntity(10);
        ItemEntity bike = itemEntity(20);
        Order created = new Order();
        when(userRepository.findById(1)).thenReturn(Optional.of(customer));
        when(itemRepository.findAllById(List.of(10, 20))).thenReturn(List.of(bike, tent));
        when(orderRepository.saveAndFlush(any(OrderEntity.class))).thenAnswer(call -> call.getArgument(0));
        when(orderMapper.toModel(any(OrderEntity.class))).thenReturn(created);

        Order result = orderService.createOrder(order(1, 10, 20));

        assertThat(result).isSameAs(created);
        ArgumentCaptor<OrderEntity> saved = ArgumentCaptor.forClass(OrderEntity.class);
        verify(orderRepository).saveAndFlush(saved.capture());
        OrderEntity entity = saved.getValue();
        assertThat(entity.getId()).isNull();
        assertThat(entity.getCustomer()).isSameAs(customer);
        assertThat(entity.getStatus()).isEqualTo(StatusTypeEnum.PENDING);
        assertThat(entity.getItems()).hasSize(2);
        // Lines keep the order requested by the client, not the order returned by the repository
        assertThat(entity.getItems().get(0).getItem()).isSameAs(tent);
        assertThat(entity.getItems().get(1).getItem()).isSameAs(bike);
        assertThat(entity.getItems()).allSatisfy(line -> {
            assertThat(line.getOrder()).isSameAs(entity);
            assertThat(line.getQuantity()).isEqualTo(1);
        });
    }

    @Test
    void should_persist_the_requested_quantity_of_each_line() {
        when(userRepository.findById(1)).thenReturn(Optional.of(userEntity(1)));
        when(itemRepository.findAllById(List.of(10, 20))).thenReturn(List.of(itemEntity(10), itemEntity(20)));
        when(orderRepository.saveAndFlush(any(OrderEntity.class))).thenAnswer(call -> call.getArgument(0));
        when(orderMapper.toModel(any(OrderEntity.class))).thenReturn(new Order());
        Order order = order(1, 10, 20);
        order.getItems().getFirst().setQuantity(3);

        orderService.createOrder(order);

        ArgumentCaptor<OrderEntity> saved = ArgumentCaptor.forClass(OrderEntity.class);
        verify(orderRepository).saveAndFlush(saved.capture());
        assertThat(saved.getValue().getItems())
                .extracting(line -> line.getItem().getId(), line -> line.getQuantity())
                .containsExactly(tuple(10, 3), tuple(20, 1));
    }

    @Test
    void should_email_a_confirmation_to_the_customer_once_the_order_is_saved() {
        UserEntity customer = userEntity(1);
        customer.setEmail("alice@decathlon.com");
        Order created = new Order();
        created.setId(42);
        when(userRepository.findById(1)).thenReturn(Optional.of(customer));
        when(itemRepository.findAllById(List.of(10))).thenReturn(List.of(itemEntity(10)));
        when(orderRepository.saveAndFlush(any(OrderEntity.class))).thenAnswer(call -> call.getArgument(0));
        when(orderMapper.toModel(any(OrderEntity.class))).thenReturn(created);

        orderService.createOrder(order(1, 10));

        verify(mailUseCase).sendMail("alice@decathlon.com", "Order Confirmation",
                "Your order number : 42 has been created");
    }

    @Test
    void should_fail_the_order_when_the_confirmation_email_cannot_be_sent() {
        when(userRepository.findById(1)).thenReturn(Optional.of(userEntity(1)));
        when(itemRepository.findAllById(List.of(10))).thenReturn(List.of(itemEntity(10)));
        when(orderRepository.saveAndFlush(any(OrderEntity.class))).thenAnswer(call -> call.getArgument(0));
        when(orderMapper.toModel(any(OrderEntity.class))).thenReturn(new Order());
        doThrow(new MailSendException("SMTP server down"))
                .when(mailUseCase).sendMail(any(), anyString(), anyString());

        // The exception leaves the @Transactional method, so Spring rolls the order back
        assertThatThrownBy(() -> orderService.createOrder(order(1, 10)))
                .isInstanceOf(MailSendException.class);
    }

    @Test
    void should_throw_when_customer_does_not_exist() {
        when(userRepository.findById(99)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> orderService.createOrder(order(99, 10)))
                .isInstanceOf(ReferenceNotFoundException.class)
                .hasMessage("Customer not found");

        verifyNoInteractions(itemRepository, orderRepository, mailUseCase);
    }

    @Test
    void should_throw_when_an_item_does_not_exist() {
        when(userRepository.findById(1)).thenReturn(Optional.of(userEntity(1)));
        when(itemRepository.findAllById(List.of(10, 999))).thenReturn(List.of(itemEntity(10)));

        assertThatThrownBy(() -> orderService.createOrder(order(1, 10, 999)))
                .isInstanceOf(ReferenceNotFoundException.class)
                .hasMessage("Item not found: 999");

        verify(orderRepository, never()).saveAndFlush(any());
        verifyNoInteractions(mailUseCase);
    }

    @Test
    void should_not_touch_repositories_when_order_is_invalid() {
        Order invalid = order(1);

        assertThatThrownBy(() -> orderService.createOrder(invalid))
                .isInstanceOf(InvalidDataException.class)
                .hasMessage("An order must contain at least one item");

        verifyNoInteractions(userRepository, itemRepository, orderRepository, orderMapper, mailUseCase);
    }

    @Test
    void should_delete_order_when_it_exists() {
        OrderEntity entity = orderEntity(3);
        when(orderRepository.findById(3)).thenReturn(Optional.of(entity));

        assertThat(orderService.deleteOrder(3)).isTrue();
        verify(orderRepository).delete(entity);
    }

    @Test
    void should_return_false_when_deleting_missing_order() {
        when(orderRepository.findById(3)).thenReturn(Optional.empty());

        assertThat(orderService.deleteOrder(3)).isFalse();
        verify(orderRepository, never()).delete(any());
    }

    private Order order(int customerId, Integer... itemIds) {
        User customer = new User();
        customer.setId(customerId);
        Order order = new Order();
        order.setCustomer(customer);
        order.setItems(Arrays.stream(itemIds).map(id -> {
            Item item = new Item();
            item.setId(id);
            OrderItem line = new OrderItem();
            line.setItem(item);
            return line;
        }).toList());
        return order;
    }

    private OrderEntity orderEntity(int id) {
        OrderEntity entity = new OrderEntity();
        entity.setId(id);
        return entity;
    }

    private UserEntity userEntity(int id) {
        UserEntity entity = new UserEntity();
        entity.setId(id);
        return entity;
    }

    private ItemEntity itemEntity(int id) {
        ItemEntity entity = new ItemEntity();
        entity.setId(id);
        return entity;
    }
}
