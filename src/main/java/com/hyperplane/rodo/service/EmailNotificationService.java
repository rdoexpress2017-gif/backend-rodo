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

import java.text.NumberFormat;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

@Service
@Slf4j
@RequiredArgsConstructor
public class EmailNotificationService {

    private static final Locale PT_BR = Locale.of("pt", "BR");

    private final UserRepository userRepository;

    public Mono<String> sendDailyCompanyReportToMaster(ReportResponseDTO companyReport) {
        final Flux<User> masterUsersFlux = Optional.ofNullable(userRepository)
                .map(UserRepository::findAll)
                .orElse(Flux.empty())
                .filter(user -> user != null
                        && user.isActive()
                        && user.getRole() == UserRole.MASTER
                        && user.getEmail() != null
                        && !user.getEmail().isBlank());

        return masterUsersFlux
                .collectList()
                .map(masters -> {
                    final List<String> recipients = masters.isEmpty()
                            ? List.of("admin@rodo.com")
                            : masters.stream().map(User::getEmail).distinct().toList();

                    final String emailSubject = String.format(
                            "[RDO Express] Relatório Diário da Empresa (RESUMO GERAL) - %s",
                            companyReport.reportCode()
                    );
                    final String emailBody = buildCompanyReportEmailBody(companyReport);

                    recipients.forEach(recipient ->
                            log.info(
                                    "E-mail do Relatório Diário da Empresa ({}) enviado com sucesso para o usuário MASTER <{}>:\nAssunto: {}\n{}",
                                    companyReport.reportCode(),
                                    recipient,
                                    emailSubject,
                                    emailBody
                            )
                    );

                    return String.join(", ", recipients);
                });
    }

    public String buildCompanyReportEmailBody(ReportResponseDTO report) {
        final NumberFormat fmt = NumberFormat.getCurrencyInstance(PT_BR);
        final CompanyReportSummaryDTO s = report.companySummary();
        if (s == null) {
            return "Relatório da Empresa " + report.reportCode();
        }

        final StringBuilder sb = new StringBuilder();
        sb.append("=== RESUMO GERAL (FINANCEIRO RDO) ===\n");
        sb.append(String.format("• Receitas    : %s (Valor total de pedidos: %d)\n", fmt.format(s.totalRevenue()), s.totalOrders()));
        sb.append(String.format("• Já Recebido : %s (%d pagos em caixa)\n", fmt.format(s.paidRevenue()), s.paidOrdersCount()));
        sb.append(String.format("• A Receber   : %s (%d a receber - não pagos)\n", fmt.format(s.pendingRevenue()), s.pendingOrdersCount()));
        sb.append(String.format("• Lucro       : %s (Somente sobre pedidos pagos)\n", fmt.format(s.netProfit()), s.paidOrdersCount()));
        sb.append(String.format("• Repasses    : %s (Pagos: %s)\n", fmt.format(s.totalCommissions()), fmt.format(s.paidCommissions())));
        sb.append(String.format("• Despesas    : %s (Lançamentos do período)\n", fmt.format(s.totalExpenses())));
        sb.append(String.format("• Colaborador : %s (Comissão motoboys)\n", fmt.format(s.collaboratorShare())));
        sb.append(String.format("• Registros   : %d (%d pagos • %d pendentes)\n\n", s.totalOrders(), s.paidOrdersCount(), s.pendingOrdersCount()));

        sb.append("=== AGREGADO POR MOTOBOY (VALORES A PAGAR NO DIA) ===\n");
        if (s.motoboyAggregates() != null) {
            for (final MotoboyReportAggregateDTO agg : s.motoboyAggregates()) {
                sb.append(String.format(
                        "• %s (%s): %d pedidos | %.1f km | Bruto: %s | Valor a Pagar: %s\n",
                        agg.motoboyName(),
                        agg.motoboyType(),
                        agg.totalOrders(),
                        agg.totalDistanceKm(),
                        fmt.format(agg.grossAmount()),
                        fmt.format(agg.amountToPay())
                ));
            }
        }
        sb.append(String.format("=> VALOR FINAL DE TODOS OS MOTOBOYS JUNTOS NO DIA: %s\n",
                fmt.format(s.totalMotoboysAmountToPay())));
        return sb.toString();
    }
}
