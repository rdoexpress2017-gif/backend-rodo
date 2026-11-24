package com.hyperplane.rodo.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record ExpenseResponseDTO(
        String id,
        Instant expenseDate,
        BigDecimal amount,
        String description,
        Instant createdAt,
        Instant updatedAt
) {}
