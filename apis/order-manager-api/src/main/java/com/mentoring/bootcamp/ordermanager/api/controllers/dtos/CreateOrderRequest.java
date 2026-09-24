package com.mentoring.bootcamp.ordermanager.api.controllers.dtos;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Request object for creating a new order")
public class CreateOrderRequest {
    @Schema(description = "The existing customer placing the order")
    private IdReference customer;
    @Schema(description = "Items to order; each item may appear only once")
    private List<ItemLine> items;

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "Reference to an existing customer")
    public static class IdReference {
        @Schema(description = "Id of the existing customer", example = "1")
        private Integer id;
    }

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "One item of the order")
    public static class ItemLine {
        @Schema(description = "Id of an existing catalog item", example = "10")
        private Integer id;
        @Schema(description = "Quantity to order, at least 1; defaults to 1 when omitted", example = "2")
        private Integer quantity;
    }
}
