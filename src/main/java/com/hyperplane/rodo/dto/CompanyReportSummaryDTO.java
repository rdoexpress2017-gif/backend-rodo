package com.hyperplane.rodo.dto;

import java.math.BigDecimal;
import java.util.List;

public record CompanyReportSummaryDTO(
        BigDecimal totalRevenue,
        BigDecimal paidRevenue,
        BigDecimal pendingRevenue,
        BigDecimal netProfit,
        BigDecimal totalCommissions,
        BigDecimal paidCommissions,
        BigDecimal totalExpenses,
        BigDecimal collaboratorShare,
        long totalOrders,
        long paidOrdersCount,
        long pendingOrdersCount,
        List<MotoboyReportAggregateDTO> motoboyAggregates,
        BigDecimal totalMotoboysAmountToPay,
        Boolean emailSentToMaster,
        String masterEmailRecipient
) {}
