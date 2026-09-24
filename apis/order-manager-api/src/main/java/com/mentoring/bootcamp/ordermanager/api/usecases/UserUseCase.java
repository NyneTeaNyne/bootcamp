package com.mentoring.bootcamp.ordermanager.api.usecases;

import com.mentoring.bootcamp.ordermanager.api.models.User;

import java.util.List;
import java.util.Optional;

public interface UserUseCase {
    User createUser(User user);

    List<User> getUsers();

    Optional<User> getUser(Integer id);

    Optional<User> getUserByUsername(String username);

    Optional<User> updateUser(Integer id, User user);

    boolean deleteUser(Integer id);
}
