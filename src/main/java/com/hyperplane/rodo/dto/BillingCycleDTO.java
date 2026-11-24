package com.hyperplane.rodo.dto;

import com.hyperplane.rodo.model.enums.BillingCycleType;
import com.hyperplane.rodo.model.enums.DayOfWeekEnum;
import jakarta.validation.constraints.NotNull;

public record BillingCycleDTO(
        @NotNull BillingCycleType type,
        DayOfWeekEnum startDayOfWeek,
        DayOfWeekEnum endDayOfWeek
) {}
