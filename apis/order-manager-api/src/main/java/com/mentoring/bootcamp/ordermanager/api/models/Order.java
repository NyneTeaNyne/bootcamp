package com.mentoring.bootcamp.ordermanager.api.models;

import com.mentoring.bootcamp.ordermanager.api.entities.StatusTypeEnum;
import com.mentoring.bootcamp.ordermanager.common.exception.InvalidDataException;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Getter
@Setter
public class Order {
    private Integer id;
    private User customer;
    private List<OrderItem> items;
    private Date orderDate;
    private StatusTypeEnum status;

    public BigDecimal getTotalAmount() {
        return items.stream()
                .map(line -> BigDecimal.valueOf(line.getItem().getPrice())
                        .multiply(BigDecimal.valueOf(line.getQuantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public void validate() {
        if (customer == null) {
            throw new InvalidDataException("A valid customer id is required");
        }
        // The customer is only a reference here (just an id), not a full user to validate
        customer.validateReference();
        if (items == null || items.isEmpty()) {
            throw new InvalidDataException("An order must contain at least one item");
        }
        Set<Integer> ids = new HashSet<>();
        for (OrderItem line : items) {
            if (line == null) {
                throw new InvalidDataException("Each item must have a valid id");
            }
            line.validate();
            // Cross-line rule: only the order can see that an item appears twice
            if (!ids.add(line.getItem().getId())) {
                throw new InvalidDataException("Each item may appear only once in an order");
            }
        }
    }
}
