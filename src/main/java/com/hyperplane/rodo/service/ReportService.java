package com.hyperplane.rodo.service;

import com.hyperplane.rodo.dto.CompanyReportSummaryDTO;
import com.hyperplane.rodo.dto.CustomReportRequestDTO;
import com.hyperplane.rodo.dto.MotoboyReportAggregateDTO;
import com.hyperplane.rodo.dto.ReportDetailResponseDTO;
import com.hyperplane.rodo.dto.ReportResponseDTO;
import com.hyperplane.rodo.exception.BusinessRuleException;
import com.hyperplane.rodo.exception.ResourceNotFoundException;
import com.hyperplane.rodo.model.Client;
import com.hyperplane.rodo.model.Order;
import com.hyperplane.rodo.model.PaymentOrder;
import com.hyperplane.rodo.model.Report;
import com.hyperplane.rodo.model.User;
import com.hyperplane.rodo.model.enums.OrderStatus;
import com.hyperplane.rodo.model.enums.PaymentOrderStatus;
import com.hyperplane.rodo.model.enums.ReportType;
import com.hyperplane.rodo.repository.ClientRepository;
import com.hyperplane.rodo.repository.MotoboyRepository;
import com.hyperplane.rodo.repository.OrderRepository;
import com.hyperplane.rodo.repository.PaymentOrderRepository;
import com.hyperplane.rodo.repository.ReportRepository;
import com.hyperplane.rodo.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

@Service
@Slf4j
@RequiredArgsConstructor
public class ReportService {

    private static final ZoneId SAO_PAULO_ZONE = ZoneId.of("America/Sao_Paulo");

    private final ReportRepository reportRepository;
    private final OrderRepository orderRepository;
    private final PaymentOrderRepository paymentOrderRepository;
    private final ClientRepository clientRepository;
    private final MotoboyRepository motoboyRepository;
    private final UserRepository userRepository;
    private final OrderService orderService;
    private final SettingsService settingsService;
    private final ExpenseService expenseService;
    private final EmailNotificationService emailNotificationService;

    public Flux<ReportResponseDTO> findAll(ReportType type, String targetEntityId) {
        return ((type != null && targetEntityId != null && !targetEntityId.isBlank())
                ? reportRepository.findByTypeAndTargetEntityIdOrderByCreatedAtDesc(type, targetEntityId)
                : (type != null)
                ? reportRepository.findByTypeOrderByCreatedAtDesc(type)
                : reportRepository.findAll())
                .concatMap(this::enrichReportResponse);
    }

    public Mono<ReportDetailResponseDTO> findReportDetail(String reportId) {
        return reportRepository.findById(reportId)
                .switchIfEmpty(Mono.error(new ResourceNotFoundException("Relatório não encontrado: " + reportId)))
                .flatMap(report -> Mono.zip(
                        enrichReportResponse(report),
                        orderService.findByIds(report.getOrderIds() != null ? report.getOrderIds() : List.of()).collectList()
                ).map(tuple -> new ReportDetailResponseDTO(tuple.getT1(), tuple.getT2())));
    }

    @Transactional
    public Mono<Void> deleteReport(String reportId) {
        return reportRepository.findById(reportId)
                .switchIfEmpty(Mono.error(new ResourceNotFoundException("Relatório não encontrado: " + reportId)))
                .flatMap(report -> report.getType() == ReportType.CLIENTE
                        ? Mono.error(new BusinessRuleException(
                                "Apenas relatórios de Motoboy e o Relatório RDO da Empresa podem ser excluídos."
                        ))
                        : reportRepository.delete(report));
    }

    @Transactional
    public Mono<Long> purgeExpiredReports(Instant referenceNow) {
        return reportRepository.deleteByCreatedAtBefore(
                ZonedDateTime.ofInstant(
                                referenceNow != null ? referenceNow : Instant.now(),
                                SAO_PAULO_ZONE
                        )
                        .minusMonths(6)
                        .toInstant()
        ).defaultIfEmpty(0L);
    }

    @Transactional
    public Mono<ReportResponseDTO> generateCustomReport(CustomReportRequestDTO request, String generatedByUserId) {
        return resolveMatchingOrdersFlux(request)
                .collectList()
                .flatMap(orders -> request.type() == ReportType.EMPRESA
                        ? generateAndDispatchCompanyReport(
                                request.startDate(),
                                request.endDate(),
                                orders,
                                generatedByUserId
                        )
                        : consolidateReportAndOptionalPaymentOrder(
                                request.type(),
                                request.targetEntityId(),
                                request.startDate(),
                                request.endDate(),
                                orders,
                                request.generatePaymentOrder() && request.type() == ReportType.CLIENTE,
                                generatedByUserId
                        ).flatMap(this::enrichReportResponse));
    }

