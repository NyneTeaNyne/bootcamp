package com.mentoring.bootcamp.ordermanager.api.controllers.dtos;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Schema(description = "One line of an order")
public class OrderItemResponse {
    @Schema(description = "Item id", example = "1")
    private Integer id;
    @Schema(description = "Product name", example = "Running Shoes")
    private String productName;
    @Schema(description = "Current unit price of the item", example = "59.99")
    private Double price;
    @Schema(description = "Ordered quantity", example = "2")
    private int quantity;
}
