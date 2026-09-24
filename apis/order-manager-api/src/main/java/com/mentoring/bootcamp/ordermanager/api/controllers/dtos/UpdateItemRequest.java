package com.mentoring.bootcamp.ordermanager.api.controllers.dtos;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Request object for updating a catalog product")
public class UpdateItemRequest {
    @Schema(description = "Unique product name, 1 to 100 characters", example = "Trail Running Shoes")
    private String productName;
    @Schema(description = "Unit price, strictly positive", example = "69.99")
    private Double price;
}
