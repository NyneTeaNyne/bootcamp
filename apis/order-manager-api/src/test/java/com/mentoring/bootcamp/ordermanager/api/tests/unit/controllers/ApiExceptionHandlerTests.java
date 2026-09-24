package com.mentoring.bootcamp.ordermanager.api.tests.unit.controllers;

import com.mentoring.bootcamp.ordermanager.api.controllers.UserController;
import com.mentoring.bootcamp.ordermanager.api.controllers.dtos.UpdateUserRequest;
import com.mentoring.bootcamp.ordermanager.api.mappers.UserMapper;
import com.mentoring.bootcamp.ordermanager.api.usecases.UserUseCase;
import com.mentoring.bootcamp.ordermanager.common.web.ApiExceptionHandler;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mapstruct.factory.Mappers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class ApiExceptionHandlerTests {
    @Mock
    private UserUseCase userUseCase;

    @ParameterizedTest
    @ValueSource(strings = {"GET", "PUT", "DELETE"})
    void missingUsersReturnProblemDetailsWithoutTrace(String method) throws Exception {
        UserMapper mapper = Mappers.getMapper(UserMapper.class);
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new UserController(userUseCase, mapper))
                .setControllerAdvice(new ApiExceptionHandler())
                .build();

        mockMvc.perform(request(HttpMethod.valueOf(method), "/api/v1/users/42")
                        .contextPath("/api/v1")
                        .queryParam("trace", "true")
                        .queryParam("message", "true")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(new ObjectMapper().writeValueAsString(
                                UpdateUserRequest.builder().username("alice").build())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Not Found"))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.detail").value("User not found"))
                .andExpect(jsonPath("$.instance").value("/api/v1/users/42"))
                .andExpect(jsonPath("$.trace").doesNotExist())
                .andExpect(jsonPath("$.exception").doesNotExist())
                .andExpect(jsonPath("$.message").doesNotExist());
    }

    @Test
    void serverStatusErrorsDoNotExposeTechnicalReasons() {
        ResponseEntity<ProblemDetail> response = new ApiExceptionHandler().handleResponseStatusException(
                new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "private database details"));

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("An unexpected error occurred", response.getBody().getDetail());
    }
}