    public Flux<ReportResponseDTO> processDailyBillingForDate(LocalDate referenceDate) {
        return hasDailyJobExecuted(referenceDate)
                .flatMapMany(alreadyExecuted -> Boolean.TRUE.equals(alreadyExecuted)
                        ? Flux.<ReportResponseDTO>empty().doOnSubscribe(sub ->
                                log.info("Fechamento automático das 20:00 ignorado: o job de geração de relatórios já foi executado hoje ({}).", referenceDate))
                        : markDailyJobExecuted(referenceDate).thenMany(processAutomatedBilling(referenceDate, false)));
    }

    public Flux<ReportResponseDTO> triggerManualDailyBilling(LocalDate referenceDate) {
        return hasDailyJobExecuted(referenceDate)
                .flatMapMany(alreadyExecuted -> Boolean.TRUE.equals(alreadyExecuted)
                        ? Flux.error(new BusinessRuleException(
                                "O fechamento e geração automática de relatórios já foi executado hoje. Não é permitido executá-lo novamente no mesmo dia."
                        ))
                        : markDailyJobExecuted(referenceDate).thenMany(processAutomatedBilling(referenceDate, true)));
    }

    public Flux<ReportResponseDTO> processAutomatedBilling(LocalDate referenceDate, boolean forceAllActiveCycles) {
        return Optional.ofNullable(orderRepository.findByCreatedAtBetween(dayStart(referenceDate), dayEnd(referenceDate)))
                .orElse(Flux.empty())
                .filter(order -> order.getStatus() != OrderStatus.CANCELADO)
                .collectList()
                .flatMapMany(dailyOrders -> clientRepository.findByActiveTrue()
                        .filter(client -> forceAllActiveCycles || isClientEligibleForBillingOnDate(client, referenceDate))
                        .concatMap(client -> consolidateClientBillingCycle(client, referenceDate))
                        .collectList()
                        .flatMapMany(clientResults -> Flux.fromIterable(
                                        clientResults.stream().map(Map.Entry::getKey).toList()
                                )
                                .concatWith(generateMotoboyReportsForClosing(
                                        deduplicateOrders(clientResults, dailyOrders),
                                        dayStart(referenceDate),
                                        dayEnd(referenceDate)
                                ))
                                .concatWith(deduplicateOrders(clientResults, dailyOrders).isEmpty()
                                        ? Mono.empty()
                                        : generateAndDispatchCompanyReport(
                                                dayStart(referenceDate),
                                                dayEnd(referenceDate),
                                                deduplicateOrders(clientResults, dailyOrders),
                                                "SYSTEM_JOB"
                                        ))));
    }

    private Flux<Order> resolveMatchingOrdersFlux(CustomReportRequestDTO request) {
        return switch (request.type()) {
            case CLIENTE -> request.generatePaymentOrder()
                    ? orderRepository.findByClientIdAndStatusAndCreatedAtBetween(
                            request.targetEntityId(), OrderStatus.ENTREGUE, request.startDate(), request.endDate())
                    : orderRepository.findByClientIdAndCreatedAtBetween(
                            request.targetEntityId(), request.startDate(), request.endDate())
                            .filter(order -> order.getStatus() != OrderStatus.CANCELADO);
            case MOTOBOY -> orderRepository.findByMotoboyIdAndCreatedAtBetween(
                            request.targetEntityId(), request.startDate(), request.endDate())
                    .filter(order -> order.getStatus() != OrderStatus.CANCELADO);
            case MASTER, EMPRESA -> orderRepository.findByCreatedAtBetween(request.startDate(), request.endDate())
                    .filter(order -> order.getStatus() != OrderStatus.CANCELADO);
        };
    }

    private Mono<Boolean> hasDailyJobExecuted(LocalDate referenceDate) {
        return Optional.ofNullable(settingsService)
                .map(s -> s.hasDailyJobExecutedOnDate(referenceDate))
                .orElse(Mono.just(false))
                .defaultIfEmpty(false);
    }

    private Mono<?> markDailyJobExecuted(LocalDate referenceDate) {
        return Optional.ofNullable(settingsService)
                .map(s -> s.markDailyJobExecutedOnDate(referenceDate))
                .orElse(Mono.empty());
    }

