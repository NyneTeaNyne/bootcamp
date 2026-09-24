package com.mentoring.bootcamp.ordermanager.api.services;

import com.mentoring.bootcamp.ordermanager.api.config.CacheConfig;
import com.mentoring.bootcamp.ordermanager.api.entities.repositories.UserRepository;
import com.mentoring.bootcamp.ordermanager.api.mappers.UserMapper;
import com.mentoring.bootcamp.ordermanager.api.models.User;
import com.mentoring.bootcamp.ordermanager.api.usecases.UserUseCase;
import com.mentoring.bootcamp.ordermanager.common.exception.AlreadyExistsException;
import com.mentoring.bootcamp.ordermanager.common.exception.InvalidDataException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
public class UserService implements UserUseCase {
    private static final String DECATHLON_MAIL = "@decathlon.com";

    private final UserRepository userRepository;
    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;

    public UserService(UserRepository userRepository, UserMapper userMapper, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.userMapper = userMapper;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public User createUser(User user) {
        if(user.getId() != null) {
            throw new InvalidDataException("Id should be null when creating user");
        }
        user.validate();
        user.validatePassword();
        if(userRepository.existsByUsername(user.getUsername())) {
            throw new AlreadyExistsException("Username already exists");
        }
        user.setEmail(user.getUsername() + DECATHLON_MAIL);
        // Never store the raw password
        user.setPassword(passwordEncoder.encode(user.getPassword()));

        return userMapper.swallowToModel(userRepository.save(userMapper.toEntity(user)));
    }

    @Override
    public List<User> getUsers() {
        return userMapper.swallowToModel(userRepository.findAll());
    }

    @Override
    public Optional<User> getUser(Integer id) {
        return userRepository.findById(id).map(userMapper::swallowToModel);
    }

    @Override
    public Optional<User> getUserByUsername(String username) {
        return userRepository.findByUsername(username).map(userMapper::swallowToModel);
    }

    @Override
    @Transactional
    // Cached orders show this data (customer name, item name and price): drop them
    @CacheEvict(cacheNames = {CacheConfig.ORDER, CacheConfig.ORDERS}, allEntries = true)
    public Optional<User> updateUser(Integer id, User user) {
        user.validate();
        String username = user.getUsername();

        return userRepository.findById(id).map(entity -> {
            if (userRepository.existsByUsernameAndIdNot(username, id)) {
                throw new AlreadyExistsException("Username already exists");
            }
            entity.setUsername(username);
            entity.setEmail(username + DECATHLON_MAIL);
            return userMapper.swallowToModel(userRepository.save(entity));
        });
    }

    @Override
    @Transactional
    // Cached orders show this data (customer name, item name and price): drop them
    @CacheEvict(cacheNames = {CacheConfig.ORDER, CacheConfig.ORDERS}, allEntries = true)
    public boolean deleteUser(Integer id) {
        return userRepository.findById(id).map(entity -> {
            userRepository.delete(entity);
            return true;
        }).orElse(false);
    }
}
