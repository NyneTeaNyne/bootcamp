package com.mentoring.bootcamp.ordermanager.api.controllers.dtos;

import com.mentoring.bootcamp.ordermanager.api.entities.StatusTypeEnum;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;

@Getter
@Setter
@Schema(description = "A customer order")
public class OrderResponse {
    @Schema(description = "Generated order id", example = "1")
    private Integer id;
    @Schema(description = "Customer who placed the order")
    private UserResponse customer;
    @Schema(description = "Ordered items")
    private List<OrderItemResponse> items;
    @Schema(description = "Order date, set by the database")
    private Date orderDate;
    @Schema(description = "Order status", example = "PENDING")
    private StatusTypeEnum status;
    @Schema(description = "Sum of price x quantity over all lines", example = "119.98")
    private BigDecimal totalAmount;
}
