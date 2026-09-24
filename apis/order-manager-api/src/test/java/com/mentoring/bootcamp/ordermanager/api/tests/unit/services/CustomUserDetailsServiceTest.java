package com.mentoring.bootcamp.ordermanager.api.tests.unit.services;

import com.mentoring.bootcamp.ordermanager.api.models.User;
import com.mentoring.bootcamp.ordermanager.api.services.CustomUserDetailsService;
import com.mentoring.bootcamp.ordermanager.api.usecases.UserUseCase;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CustomUserDetailsServiceTest {

    @Mock
    private UserUseCase userUseCase;

    @InjectMocks
    private CustomUserDetailsService userDetailsService;

    @Test
    void should_load_user_with_its_hashed_password_and_the_admin_role() {
        User user = new User();
        user.setUsername("alice");
        user.setPassword("$2a$10$hashedPassword");
        when(userUseCase.getUserByUsername("alice")).thenReturn(Optional.of(user));

        UserDetails details = userDetailsService.loadUserByUsername("alice");

        assertThat(details.getUsername()).isEqualTo("alice");
        assertThat(details.getPassword()).isEqualTo("$2a$10$hashedPassword");
        assertThat(details.getAuthorities())
                .extracting(GrantedAuthority::getAuthority)
                .containsExactly("ROLE_ADMIN");
    }

    @Test
    void should_throw_when_user_does_not_exist() {
        when(userUseCase.getUserByUsername("ghost")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userDetailsService.loadUserByUsername("ghost"))
                .isInstanceOf(UsernameNotFoundException.class);
    }
}
