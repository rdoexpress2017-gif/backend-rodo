package com.hyperplane.rodo.dto;

import com.hyperplane.rodo.model.enums.MotoboyType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record MotoboyRequestDTO(
        @NotBlank String name,
        @NotBlank String cpf,
        @NotBlank String phone,
        @NotNull MotoboyType type,
        Boolean active
) {}
