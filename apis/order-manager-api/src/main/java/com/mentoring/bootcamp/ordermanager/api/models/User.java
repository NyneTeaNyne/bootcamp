package com.mentoring.bootcamp.ordermanager.api.models;

import com.mentoring.bootcamp.ordermanager.common.exception.InvalidDataException;
import lombok.Getter;
import lombok.Setter;

import java.util.Date;

@Getter
@Setter
public class User {
    private static final int USERNAME_MAX_LENGTH = 50;
    private static final int PASSWORD_MIN_LENGTH = 8;
    // BCrypt only uses the first 72 bytes of a password
    private static final int PASSWORD_MAX_LENGTH = 72;

    private Integer id;
    private String username;
    private String email;
    private String password;
    private Date create_at;

    public void validate() {
        if (username == null || username.isBlank() || username.length() > USERNAME_MAX_LENGTH) {
            throw new InvalidDataException("Username must contain between 1 and 50 characters");
        }
    }

    /**
     * Validates the raw password chosen when the account is created (before it is hashed).
     */
    public void validatePassword() {
        if (password == null || password.length() < PASSWORD_MIN_LENGTH || password.length() > PASSWORD_MAX_LENGTH) {
            throw new InvalidDataException("Password must contain between 8 and 72 characters");
        }
    }

    /**
     * Validates a user used as a reference (e.g. the customer of an order): only the id is provided.
     */
    public void validateReference() {
        if (id == null || id <= 0) {
            throw new InvalidDataException("A valid customer id is required");
        }
    }
}
