package com.hyperplane.rodo.dto;

import java.math.BigDecimal;

public record FinancialSummaryDTO(
        BigDecimal totalRevenue,
        BigDecimal totalCommissions,
        BigDecimal companyShare,
        BigDecimal collaboratorShare,
        long totalOrders,
        BigDecimal netBalance,
        BigDecimal totalExpenses,
        BigDecimal paidRevenue,
        BigDecimal pendingRevenue,
        BigDecimal paidCommissions,
        long paidOrdersCount,
        long pendingOrdersCount
) {}
