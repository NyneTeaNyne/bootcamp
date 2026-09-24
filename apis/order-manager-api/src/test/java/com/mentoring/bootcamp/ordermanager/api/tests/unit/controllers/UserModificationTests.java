package com.mentoring.bootcamp.ordermanager.api.tests.unit.controllers;

import com.mentoring.bootcamp.ordermanager.api.controllers.UserController;
import com.mentoring.bootcamp.ordermanager.api.controllers.dtos.UpdateUserRequest;
import com.mentoring.bootcamp.ordermanager.api.entities.UserEntity;
import com.mentoring.bootcamp.ordermanager.api.entities.repositories.UserRepository;
import com.mentoring.bootcamp.ordermanager.api.mappers.UserMapper;
import com.mentoring.bootcamp.ordermanager.api.services.UserService;
import com.mentoring.bootcamp.ordermanager.common.web.ApiExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mapstruct.factory.Mappers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.Date;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class UserModificationTests {
    @Mock
    private UserRepository userRepository;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        UserMapper mapper = Mappers.getMapper(UserMapper.class);
        UserService service = new UserService(userRepository, mapper, new BCryptPasswordEncoder());
        mockMvc = MockMvcBuilders.standaloneSetup(new UserController(service, mapper))
                .setControllerAdvice(new ApiExceptionHandler())
                .build();
    }

    @Test
    void updatesUsernameAndEmailWhilePreservingGeneratedFields() throws Exception {
        UserEntity entity = existingUser();
        Date createdAt = entity.getCreate_at();
        when(userRepository.findById(42)).thenReturn(Optional.of(entity));
        when(userRepository.save(entity)).thenReturn(entity);

        mockMvc.perform(put("/api/v1/users/42").contextPath("/api/v1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("bob")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(4))
                .andExpect(jsonPath("$.id").value(42))
                .andExpect(jsonPath("$.username").value("bob"))
                .andExpect(jsonPath("$.email").value("bob@decathlon.com"))
                .andExpect(jsonPath("$.create_at").isNotEmpty());

        assertEquals(createdAt, entity.getCreate_at());
        assertEquals(42, entity.getId());
        verify(userRepository).existsByUsernameAndIdNot("bob", 42);
        verify(userRepository).save(entity);
    }

    @Test
    void acceptsUnchangedUsername() throws Exception {
        UserEntity entity = existingUser();
        when(userRepository.findById(42)).thenReturn(Optional.of(entity));
        when(userRepository.save(entity)).thenReturn(entity);

        mockMvc.perform(put("/api/v1/users/42").contextPath("/api/v1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("alice")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("alice"));

        verify(userRepository).existsByUsernameAndIdNot("alice", 42);
    }

    @Test
    void rejectsDuplicateUsernameWithoutModifyingUser() throws Exception {
        UserEntity entity = existingUser();
        when(userRepository.findById(42)).thenReturn(Optional.of(entity));
        when(userRepository.existsByUsernameAndIdNot("bob", 42)).thenReturn(true);

        mockMvc.perform(put("/api/v1/users/42").contextPath("/api/v1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("bob")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Username already exists"))
                .andExpect(jsonPath("$.trace").doesNotExist());

        assertEquals("alice", entity.getUsername());
        verify(userRepository, never()).save(any(UserEntity.class));
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "   "})
    void rejectsMissingOrBlankUsername(String username) throws Exception {
        mockMvc.perform(put("/api/v1/users/42").contextPath("/api/v1")
                        .contentType(MediaType.APPLICATION_JSON).content(body(username)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Username must contain between 1 and 50 characters"));

        verifyNoInteractions(userRepository);
    }

    @Test
    void rejectsUsernameLongerThanColumn() throws Exception {
        mockMvc.perform(put("/api/v1/users/42").contextPath("/api/v1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("a".repeat(51))))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(userRepository);
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 50})
    void acceptsUsernameLengthBoundaries(int length) throws Exception {
        UserEntity entity = existingUser();
        String username = "a".repeat(length);
        when(userRepository.findById(42)).thenReturn(Optional.of(entity));
        when(userRepository.save(entity)).thenReturn(entity);

        mockMvc.perform(put("/api/v1/users/42").contextPath("/api/v1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(username)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value(username));
    }

    @Test
    void updateReturnsNotFoundForMissingUser() throws Exception {
        when(userRepository.findById(42)).thenReturn(Optional.empty());

        mockMvc.perform(put("/api/v1/users/42").contextPath("/api/v1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("bob")))
                .andExpect(status().isNotFound());

        verify(userRepository, never()).save(any(UserEntity.class));
    }

    @Test
    void deletesExistingUserWithoutResponseBody() throws Exception {
        UserEntity entity = existingUser();
        when(userRepository.findById(42)).thenReturn(Optional.of(entity));

        mockMvc.perform(delete("/api/v1/users/42").contextPath("/api/v1"))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));

        verify(userRepository).delete(entity);
    }

    @Test
    void deleteReturnsNotFoundForMissingUser() throws Exception {
        when(userRepository.findById(42)).thenReturn(Optional.empty());

        mockMvc.perform(delete("/api/v1/users/42").contextPath("/api/v1"))
                .andExpect(status().isNotFound());

        verify(userRepository, never()).delete(any(UserEntity.class));
    }

    @Test
    void relatedOrdersPreventDeletionWithoutExposingDatabaseDetails() throws Exception {
        UserEntity entity = existingUser();
        when(userRepository.findById(42)).thenReturn(Optional.of(entity));
        doThrow(new DataIntegrityViolationException("private SQL constraint details"))
                .when(userRepository).delete(entity);

        mockMvc.perform(delete("/api/v1/users/42").contextPath("/api/v1"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Operation conflicts with existing data"))
                .andExpect(jsonPath("$.trace").doesNotExist())
                .andExpect(jsonPath("$.message").doesNotExist());
    }

    @Test
    void concurrentUsernameConflictReturnsSafeError() throws Exception {
        UserEntity entity = existingUser();
        when(userRepository.findById(42)).thenReturn(Optional.of(entity));
        when(userRepository.save(entity)).thenThrow(
                new DataIntegrityViolationException("private SQL unique constraint"));

        mockMvc.perform(put("/api/v1/users/42").contextPath("/api/v1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("bob")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Operation conflicts with existing data"))
                .andExpect(jsonPath("$.trace").doesNotExist());
    }

    @Test
    void rejectsNonNumericIds() throws Exception {
        mockMvc.perform(delete("/api/v1/users/invalid").contextPath("/api/v1"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(put("/api/v1/users/invalid").contextPath("/api/v1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("bob")))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(userRepository);
    }

    private String body(String username) {
        return objectMapper.writeValueAsString(UpdateUserRequest.builder().username(username).build());
    }

    private UserEntity existingUser() {
        UserEntity entity = new UserEntity();
        entity.setId(42);
        entity.setUsername("alice");
        entity.setEmail("alice@decathlon.com");
        entity.setCreate_at(Date.from(Instant.parse("2026-09-22T12:00:00Z")));
        return entity;
    }
}
