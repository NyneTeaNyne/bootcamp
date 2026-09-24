package com.mentoring.bootcamp.ordermanager.api.controllers.dtos;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

import java.util.Date;

@Getter
@Setter
@Schema(description = "A user account")
public class UserResponse {
    @Schema(description = "Generated user id", example = "1")
    private Integer id;
    @Schema(description = "Unique username", example = "blue_hiker_26")
    private String username;
    @Schema(description = "Corporate email generated from the username", example = "blue_hiker_26@decathlon.com")
    private String email;
    @Schema(description = "Creation date, set by the database")
    private Date create_at;
}
