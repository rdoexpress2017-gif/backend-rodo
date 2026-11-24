package com.hyperplane.rodo.mapper;

import com.hyperplane.rodo.dto.UserRequestDTO;
import com.hyperplane.rodo.dto.UserResponseDTO;
import com.hyperplane.rodo.model.User;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface UserMapper {
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "passwordHash", ignore = true)
    @Mapping(target = "active", expression = "java(dto.active() != null ? dto.active() : true)")
    @Mapping(target = "createdAt", expression = "java(java.time.Instant.now())")
    @Mapping(target = "updatedAt", expression = "java(java.time.Instant.now())")
    User toEntity(UserRequestDTO dto);

    UserResponseDTO toResponseDTO(User entity);
}
