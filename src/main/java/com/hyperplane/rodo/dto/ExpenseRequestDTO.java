package com.hyperplane.rodo.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.time.Instant;

public record ExpenseRequestDTO(
        @NotNull(message = "A data do lançamento é obrigatória")
        Instant expenseDate,

        @NotNull(message = "O valor do gasto é obrigatório")
        @Positive(message = "O valor do gasto deve ser maior que zero")
        BigDecimal amount,

        @NotBlank(message = "A descrição do gasto é obrigatória")
        String description
) {}
