package com.hyperplane.rodo.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;

public record SystemSettingsRequestDTO(
        @NotNull @PositiveOrZero BigDecimal pricePerKm,
        @NotNull @PositiveOrZero BigDecimal baseRate,
        @NotNull @Positive Double defaultMotoboyCommissionPercentage,
        String dailyJobExecutionTime
) {}
