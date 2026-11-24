package com.hyperplane.rodo.dto;

import com.hyperplane.rodo.model.enums.UserRole;

public record AuthLoginResponseDTO(
        String token,
        String userId,
        String name,
        String email,
        UserRole role
) {}
