package com.mentoring.bootcamp.ordermanager.api.entities.repositories;

import com.mentoring.bootcamp.ordermanager.api.entities.UserEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserRepository extends JpaRepository<UserEntity, Integer> {
    boolean existsByUsername(String username);

    boolean existsByUsernameAndIdNot(String username, Integer id);

    Optional<UserEntity> findByUsername(String username);
}
