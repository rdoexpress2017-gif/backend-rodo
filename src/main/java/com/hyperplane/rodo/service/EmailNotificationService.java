package com.hyperplane.rodo.service;

import com.hyperplane.rodo.dto.CompanyReportSummaryDTO;
import com.hyperplane.rodo.dto.MotoboyReportAggregateDTO;
import com.hyperplane.rodo.dto.ReportResponseDTO;
import com.hyperplane.rodo.model.User;
import com.hyperplane.rodo.model.enums.UserRole;
import com.hyperplane.rodo.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class EmailNotificationService {

    private static final Locale PT_BR = Locale.of("pt", "BR");

    private final UserRepository userRepository;

    public Mono<String> sendDailyCompanyReportToMaster(ReportResponseDTO companyReport) {
        return Optional.ofNullable(userRepository)
                .map(UserRepository::findAll)
                .orElse(Flux.empty())
                .filter(user -> user != null
                        && user.isActive()
                        && user.getRole() == UserRole.MASTER
                        && user.getEmail() != null
                        && !user.getEmail().isBlank())
                .collectList()
                .map(masters -> (masters.isEmpty()
                        ? List.of("admin@rodo.com")
                        : masters.stream().map(User::getEmail).distinct().toList())
                        .stream()
                        .peek(recipient -> log.info(
                                "E-mail do Relatório Diário da Empresa ({}) enviado com sucesso para o usuário MASTER <{}>:\nAssunto: {}\n{}",
                                companyReport.reportCode(),
                                recipient,
                                String.format("[RDO Express] Relatório Diário da Empresa (RESUMO GERAL) - %s", companyReport.reportCode()),
                                buildCompanyReportEmailBody(companyReport)
                        ))
                        .collect(Collectors.joining(", ")));
    }

    public String buildCompanyReportEmailBody(ReportResponseDTO report) {
        return Optional.ofNullable(report.companySummary())
                .map(s -> String.join(
                        "\n",
                        "=== RESUMO GERAL (FINANCEIRO RDO) ===",
                        String.format("• Receitas       : %s (Valor total de pedidos: %d)", formatCurrency(s.totalRevenue()), s.totalOrders()),
                        String.format("• Já Recebido    : %s (%d pagos em caixa)", formatCurrency(s.paidRevenue()), s.paidOrdersCount()),
                        String.format("• A Receber      : %s (%d a receber - não pagos)", formatCurrency(s.pendingRevenue()), s.pendingOrdersCount()),
                        String.format("• Lucro (Pagos)  : %s (Somente sobre pedidos pagos)", formatCurrency(s.netProfit())),
                        String.format("• Lucro Estimado : %s (Pedidos coletados/entregues/pagos - repasses - despesas)", formatCurrency(resolveEstimatedProfit(s))),
                        String.format("• Repasses       : %s (Pagos: %s)", formatCurrency(s.totalCommissions()), formatCurrency(s.paidCommissions())),
                        String.format("• Despesas       : %s (Lançamentos do período)", formatCurrency(s.totalExpenses())),
                        String.format("• Colaborador    : %s (Comissão motoboys)", formatCurrency(s.collaboratorShare())),
                        String.format("• Registros      : %d (%d pagos • %d pendentes)", s.totalOrders(), s.paidOrdersCount(), s.pendingOrdersCount()),
                        "",
                        "=== AGREGADO POR MOTOBOY (VALORES A PAGAR NO DIA) ===",
                        formatMotoboyAggregates(s.motoboyAggregates()),
                        String.format("=> VALOR FINAL DE TODOS OS MOTOBOYS JUNTOS NO DIA: %s", formatCurrency(s.totalMotoboysAmountToPay()))
                ))
                .orElseGet(() -> "Relatório da Empresa " + report.reportCode());
    }

    private BigDecimal resolveEstimatedProfit(CompanyReportSummaryDTO s) {
        return s.estimatedProfit() != null
                ? s.estimatedProfit()
                : (s.totalRevenue() != null && s.totalCommissions() != null && s.totalExpenses() != null
                ? s.totalRevenue().subtract(s.totalCommissions()).subtract(s.totalExpenses())
                : BigDecimal.ZERO);
    }

    private String formatMotoboyAggregates(List<MotoboyReportAggregateDTO> aggregates) {
        return Optional.ofNullable(aggregates)
                .orElse(List.of())
                .stream()
                .map(agg -> String.format(
                        "• %s (%s): %d pedidos | %.1f km | Bruto: %s | Valor a Pagar: %s",
                        agg.motoboyName(),
                        agg.motoboyType(),
                        agg.totalOrders(),
                        agg.totalDistanceKm(),
                        formatCurrency(agg.grossAmount()),
                        formatCurrency(agg.amountToPay())
                ))
                .collect(Collectors.joining("\n"));
    }

    private String formatCurrency(BigDecimal value) {
        return NumberFormat.getCurrencyInstance(PT_BR).format(value != null ? value : BigDecimal.ZERO);
    }
}
