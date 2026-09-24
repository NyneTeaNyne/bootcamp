package com.mentoring.bootcamp.ordermanager.common.exception;

/**
 * The resource targeted by the URL does not exist (e.g. GET /users/42). Answered with HTTP 404.
 * Not a {@link BusinessException}: no rule was broken, the thing is just not there.
 */
public class NotFoundException extends RuntimeException {
    public NotFoundException(String message) {
        super(message);
    }
}