    private Instant dayStart(LocalDate referenceDate) {
        return referenceDate.atStartOfDay(SAO_PAULO_ZONE).toInstant();
    }

    private Instant dayEnd(LocalDate referenceDate) {
        return referenceDate.atTime(LocalTime.of(23, 59, 59)).atZone(SAO_PAULO_ZONE).toInstant();
    }

    private Mono<Map.Entry<ReportResponseDTO, List<Order>>> consolidateClientBillingCycle(
            Client client,
            LocalDate referenceDate
    ) {
        return orderRepository.findByClientIdAndStatusAndCreatedAtBetween(
                        client.getId(),
                        OrderStatus.ENTREGUE,
                        calculateBillingStartDate(client, referenceDate).atStartOfDay(SAO_PAULO_ZONE).toInstant(),
                        dayEnd(referenceDate)
                )
                .collectList()
                .filter(orders -> !orders.isEmpty())
                .flatMap(orders -> consolidateReportAndOptionalPaymentOrder(
                        ReportType.CLIENTE,
                        client.getId(),
                        calculateBillingStartDate(client, referenceDate).atStartOfDay(SAO_PAULO_ZONE).toInstant(),
                        dayEnd(referenceDate),
                        orders,
                        true,
                        "SYSTEM_JOB"
                )
                .flatMap(this::enrichReportResponse)
                .map(reportDto -> Map.entry(reportDto, orders)));
    }

    private List<Order> deduplicateOrders(
            List<Map.Entry<ReportResponseDTO, List<Order>>> clientResults,
            List<Order> dailyOrders
    ) {
        return List.copyOf(
                Stream.concat(
                                clientResults.stream().flatMap(entry -> entry.getValue().stream()),
                                dailyOrders.stream()
                        )
                        .filter(Objects::nonNull)
                        .collect(Collectors.toMap(
                                order -> order.getId() != null ? order.getId() : UUID.randomUUID().toString(),
                                Function.identity(),
                                (existing, duplicate) -> existing,
                                LinkedHashMap::new
                        ))
                        .values()
        );
    }

    private Flux<ReportResponseDTO> generateMotoboyReportsForClosing(
            Collection<Order> uniqueOrders,
            Instant dayStart,
            Instant periodEnd
    ) {
        return Flux.fromIterable(
                        uniqueOrders.stream()
                                .filter(o -> o.getMotoboyId() != null && !o.getMotoboyId().isBlank())
                                .collect(Collectors.groupingBy(
                                        Order::getMotoboyId,
                                        LinkedHashMap::new,
                                        Collectors.toList()
                                ))
                                .entrySet()
                )
                .concatMap(entry -> consolidateReportAndOptionalPaymentOrder(
                        ReportType.MOTOBOY,
                        entry.getKey(),
                        entry.getValue().stream()
                                .map(Order::getCreatedAt)
                                .filter(Objects::nonNull)
                                .min(Instant::compareTo)
                                .orElse(dayStart),
                        periodEnd,
                        entry.getValue(),
                        false,
                        "SYSTEM_JOB"
                ).flatMap(this::enrichReportResponse));
    }

    private Mono<ReportResponseDTO> generateAndDispatchCompanyReport(
            Instant periodStart,
            Instant periodEnd,
            List<Order> orders,
            String generatedByUserId
    ) {
        return buildCompanyReportSummary(orders, periodStart, periodEnd, false, null)
                .flatMap(initialSummary -> reportRepository.save(
                        Report.builder()
                                .reportCode(generateReportCode("RDO"))
                                .type(ReportType.EMPRESA)
                                .targetEntityId("RDO_EXPRESS")
                                .orderIds(extractOrderIds(orders))
                                .periodStart(periodStart)
                                .periodEnd(periodEnd)
                                .totalOrders(orders.size())
                                .totalAmount(initialSummary.totalRevenue())
                                .totalDistanceKm(roundTwoDecimals(sumDistances(orders)))
                                .companySummary(initialSummary)
                                .generatedByUserId(generatedByUserId)
                                .createdAt(Instant.now())
                                .updatedAt(Instant.now())
                                .build()
                ))
                .flatMap(this::enrichReportResponse)
                .flatMap(enrichedDto -> Optional.ofNullable(emailNotificationService)
                        .map(svc -> svc.sendDailyCompanyReportToMaster(enrichedDto))
                        .orElse(Mono.just("admin@rodo.com"))
                        .defaultIfEmpty("admin@rodo.com")
                        .map(recipient -> withCompanyEmailDispatched(enrichedDto, recipient)));
    }

