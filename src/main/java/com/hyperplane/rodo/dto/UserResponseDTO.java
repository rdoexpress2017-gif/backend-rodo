package com.hyperplane.rodo.dto;

import com.hyperplane.rodo.model.enums.UserRole;

import java.time.Instant;

public record UserResponseDTO(
        String id,
        String name,
        String email,
        String phone,
        UserRole role,
        boolean active,
        Instant createdAt,
        Instant updatedAt
) {}
