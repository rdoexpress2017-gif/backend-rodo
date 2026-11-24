package com.hyperplane.rodo.dto;

import com.hyperplane.rodo.model.enums.PaymentOrderStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record PaymentOrderResponseDTO(
        String id,
        String reportId,
        String clientId,
        String clientName,
        List<String> orderIds,
        BigDecimal totalAmount,
        PaymentOrderStatus status,
        Instant issueDate,
        Instant settledAt,
        Instant createdAt,
        Instant updatedAt
) {}
