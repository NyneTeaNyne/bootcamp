package com.mentoring.bootcamp.ordermanager.api.controllers.dtos;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Request object for creating a new user")
public class CreateUserRequest {
    @Schema(description = "Unique username, 1 to 50 characters", example = "blue_hiker_26")
    private String username;

    @Schema(description = "Password, 8 to 72 characters. Stored hashed (BCrypt), never returned by the API",
            example = "Str0ngPassw0rd!")
    private String password;
}
