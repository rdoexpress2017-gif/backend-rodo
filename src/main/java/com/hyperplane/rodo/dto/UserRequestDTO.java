package com.hyperplane.rodo.dto;

import com.hyperplane.rodo.model.enums.UserRole;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record UserRequestDTO(
        @NotBlank String name,
        @NotBlank @Email String email,
        String password,
        String phone,
        @NotNull UserRole role,
        Boolean active
) {}
