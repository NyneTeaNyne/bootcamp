package com.mentoring.bootcamp.ordermanager.common.exception;

/**
 * The data is valid but a unique value is already taken (username, product name...).
 * Answered with HTTP 400, as the bootcamp's REST conventions ask for duplicates.
 */
public class AlreadyExistsException extends BusinessException {
    public AlreadyExistsException(String message) {
        super(message);
    }
}
