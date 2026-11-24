package com.hyperplane.rodo.dto;

import java.math.BigDecimal;

public record MotoboyReportAggregateDTO(
        String motoboyId,
        String motoboyName,
        String motoboyType,
        int totalOrders,
        double totalDistanceKm,
        BigDecimal grossAmount,
        BigDecimal amountToPay
) {}
