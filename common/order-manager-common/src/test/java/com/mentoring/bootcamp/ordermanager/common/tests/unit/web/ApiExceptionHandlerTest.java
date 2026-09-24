package com.mentoring.bootcamp.ordermanager.common.tests.unit.web;

import com.mentoring.bootcamp.ordermanager.common.exception.AlreadyExistsException;
import com.mentoring.bootcamp.ordermanager.common.exception.BusinessException;
import com.mentoring.bootcamp.ordermanager.common.exception.InvalidDataException;
import com.mentoring.bootcamp.ordermanager.common.exception.NotFoundException;
import com.mentoring.bootcamp.ordermanager.common.exception.ReferenceNotFoundException;
import com.mentoring.bootcamp.ordermanager.common.web.ApiExceptionHandler;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.server.ResponseStatusException;

import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class ApiExceptionHandlerTest {

    private final ApiExceptionHandler handler = new ApiExceptionHandler();

    static Stream<BusinessException> businessErrors() {
        return Stream.of(
                new InvalidDataException("Item should have a valid positive price"),
                new AlreadyExistsException("Username already exists"),
                new ReferenceNotFoundException("Customer not found"));
    }

    @ParameterizedTest
    @MethodSource("businessErrors")
    void should_return_400_with_the_message_for_every_broken_business_rule(BusinessException exception) {
        ProblemDetail problem = handler.handleBusinessException(exception);

        assertThat(problem.getStatus()).isEqualTo(400);
        assertThat(problem.getDetail()).isEqualTo(exception.getMessage());
    }

    @Test
    void should_return_404_when_the_resource_does_not_exist() {
        ProblemDetail problem = handler.handleNotFoundException(new NotFoundException("Order not found"));

        assertThat(problem.getStatus()).isEqualTo(404);
        assertThat(problem.getTitle()).isEqualTo("Not Found");
        assertThat(problem.getDetail()).isEqualTo("Order not found");
    }

    @Test
    void should_keep_the_reason_of_client_errors() {
        ResponseEntity<ProblemDetail> response = handler.handleResponseStatusException(
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody().getDetail()).isEqualTo("Order not found");
    }

    @Test
    void should_hide_the_reason_of_server_errors() {
        ResponseEntity<ProblemDetail> response = handler.handleResponseStatusException(
                new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "private database details"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(response.getBody().getDetail()).isEqualTo("An unexpected error occurred");
    }

    @Test
    void should_return_409_without_sql_details_on_data_conflicts() {
        ProblemDetail problem = handler.handleDataIntegrityViolation(
                new DataIntegrityViolationException("private SQL constraint details"));

        assertThat(problem.getStatus()).isEqualTo(409);
        assertThat(problem.getDetail()).isEqualTo("Operation conflicts with existing data");
    }
}
