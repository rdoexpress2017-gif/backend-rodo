package com.hyperplane.rodo.dto;

import com.hyperplane.rodo.model.enums.PaymentOrderStatus;
import com.hyperplane.rodo.model.enums.ReportType;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record ReportResponseDTO(
        String id,
        String reportCode,
        ReportType type,
        String targetEntityId,
        String targetEntityName,
        String targetEntitySubtype,
        List<String> orderIds,
        Instant periodStart,
        Instant periodEnd,
        Integer totalOrders,
        BigDecimal totalAmount,
        Double totalDistanceKm,
        String paymentOrderId,
        PaymentOrderStatus paymentOrderStatus,
        String generatedByUserId,
        String generatedByUserName,
        Instant createdAt,
        Instant updatedAt,
        CompanyReportSummaryDTO companySummary
) {
    public ReportResponseDTO(
            String id,
            String reportCode,
            ReportType type,
            String targetEntityId,
            String targetEntityName,
            String targetEntitySubtype,
            List<String> orderIds,
            Instant periodStart,
            Instant periodEnd,
            Integer totalOrders,
            BigDecimal totalAmount,
            Double totalDistanceKm,
            String paymentOrderId,
            PaymentOrderStatus paymentOrderStatus,
            String generatedByUserId,
            String generatedByUserName,
            Instant createdAt,
            Instant updatedAt
    ) {
        this(
                id,
                reportCode,
                type,
                targetEntityId,
                targetEntityName,
                targetEntitySubtype,
                orderIds,
                periodStart,
                periodEnd,
                totalOrders,
                totalAmount,
                totalDistanceKm,
                paymentOrderId,
                paymentOrderStatus,
                generatedByUserId,
                generatedByUserName,
                createdAt,
                updatedAt,
                null
        );
    }
}
