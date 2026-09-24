package com.mentoring.bootcamp.ordermanager.api.tests.unit.controllers;

import com.mentoring.bootcamp.ordermanager.api.controllers.UserController;
import com.mentoring.bootcamp.ordermanager.api.entities.UserEntity;
import com.mentoring.bootcamp.ordermanager.api.entities.repositories.UserRepository;
import com.mentoring.bootcamp.ordermanager.api.mappers.UserMapper;
import com.mentoring.bootcamp.ordermanager.api.services.UserService;
import com.mentoring.bootcamp.ordermanager.common.web.ApiExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Optional;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class UserRetrievalTests {
    @Mock
    private UserRepository userRepository;

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
    void returnsUsers() throws Exception {
        when(userRepository.findAll()).thenReturn(List.of(user(1, "alice"), user(2, "bob")));

        mockMvc.perform(get("/v1/users").contextPath("/v1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].length()").value(4))
                .andExpect(jsonPath("$[0].id").value(1))
                .andExpect(jsonPath("$[0].username").value("alice"))
                .andExpect(jsonPath("$[0].email").value("alice@decathlon.com"))
                .andExpect(jsonPath("$[0].create_at").isNotEmpty())
                .andExpect(jsonPath("$[1].id").value(2))
                .andExpect(jsonPath("$[1].username").value("bob"));

        verify(userRepository).findAll();
    }

    @Test
    void returnsEmptyListWhenNoUsersExist() throws Exception {
        when(userRepository.findAll()).thenReturn(List.of());

        mockMvc.perform(get("/v1/users").contextPath("/v1"))
                .andExpect(status().isOk())
                .andExpect(content().json("[]"));
    }

    @Test
    void returnsUserById() throws Exception {
        when(userRepository.findById(42)).thenReturn(Optional.of(user(42, "alice")));

        mockMvc.perform(get("/v1/users/42").contextPath("/v1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(4))
                .andExpect(jsonPath("$.id").value(42))
                .andExpect(jsonPath("$.username").value("alice"))
                .andExpect(jsonPath("$.email").value("alice@decathlon.com"))
                .andExpect(jsonPath("$.create_at").isNotEmpty());

        verify(userRepository).findById(42);
    }

    @Test
    void returnsNotFoundForMissingUser() throws Exception {
        when(userRepository.findById(42)).thenReturn(Optional.empty());

        mockMvc.perform(get("/v1/users/42").contextPath("/v1"))
                .andExpect(status().isNotFound());
    }

    @Test
    void rejectsNonNumericId() throws Exception {
        mockMvc.perform(get("/v1/users/invalid").contextPath("/v1"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(userRepository);
    }

    private UserEntity user(int id, String username) {
        UserEntity entity = new UserEntity();
        entity.setId(id);
        entity.setUsername(username);
        entity.setEmail(username + "@decathlon.com");
        entity.setCreate_at(Date.from(Instant.parse("2026-09-22T12:00:00Z")));
        return entity;
    }
}
