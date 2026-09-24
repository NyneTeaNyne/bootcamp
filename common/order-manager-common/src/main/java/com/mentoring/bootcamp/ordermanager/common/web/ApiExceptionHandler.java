package com.mentoring.bootcamp.ordermanager.common.web;

import com.mentoring.bootcamp.ordermanager.common.exception.BusinessException;
import com.mentoring.bootcamp.ordermanager.common.exception.NotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

/**
 * Turns exceptions into RFC 9457 "Problem Details" JSON, without ever exposing technical details.
 * Shared so that every API of the project answers errors in the same format.
 */
@RestControllerAdvice
public class ApiExceptionHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(ApiExceptionHandler.class);

    /**
     * Every broken business rule (invalid data, duplicate, unknown reference): HTTP 400.
     */
    @ExceptionHandler(BusinessException.class)
    public ProblemDetail handleBusinessException(BusinessException exception) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, exception.getMessage());
    }

    @ExceptionHandler(NotFoundException.class)
    public ProblemDetail handleNotFoundException(NotFoundException exception) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, exception.getMessage());
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ProblemDetail> handleResponseStatusException(ResponseStatusException exception) {
        ProblemDetail problem = ProblemDetail.forStatus(exception.getStatusCode());
        if (exception.getStatusCode().is5xxServerError()) {
            LOGGER.error("Unexpected API error", exception);
            problem.setDetail("An unexpected error occurred");
        } else {
            problem.setDetail(exception.getReason());
        }
        return ResponseEntity.status(exception.getStatusCode())
                .headers(exception.getHeaders())
                .body(problem);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ProblemDetail handleDataIntegrityViolation(DataIntegrityViolationException exception) {
        LOGGER.warn("Operation conflicts with existing data", exception);
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT,
                "Operation conflicts with existing data");
    }
}