    private ReportResponseDTO withCompanyEmailDispatched(ReportResponseDTO dto, String recipient) {
        return new ReportResponseDTO(
                dto.id(),
                dto.reportCode(),
                dto.type(),
                dto.targetEntityId(),
                dto.targetEntityName(),
                dto.targetEntitySubtype(),
                dto.orderIds(),
                dto.periodStart(),
                dto.periodEnd(),
                dto.totalOrders(),
                dto.totalAmount(),
                dto.totalDistanceKm(),
                dto.paymentOrderId(),
                dto.paymentOrderStatus(),
                dto.generatedByUserId(),
                dto.generatedByUserName(),
                dto.createdAt(),
                dto.updatedAt(),
                new CompanyReportSummaryDTO(
                        dto.companySummary().totalRevenue(),
                        dto.companySummary().paidRevenue(),
                        dto.companySummary().pendingRevenue(),
                        dto.companySummary().netProfit(),
                        dto.companySummary().estimatedProfit(),
                        dto.companySummary().totalCommissions(),
                        dto.companySummary().paidCommissions(),
                        dto.companySummary().totalExpenses(),
                        dto.companySummary().collaboratorShare(),
                        dto.companySummary().totalOrders(),
                        dto.companySummary().paidOrdersCount(),
                        dto.companySummary().pendingOrdersCount(),
                        dto.companySummary().motoboyAggregates(),
                        dto.companySummary().totalMotoboysAmountToPay(),
                        true,
                        recipient
                )
        );
    }

    private Mono<CompanyReportSummaryDTO> buildCompanyReportSummary(
            List<Order> orders,
            Instant periodStart,
            Instant periodEnd,
            boolean emailSent,
            String recipient
    ) {
        return Mono.zip(
                        Optional.ofNullable(expenseService)
                                .map(svc -> svc.sumExpensesInPeriod(periodStart, periodEnd))
                                .orElse(Mono.just(BigDecimal.ZERO))
                                .defaultIfEmpty(BigDecimal.ZERO),
                        buildMotoboyAggregates(orders)
                )
                .map(tuple -> new CompanyReportSummaryDTO(
                        sumOrderMoney(orders, o -> true, Order::getAmount),
                        sumOrderMoney(orders, this::isOrderPaid, Order::getAmount),
                        sumOrderMoney(orders, o -> !isOrderPaid(o), Order::getAmount),
                        sumOrderMoney(orders, this::isOrderPaid, Order::getAmount)
                                .subtract(sumOrderMoney(orders, this::isOrderPaid, Order::getMotoboyCommissionAmount))
                                .subtract(tuple.getT1()),
                        sumOrderMoney(orders, this::isOrderCollectedOrForward, Order::getAmount)
                                .subtract(sumOrderMoney(orders, this::isOrderCollectedOrForward, Order::getMotoboyCommissionAmount))
                                .subtract(tuple.getT1()),
                        sumOrderMoney(orders, o -> true, Order::getMotoboyCommissionAmount),
                        sumOrderMoney(orders, this::isOrderPaid, Order::getMotoboyCommissionAmount),
                        tuple.getT1(),
                        sumOrderMoney(orders, o -> true, Order::getMotoboyCommissionAmount),
                        orders.size(),
                        orders.stream().filter(this::isOrderPaid).count(),
                        orders.stream().filter(o -> !isOrderPaid(o)).count(),
                        tuple.getT2(),
                        tuple.getT2().stream()
                                .map(MotoboyReportAggregateDTO::amountToPay)
                                .filter(Objects::nonNull)
                                .reduce(BigDecimal.ZERO, BigDecimal::add),
                        emailSent,
                        recipient
                ));
    }

