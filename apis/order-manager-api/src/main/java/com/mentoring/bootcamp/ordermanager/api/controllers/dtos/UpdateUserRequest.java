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
@Schema(description = "Request object for renaming a user")
public class UpdateUserRequest {
    @Schema(description = "New unique username, 1 to 50 characters", example = "green_runner_27")
    private String username;
}
