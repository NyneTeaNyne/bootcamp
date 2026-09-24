package com.mentoring.bootcamp.ordermanager.api.services;

import com.mentoring.bootcamp.ordermanager.api.models.User;
import com.mentoring.bootcamp.ordermanager.api.usecases.UserUseCase;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Bridge between Spring Security and our users: used by the login page to check username and password.
 */
@Service
public class CustomUserDetailsService implements UserDetailsService {

    private final UserUseCase userUseCase;

    public CustomUserDetailsService(UserUseCase userUseCase) {
        this.userUseCase = userUseCase;
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        User user = userUseCase.getUserByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException(username));
        return new org.springframework.security.core.userdetails.User(
                user.getUsername(),
                user.getPassword(),
                // As in the bootcamp module: no role column yet, every user is an ADMIN
                List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
    }
}