    private Mono<List<MotoboyReportAggregateDTO>> buildMotoboyAggregates(List<Order> orders) {
        return Flux.fromIterable(
                        orders.stream()
                                .filter(o -> o.getMotoboyId() != null && !o.getMotoboyId().isBlank())
                                .collect(Collectors.groupingBy(
                                        Order::getMotoboyId,
                                        LinkedHashMap::new,
                                        Collectors.toList()
                                ))
                                .entrySet()
                )
                .concatMap(entry -> Optional.ofNullable(motoboyRepository.findById(entry.getKey()))
                        .orElse(Mono.empty())
                        .map(m -> new String[]{m.getName(), m.getType() != null ? m.getType().name() : "FIXO"})
                        .defaultIfEmpty(new String[]{"Motoboy", "FIXO"})
                        .map(info -> new MotoboyReportAggregateDTO(
                                entry.getKey(),
                                info[0],
                                info[1],
                                entry.getValue().size(),
                                roundTwoDecimals(sumDistances(entry.getValue())),
                                sumOrderMoney(entry.getValue(), o -> true, Order::getAmount),
                                sumOrderMoney(entry.getValue(), o -> true, Order::getMotoboyCommissionAmount)
                        )))
                .collectList();
    }

    private boolean isOrderPaid(Order order) {
        return order != null && order.getStatus() == OrderStatus.PAGO;
    }

    private boolean isOrderCollectedOrForward(Order order) {
        return order != null && order.getStatus() != null && switch (order.getStatus()) {
            case COLETADO, ENTREGUE, AGUARDANDO_PAGAMENTO, PAGO -> true;
            case PENDENTE, CANCELADO -> false;
        };
    }

