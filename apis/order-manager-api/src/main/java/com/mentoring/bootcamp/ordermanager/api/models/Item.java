package com.mentoring.bootcamp.ordermanager.api.models;

import com.mentoring.bootcamp.ordermanager.common.exception.InvalidDataException;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class Item {
    private static final int PRODUCT_NAME_MAX_LENGTH = 100;

    private Integer id;
    private String productName;
    private Double price;

    public void validate() {
        if (productName == null || productName.isBlank() || productName.length() > PRODUCT_NAME_MAX_LENGTH) {
            throw new InvalidDataException("Product name must contain between 1 and 100 characters");
        }
        if (price == null || price <= 0.0) {
            throw new InvalidDataException("Item should have a valid positive price");
        }
    }

    /**
     * Validates an item used as a reference (e.g. in an order line): only the id is provided.
     */
    public void validateReference() {
        if (id == null || id <= 0) {
            throw new InvalidDataException("Each item must have a valid id");
        }
    }
}
