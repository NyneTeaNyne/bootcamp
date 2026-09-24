package com.mentoring.bootcamp.ordermanager.common.exception;

/**
 * The request body points to something that does not exist (e.g. an order for an unknown customer or item).
 * HTTP 400, not 404: the requested URL exists, it is the content of the request that is wrong.
 */
public class ReferenceNotFoundException extends BusinessException {
    public ReferenceNotFoundException(String message) {
        super(message);
    }
}
