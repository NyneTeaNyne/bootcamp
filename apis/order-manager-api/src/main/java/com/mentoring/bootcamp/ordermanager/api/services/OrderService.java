package com.mentoring.bootcamp.ordermanager.api.services;

import com.mentoring.bootcamp.ordermanager.api.config.CacheConfig;
import com.mentoring.bootcamp.ordermanager.api.entities.ItemEntity;
import com.mentoring.bootcamp.ordermanager.api.entities.OrderEntity;
import com.mentoring.bootcamp.ordermanager.api.entities.OrderItemEntity;
import com.mentoring.bootcamp.ordermanager.api.entities.UserEntity;
import com.mentoring.bootcamp.ordermanager.api.entities.repositories.ItemRepository;
import com.mentoring.bootcamp.ordermanager.api.entities.repositories.OrderRepository;
import com.mentoring.bootcamp.ordermanager.api.entities.repositories.UserRepository;
import com.mentoring.bootcamp.ordermanager.api.mappers.OrderMapper;
import com.mentoring.bootcamp.ordermanager.api.models.Order;
import com.mentoring.bootcamp.ordermanager.api.models.OrderItem;
import com.mentoring.bootcamp.ordermanager.api.usecases.OrderUseCase;
import com.mentoring.bootcamp.ordermanager.common.exception.ReferenceNotFoundException;
import com.mentoring.bootcamp.ordermanager.common.mail.MailUseCase;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class OrderService implements OrderUseCase {
    private final OrderRepository orderRepository;
    private final UserRepository userRepository;
    private final ItemRepository itemRepository;
    private final OrderMapper orderMapper;
    private final MailUseCase mailUseCase;

    public OrderService(OrderRepository orderRepository, UserRepository userRepository,
                        ItemRepository itemRepository, OrderMapper orderMapper, MailUseCase mailUseCase) {
        this.orderRepository = orderRepository;
        this.userRepository = userRepository;
        this.itemRepository = itemRepository;
        this.orderMapper = orderMapper;
        this.mailUseCase = mailUseCase;
    }

    @Override
    @Transactional
    @CacheEvict(cacheNames = CacheConfig.ORDERS, allEntries = true)
    public Order createOrder(Order order) {
        order.validate();
        UserEntity customer = userRepository.findById(order.getCustomer().getId())
                .orElseThrow(() -> new ReferenceNotFoundException("Customer not found"));
        List<Integer> itemIds = order.getItems().stream().map(line -> line.getItem().getId()).toList();
        Map<Integer, ItemEntity> items = itemRepository.findAllById(itemIds).stream()
                .collect(Collectors.toMap(ItemEntity::getId, Function.identity()));

        OrderEntity entity = new OrderEntity();
        entity.setCustomer(customer);
        for (OrderItem requested : order.getItems()) {
            Integer itemId = requested.getItem().getId();
            ItemEntity item = items.get(itemId);
            if (item == null) {
                throw new ReferenceNotFoundException("Item not found: " + itemId);
            }
            OrderItemEntity line = new OrderItemEntity();
            line.setOrder(entity);
            line.setItem(item);
            line.setQuantity(requested.getQuantity());
            entity.getItems().add(line);
        }
        Order created = orderMapper.toModel(orderRepository.saveAndFlush(entity));

        // Sent last: if it fails, the exception rolls back the whole order
        mailUseCase.sendMail(
                customer.getEmail(),
                "Order Confirmation",
                "Your order number : " + created.getId() + " has been created");
        return created;
    }

    @Override
    @Cacheable(cacheNames = CacheConfig.ORDERS, key = CacheConfig.ALL)
    public List<Order> getOrders() {
        return orderMapper.toModel(orderRepository.findAll());
    }

    @Override
    // Spring unwraps the Optional: only existing orders are cached
    @Cacheable(cacheNames = CacheConfig.ORDER, key = "#id", unless = "#result == null")
    public Optional<Order> getOrder(Integer id) {
        return orderRepository.findById(id).map(orderMapper::toModel);
    }

    @Override
    @Transactional
    @Caching(evict = {
            @CacheEvict(cacheNames = CacheConfig.ORDER, key = "#id"),
            @CacheEvict(cacheNames = CacheConfig.ORDERS, allEntries = true)})
    public boolean deleteOrder(Integer id) {
        return orderRepository.findById(id).map(entity -> {
            orderRepository.delete(entity);
            return true;
        }).orElse(false);
    }
}
