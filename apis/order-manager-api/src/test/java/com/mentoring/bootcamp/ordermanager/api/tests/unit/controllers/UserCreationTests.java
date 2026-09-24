package com.mentoring.bootcamp.ordermanager.api.tests.unit.controllers;

import com.mentoring.bootcamp.ordermanager.api.controllers.UserController;
import com.mentoring.bootcamp.ordermanager.api.controllers.dtos.CreateUserRequest;
import com.mentoring.bootcamp.ordermanager.api.entities.UserEntity;
import com.mentoring.bootcamp.ordermanager.api.entities.repositories.UserRepository;
import com.mentoring.bootcamp.ordermanager.api.mappers.UserMapper;
import com.mentoring.bootcamp.ordermanager.api.models.User;
import com.mentoring.bootcamp.ordermanager.api.services.UserService;
import com.mentoring.bootcamp.ordermanager.common.exception.BusinessException;
import com.mentoring.bootcamp.ordermanager.common.web.ApiExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class UserCreationTests {
    @Mock
    private UserRepository userRepository;

    private final UserMapper userMapper = Mappers.getMapper(UserMapper.class);
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
    private UserService userService;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        userService = new UserService(userRepository, userMapper, passwordEncoder);
        mockMvc = MockMvcBuilders.standaloneSetup(new UserController(userService, userMapper))
                .setControllerAdvice(new ApiExceptionHandler())
                .build();
    }

    @Test
    void requestMapsOnlyUsernameAndPassword() {
        CreateUserRequest request = new CreateUserRequest();
        request.setUsername("alice");
        request.setPassword("Str0ngPassw0rd!");

        User user = userMapper.toModel(request);

        assertEquals("alice", user.getUsername());
        assertEquals("Str0ngPassw0rd!", user.getPassword());
        assertNull(user.getId());
        assertNull(user.getEmail());
        assertNull(user.getCreate_at());
    }

    @Test
    void createsUserAndReturnsServerGeneratedFields() throws Exception {
        UserEntity saved = new UserEntity();
        saved.setId(42);
        saved.setUsername("alice");
        saved.setEmail("alice@decathlon.com");
        saved.setCreate_at(Date.from(Instant.parse("2026-09-22T12:00:00Z")));
        when(userRepository.save(any(UserEntity.class))).thenReturn(saved);

        mockMvc.perform(post("/v1/users")
                        .contextPath("/v1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                CreateUserRequest.builder().username("alice").password("Str0ngPassw0rd!").build())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.length()").value(4))
                .andExpect(jsonPath("$.id").value(42))
                .andExpect(jsonPath("$.username").value("alice"))
                .andExpect(jsonPath("$.email").value("alice@decathlon.com"))
                .andExpect(jsonPath("$.create_at").isNotEmpty());

        ArgumentCaptor<UserEntity> entity = ArgumentCaptor.forClass(UserEntity.class);
        verify(userRepository).save(entity.capture());
        assertNull(entity.getValue().getId());
        assertEquals("alice", entity.getValue().getUsername());
        assertEquals("alice@decathlon.com", entity.getValue().getEmail());
        assertNull(entity.getValue().getCreate_at());
        // Only the BCrypt hash is stored, never the raw password
        assertNotEquals("Str0ngPassw0rd!", entity.getValue().getPassword());
        assertTrue(passwordEncoder.matches("Str0ngPassw0rd!", entity.getValue().getPassword()));
    }

    @Test
    void rejectsDuplicateUsername() {
        User user = new User();
        user.setUsername("alice");
        user.setPassword("Str0ngPassw0rd!");
        when(userRepository.existsByUsername("alice")).thenReturn(true);

        assertThrows(BusinessException.class, () -> userService.createUser(user));
        verify(userRepository, never()).save(any(UserEntity.class));
    }

    @Test
    void rejectsExistingId() {
        User user = new User();
        user.setId(42);

        assertThrows(BusinessException.class, () -> userService.createUser(user));
        verify(userRepository, never()).save(any(UserEntity.class));
    }
}
