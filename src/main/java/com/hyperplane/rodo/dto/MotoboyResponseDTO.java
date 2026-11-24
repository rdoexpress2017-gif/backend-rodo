package com.hyperplane.rodo.dto;

import com.hyperplane.rodo.model.enums.MotoboyType;

import java.time.Instant;

public record MotoboyResponseDTO(
        String id,
        String name,
        String cpf,
        String phone,
        MotoboyType type,
        boolean active,
        Instant createdAt,
        Instant updatedAt
) {}
