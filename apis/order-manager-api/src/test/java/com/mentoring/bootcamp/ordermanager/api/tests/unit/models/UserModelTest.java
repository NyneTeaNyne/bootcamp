package com.mentoring.bootcamp.ordermanager.api.tests.unit.models;

import com.mentoring.bootcamp.ordermanager.api.entities.UserEntity;
import com.mentoring.bootcamp.ordermanager.api.entities.repositories.UserRepository;
import com.mentoring.bootcamp.ordermanager.api.mappers.UserMapper;
import com.mentoring.bootcamp.ordermanager.api.models.User;
import com.mentoring.bootcamp.ordermanager.api.services.UserService;
import com.mentoring.bootcamp.ordermanager.common.exception.BusinessException;
import com.mentoring.bootcamp.ordermanager.common.exception.InvalidDataException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class UserModelTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private UserMapper userMapper;
    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private UserService userService;

    @ParameterizedTest
    @ValueSource(ints = {1, 50})
    void should_accept_username_length_boundaries(int length) {
        assertThatCode(() -> user("a".repeat(length)).validate()).doesNotThrowAnyException();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    void should_reject_a_blank_username(String username) {
        assertThatThrownBy(() -> user(username).validate())
                .isInstanceOf(InvalidDataException.class)
                .hasMessage("Username must contain between 1 and 50 characters");
    }

    @Test
    void should_reject_a_username_longer_than_50_characters() {
        assertThatThrownBy(() -> user("a".repeat(51)).validate())
                .isInstanceOf(BusinessException.class);
    }

    @ParameterizedTest
    @ValueSource(ints = {8, 72})
    void should_accept_password_length_boundaries(int length) {
        User user = user("alice");
        user.setPassword("p".repeat(length));

        assertThatCode(user::validatePassword).doesNotThrowAnyException();
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "short", "1234567"})
    void should_reject_a_missing_or_too_short_password(String password) {
        User user = user("alice");
        user.setPassword(password);

        assertThatThrownBy(user::validatePassword)
                .isInstanceOf(InvalidDataException.class)
                .hasMessage("Password must contain between 8 and 72 characters");
    }

    @Test
    void should_reject_a_password_longer_than_72_characters() {
        User user = user("alice");
        user.setPassword("p".repeat(73));

        assertThatThrownBy(user::validatePassword).isInstanceOf(BusinessException.class);
    }

    @Test
    void should_reject_creation_without_touching_database_when_password_is_invalid() {
        User user = user("alice");
        user.setPassword("short");

        assertThatThrownBy(() -> userService.createUser(user))
                .isInstanceOf(InvalidDataException.class)
                .hasMessage("Password must contain between 8 and 72 characters");

        verifyNoInteractions(userRepository, userMapper, passwordEncoder);
    }

    @Test
    void should_accept_a_reference_with_only_an_id() {
        User reference = new User();
        reference.setId(1);

        // No username: a reference is not a full user, so validate() would reject it
        assertThatCode(reference::validateReference).doesNotThrowAnyException();
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(ints = {0, -1})
    void should_reject_a_reference_with_an_invalid_id(Integer id) {
        User reference = new User();
        reference.setId(id);

        assertThatThrownBy(reference::validateReference)
                .isInstanceOf(InvalidDataException.class)
                .hasMessage("A valid customer id is required");
    }

    @Test
    void should_reject_creation_without_touching_database_when_username_is_invalid() {
        assertThatThrownBy(() -> userService.createUser(user(" ")))
                .isInstanceOf(InvalidDataException.class)
                .hasMessage("Username must contain between 1 and 50 characters");

        verifyNoInteractions(userRepository, userMapper);
    }

    @Test
    void should_reject_creation_when_id_is_provided() {
        User user = user("alice");
        user.setId(42);

        assertThatThrownBy(() -> userService.createUser(user))
                .isInstanceOf(BusinessException.class);

        verify(userRepository, never()).save(any(UserEntity.class));
    }

    private User user(String username) {
        User user = new User();
        user.setUsername(username);
        return user;
    }
}