    private BigDecimal sumOrderMoney(
            List<Order> orders,
            Predicate<Order> filter,
            Function<Order, BigDecimal> extractor
    ) {
        return orders.stream()
                .filter(filter)
                .map(extractor)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private double sumDistances(List<Order> orders) {
        return orders.stream()
                .map(Order::getRouteDistanceKm)
                .filter(Objects::nonNull)
                .mapToDouble(Double::doubleValue)
                .sum();
    }

    private double roundTwoDecimals(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    private List<String> extractOrderIds(List<Order> orders) {
        return orders.stream()
                .map(Order::getId)
                .filter(Objects::nonNull)
                .toList();
    }

    private String generateReportCode(String prefix) {
        return "REL-"
                + YearMonth.now(SAO_PAULO_ZONE).format(DateTimeFormatter.ofPattern("yyyyMM"))
                + "-"
                + prefix
                + UUID.randomUUID().toString().substring(0, 4).toUpperCase();
    }

    public boolean isClientEligibleForBillingOnDate(Client client, LocalDate referenceDate) {
        return client.getBillingCycle() != null
                && client.getBillingCycle().getType() != null
                && client.isActive()
                && switch (client.getBillingCycle().getType()) {
                    case DAILY -> true;
                    case WEEKLY -> client.getBillingCycle().getEndDayOfWeek() != null
                            && referenceDate.getDayOfWeek() == client.getBillingCycle().getEndDayOfWeek().toJavaDayOfWeek();
                    case MONTHLY -> referenceDate.equals(referenceDate.withDayOfMonth(referenceDate.lengthOfMonth()));
                };
    }

    public LocalDate calculateBillingStartDate(Client client, LocalDate referenceDate) {
        return (client.getBillingCycle() == null || client.getBillingCycle().getType() == null)
                ? referenceDate
                : switch (client.getBillingCycle().getType()) {
                    case DAILY -> referenceDate;
                    case WEEKLY -> IntStream.rangeClosed(0, 7)
                            .mapToObj(referenceDate.minusDays(7)::plusDays)
                            .filter(d -> d.getDayOfWeek() == (client.getBillingCycle().getStartDayOfWeek() != null
                                    ? client.getBillingCycle().getStartDayOfWeek().toJavaDayOfWeek()
                                    : referenceDate.minusDays(7).getDayOfWeek()))
                            .findFirst()
                            .orElse(referenceDate.minusDays(7));
                    case MONTHLY -> referenceDate.withDayOfMonth(1);
                };
    }

    private Mono<Report> consolidateReportAndOptionalPaymentOrder(
            ReportType type,
            String targetEntityId,
            Instant periodStart,
            Instant periodEnd,
            List<Order> orders,
            boolean createPaymentOrder,
            String generatedByUserId
    ) {
        return reportRepository.save(
                        Report.builder()
                                .reportCode(generateReportCode(""))
                                .type(type)
                                .targetEntityId(targetEntityId)
                                .orderIds(extractOrderIds(orders))
                                .periodStart(periodStart)
                                .periodEnd(periodEnd)
                                .totalOrders(orders.size())
                                .totalAmount(sumOrderMoney(
                                        orders,
                                        o -> true,
                                        order -> type == ReportType.MOTOBOY ? order.getMotoboyCommissionAmount() : order.getAmount()
                                ))
                                .totalDistanceKm(roundTwoDecimals(sumDistances(orders)))
                                .generatedByUserId(generatedByUserId)
                                .createdAt(Instant.now())
                                .updatedAt(Instant.now())
                                .build()
                )
                .flatMap(savedReport -> (!createPaymentOrder || orders.isEmpty())
                        ? Mono.just(savedReport)
                        : paymentOrderRepository.save(
                                PaymentOrder.builder()
                                        .reportId(savedReport.getId())
                                        .clientId(targetEntityId)
                                        .orderIds(extractOrderIds(orders))
                                        .totalAmount(savedReport.getTotalAmount())
                                        .status(PaymentOrderStatus.AGUARDANDO_PAGAMENTO)
                                        .issueDate(Instant.now())
                                        .createdAt(Instant.now())
                                        .updatedAt(Instant.now())
                                        .build()
                        )
                        .flatMap(savedPo -> Flux.fromIterable(orders)
                                .map(order -> order.toBuilder()
                                        .status(OrderStatus.AGUARDANDO_PAGAMENTO)
                                        .paymentOrderId(savedPo.getId())
                                        .updatedAt(Instant.now())
                                        .build())
                                .flatMap(orderRepository::save)
                                .then(Mono.just(savedReport))));
    }

    private Mono<ReportResponseDTO> enrichReportResponse(Report report) {
        return Mono.zip(
                        resolveTargetEntityInfo(report),
                        resolvePaymentOrder(report),
                        resolveGeneratedByName(report)
                )
                .map(tuple -> new ReportResponseDTO(
                        report.getId(),
                        report.getReportCode(),
                        report.getType(),
                        report.getTargetEntityId(),
                        tuple.getT1()[0],
                        tuple.getT1()[1],
                        report.getOrderIds() != null ? report.getOrderIds() : List.of(),
                        report.getPeriodStart(),
                        report.getPeriodEnd(),
                        report.getTotalOrders(),
                        report.getTotalAmount(),
                        report.getTotalDistanceKm(),
                        tuple.getT2().getId(),
                        tuple.getT2().getStatus(),
                        report.getGeneratedByUserId(),
                        tuple.getT3(),
                        report.getCreatedAt(),
                        tuple.getT2().getUpdatedAt() != null
                                ? tuple.getT2().getUpdatedAt()
                                : (report.getUpdatedAt() != null ? report.getUpdatedAt() : report.getCreatedAt()),
                        report.getCompanySummary()
                ));
    }

    private Mono<String[]> resolveTargetEntityInfo(Report report) {
        return switch (report.getType()) {
            case CLIENTE -> report.getTargetEntityId() != null
                    ? clientRepository.findById(report.getTargetEntityId())
                            .map(c -> new String[]{
                                    c.getName(),
                                    c.getBillingCycle() != null && c.getBillingCycle().getType() != null
                                            ? c.getBillingCycle().getType().name()
                                            : "CLIENTE"
                            })
                            .defaultIfEmpty(new String[]{"Cliente", "CLIENTE"})
                    : Mono.just(new String[]{"Cliente", "CLIENTE"});
            case MOTOBOY -> report.getTargetEntityId() != null
                    ? motoboyRepository.findById(report.getTargetEntityId())
                            .map(m -> new String[]{m.getName(), m.getType() != null ? m.getType().name() : "FIXO"})
                            .defaultIfEmpty(new String[]{"Motoboy", "FIXO"})
                    : Mono.just(new String[]{"Motoboy", "FIXO"});
            case MASTER -> Mono.just(new String[]{"Consolidado Geral", "MASTER"});
            case EMPRESA -> Mono.just(new String[]{"RDO Express (Resumo Geral)", "EMPRESA"});
        };
    }

    private Mono<PaymentOrder> resolvePaymentOrder(Report report) {
        return report.getType() == ReportType.CLIENTE && report.getId() != null
                ? paymentOrderRepository.findByReportId(report.getId()).defaultIfEmpty(PaymentOrder.builder().build())
                : Mono.just(PaymentOrder.builder().build());
    }

    private Mono<String> resolveGeneratedByName(Report report) {
        return (report.getGeneratedByUserId() != null && !report.getGeneratedByUserId().equals("SYSTEM_JOB"))
                ? userRepository.findById(report.getGeneratedByUserId()).map(User::getName).defaultIfEmpty("Administrador")
                : Mono.just("Robô Automático");
    }
}
