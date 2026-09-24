package com.mentoring.bootcamp.ordermanager.api.controllers.dtos;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Schema(description = "A catalog product")
public class ItemResponse {
    @Schema(description = "Generated item id", example = "1")
    private Integer id;
    @Schema(description = "Product name", example = "Running Shoes")
    private String productName;
    @Schema(description = "Current unit price", example = "59.99")
    private Double price;
}
