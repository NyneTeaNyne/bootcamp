package com.mentoring.bootcamp.ordermanager.api.controllers;

import com.mentoring.bootcamp.ordermanager.api.controllers.dtos.CreateUserRequest;
import com.mentoring.bootcamp.ordermanager.api.controllers.dtos.UpdateUserRequest;
import com.mentoring.bootcamp.ordermanager.api.controllers.dtos.UserResponse;
import com.mentoring.bootcamp.ordermanager.api.mappers.UserMapper;
import com.mentoring.bootcamp.ordermanager.api.models.User;
import com.mentoring.bootcamp.ordermanager.api.usecases.UserUseCase;
import com.mentoring.bootcamp.ordermanager.common.exception.NotFoundException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/users")
@Tag(name = "Users", description = "Operations related to customer accounts")
public class UserController {
    private final UserUseCase userUseCase;
    private final UserMapper userMapper;

    public UserController(UserUseCase userUseCase, UserMapper userMapper) {
        this.userUseCase = userUseCase;
        this.userMapper = userMapper;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create a user", description = "The email is generated from the username: <username>@decathlon.com")
    @ApiResponse(responseCode = "201", description = "User successfully created")
    @ApiResponse(responseCode = "400", description = "Invalid username, or username already exists")
    public UserResponse createUser(@RequestBody CreateUserRequest request) {
        User userModel = userMapper.toModel(request);
        return userMapper.toResponse(userUseCase.createUser(userModel));
    }

    @GetMapping
    @Operation(summary = "List all users")
    @ApiResponse(responseCode = "200", description = "List of users (possibly empty)")
    public List<UserResponse> getUsers() {
        return userMapper.toResponse(userUseCase.getUsers());
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a user by id")
    @ApiResponse(responseCode = "200", description = "User found")
    @ApiResponse(responseCode = "404", description = "User not found")
    public UserResponse getUser(@Parameter(description = "User id", example = "1") @PathVariable("id") Integer id) {
        User user = userUseCase.getUser(id)
                .orElseThrow(() -> new NotFoundException("User not found"));
        return userMapper.toResponse(user);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Rename a user", description = "Changes the username and regenerates the email accordingly")
    @ApiResponse(responseCode = "200", description = "User updated")
    @ApiResponse(responseCode = "400", description = "Invalid username, or username used by another user")
    @ApiResponse(responseCode = "404", description = "User not found")
    public UserResponse updateUser(@Parameter(description = "User id", example = "1") @PathVariable("id") Integer id,
                                   @RequestBody UpdateUserRequest request) {
        User user = userUseCase.updateUser(id, userMapper.toModel(request))
                .orElseThrow(() -> new NotFoundException("User not found"));
        return userMapper.toResponse(user);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Delete a user")
    @ApiResponse(responseCode = "204", description = "User deleted")
    @ApiResponse(responseCode = "404", description = "User not found")
    @ApiResponse(responseCode = "409", description = "User still has orders and cannot be deleted")
    public void deleteUser(@Parameter(description = "User id", example = "1") @PathVariable("id") Integer id) {
        if (!userUseCase.deleteUser(id)) {
            throw new NotFoundException("User not found");
        }
    }
}
