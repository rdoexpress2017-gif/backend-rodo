package com.hyperplane.rodo.dto;

import com.hyperplane.rodo.model.enums.ReportType;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;

public record CustomReportRequestDTO(
        @NotNull ReportType type,
        String targetEntityId,
        @NotNull Instant startDate,
        @NotNull Instant endDate,
        boolean generatePaymentOrder
) {}
