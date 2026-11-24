package com.hyperplane.rodo.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

public record SystemSettingsResponseDTO(
        String id,
        BigDecimal pricePerKm,
        BigDecimal baseRate,
        Double defaultMotoboyCommissionPercentage,
        String updatedByUserId,
        Instant updatedAt,
        Boolean dailyJobExecutedToday,
        LocalDate lastDailyJobExecutionDate,
        String dailyJobExecutionTime
) {}
