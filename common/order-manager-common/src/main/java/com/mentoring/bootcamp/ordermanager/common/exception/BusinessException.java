package com.mentoring.bootcamp.ordermanager.common.exception;

/**
 * A business rule was broken. Its message is safe to show to API clients (HTTP 400).
 * Abstract on purpose: always throw the subclass that says which rule was broken.
 *
 * @see InvalidDataException
 * @see AlreadyExistsException
 * @see ReferenceNotFoundException
 */
public abstract class BusinessException extends RuntimeException {
    protected BusinessException(String message) {
        super(message);
    }
}
