package com.mentoring.bootcamp.ordermanager.api.mappers;

import com.mentoring.bootcamp.ordermanager.api.controllers.dtos.CreateUserRequest;
import com.mentoring.bootcamp.ordermanager.api.controllers.dtos.UpdateUserRequest;
import com.mentoring.bootcamp.ordermanager.api.controllers.dtos.UserResponse;
import com.mentoring.bootcamp.ordermanager.api.entities.UserEntity;
import com.mentoring.bootcamp.ordermanager.api.models.User;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

import java.util.List;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface UserMapper {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "email", ignore = true)
    @Mapping(target = "create_at", ignore = true)
    User toModel(CreateUserRequest request);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "email", ignore = true)
    @Mapping(target = "create_at", ignore = true)
    User toModel(UpdateUserRequest request);

    UserEntity toEntity(User user);

    User swallowToModel(UserEntity userEntity);

    List<User> swallowToModel(List<UserEntity> userEntities);

    UserResponse toResponse(User user);

    List<UserResponse> toResponse(List<User> users);
}
