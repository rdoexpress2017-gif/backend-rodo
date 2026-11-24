package com.hyperplane.rodo.dto;

import com.hyperplane.rodo.model.enums.OrderStatus;

import java.math.BigDecimal;
import java.time.Instant;

public record OrderResponseDTO(
        String id,
        Long orderNumber,
        String clientId,
        String clientName,
        String motoboyId,
        String motoboyName,
        String requesterName,
        String createdByUserId,
        String createdByUserName,
        AddressDTO originAddress,
        AddressDTO destinationAddress,
        Double routeDistanceKm,
        BigDecimal amount,
        BigDecimal motoboyCommissionAmount,
        OrderStatus status,
        String paymentOrderId,
        Instant createdAt,
        Instant collectedAt,
        Instant deliveredAt,
        Instant paidAt,
        Instant updatedAt
) {}
