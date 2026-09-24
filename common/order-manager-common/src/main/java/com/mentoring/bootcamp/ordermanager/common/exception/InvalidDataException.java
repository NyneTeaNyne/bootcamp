package com.mentoring.bootcamp.ordermanager.common.exception;

/**
 * The data sent is invalid on its own (empty name, negative price, missing id...), whatever the database contains.
 */
public class InvalidDataException extends BusinessException {
    public InvalidDataException(String message) {
        super(message);
    }
}
