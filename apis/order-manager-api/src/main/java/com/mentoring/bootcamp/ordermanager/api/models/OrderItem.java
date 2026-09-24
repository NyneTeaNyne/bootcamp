package com.mentoring.bootcamp.ordermanager.api.models;

import com.mentoring.bootcamp.ordermanager.common.exception.InvalidDataException;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class OrderItem {
    private Item item;
    private int quantity = 1;

    /**
     * Validates the line itself. The item is only a reference to the catalog here (just an id),
     * so we call {@link Item#validateReference()} rather than {@link Item#validate()}, which needs a name and a price.
     */
    public void validate() {
        if (item == null) {
            throw new InvalidDataException("Each item must have a valid id");
        }
        item.validateReference();
        if (quantity < 1) {
            throw new InvalidDataException("Each item must have a quantity of at least 1");
        }
    }
}
