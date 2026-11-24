package com.hyperplane.rodo.service;

import com.hyperplane.rodo.dto.CompanyReportSummaryDTO;
import com.hyperplane.rodo.dto.CustomReportRequestDTO;
import com.hyperplane.rodo.dto.MotoboyReportAggregateDTO;
import com.hyperplane.rodo.dto.OrderResponseDTO;
import com.hyperplane.rodo.dto.ReportDetailResponseDTO;
import com.hyperplane.rodo.dto.ReportResponseDTO;
import com.hyperplane.rodo.exception.BusinessRuleException;
import com.hyperplane.rodo.exception.ResourceNotFoundException;
import com.hyperplane.rodo.model.BillingCycle;
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
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
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
        final Flux<Report> source = (type != null && targetEntityId != null && !targetEntityId.isBlank())
                ? reportRepository.findByTypeAndTargetEntityIdOrderByCreatedAtDesc(type, targetEntityId)
                : (type != null)
                ? reportRepository.findByTypeOrderByCreatedAtDesc(type)
                : reportRepository.findAll();

        return source.concatMap(this::enrichReportResponse);
    }

    public Mono<ReportDetailResponseDTO> findReportDetail(String reportId) {
        return reportRepository.findById(reportId)
                .switchIfEmpty(Mono.error(new ResourceNotFoundException("Relatório não encontrado: " + reportId)))
                .flatMap(report -> {
                    final List<String> orderIds = report.getOrderIds() != null ? report.getOrderIds() : List.of();
                    return Mono.zip(
                            enrichReportResponse(report),
                            orderService.findByIds(orderIds).collectList()
                    ).map(tuple -> new ReportDetailResponseDTO(tuple.getT1(), tuple.getT2()));
                });
    }

    @Transactional
    public Mono<Void> deleteReport(String reportId) {
        return reportRepository.findById(reportId)
                .switchIfEmpty(Mono.error(new ResourceNotFoundException("Relatório não encontrado: " + reportId)))
                .flatMap(report -> {
                    if (report.getType() == ReportType.CLIENTE) {
                        return Mono.error(new BusinessRuleException(
                                "Apenas relatórios de Motoboy e o Relatório RDO da Empresa podem ser excluídos."
                        ));
                    }
                    return reportRepository.delete(report);
                });
    }

    @Transactional
    public Mono<ReportResponseDTO> generateCustomReport(CustomReportRequestDTO request, String generatedByUserId) {
        final Flux<Order> matchingOrdersFlux = switch (request.type()) {
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

        return matchingOrdersFlux.collectList()
                .flatMap(orders -> {
                    if (request.type() == ReportType.EMPRESA) {
                        return generateAndDispatchCompanyReport(
                                request.startDate(),
                                request.endDate(),
                                orders,
                                generatedByUserId
                        );
                    }
                    return consolidateReportAndOptionalPaymentOrder(
                            request.type(),
                            request.targetEntityId(),
                            request.startDate(),
                            request.endDate(),
                            orders,
                            request.generatePaymentOrder() && request.type() == ReportType.CLIENTE,
                            generatedByUserId
                    ).flatMap(this::enrichReportResponse);
                });
    }

    public Flux<ReportResponseDTO> processDailyBillingForDate(LocalDate referenceDate) {
        final Mono<Boolean> alreadyExecutedMono = Optional.ofNullable(settingsService)
                .map(s -> s.hasDailyJobExecutedOnDate(referenceDate))
                .orElse(Mono.just(false))
                .defaultIfEmpty(false);

        return alreadyExecutedMono.flatMapMany(alreadyExecuted -> {
            if (Boolean.TRUE.equals(alreadyExecuted)) {
                log.info("Fechamento automático das 20:00 ignorado: o job de geração de relatórios já foi executado hoje ({}).", referenceDate);
                return Flux.empty();
            }
            final Mono<?> markMono = Optional.ofNullable(settingsService)
                    .map(s -> s.markDailyJobExecutedOnDate(referenceDate))
                    .orElse(Mono.empty());
            return markMono.thenMany(processAutomatedBilling(referenceDate, false));
        });
    }

    public Flux<ReportResponseDTO> triggerManualDailyBilling(LocalDate referenceDate) {
        final Mono<Boolean> alreadyExecutedMono = Optional.ofNullable(settingsService)
                .map(s -> s.hasDailyJobExecutedOnDate(referenceDate))
                .orElse(Mono.just(false))
                .defaultIfEmpty(false);

        return alreadyExecutedMono.flatMapMany(alreadyExecuted -> {
            if (Boolean.TRUE.equals(alreadyExecuted)) {
                return Flux.error(new BusinessRuleException(
                        "O fechamento e geração automática de relatórios já foi executado hoje. Não é permitido executá-lo novamente no mesmo dia."
                ));
            }
            final Mono<?> markMono = Optional.ofNullable(settingsService)
                    .map(s -> s.markDailyJobExecutedOnDate(referenceDate))
                    .orElse(Mono.empty());
            return markMono.thenMany(processAutomatedBilling(referenceDate, true));
        });
    }

    public Flux<ReportResponseDTO> processAutomatedBilling(LocalDate referenceDate, boolean forceAllActiveCycles) {
        final Instant dayStart = referenceDate.atStartOfDay(SAO_PAULO_ZONE).toInstant();
        final Instant periodEnd = referenceDate.atTime(LocalTime.of(23, 59, 59)).atZone(SAO_PAULO_ZONE).toInstant();

        // Capture today's non-cancelled orders BEFORE client billing transitions ENTREGUE -> AGUARDANDO_PAGAMENTO,
        // ensuring motoboys receive their daily 20:00 report even on days when only WEEKLY/MONTHLY clients had deliveries.
        final Mono<List<Order>> dailyMotoboyOrdersMono = Optional.ofNullable(
                        orderRepository.findByCreatedAtBetween(dayStart, periodEnd)
                )
                .orElse(Flux.empty())
                .filter(order -> order.getStatus() != OrderStatus.CANCELADO)
                .collectList();

        return dailyMotoboyOrdersMono.flatMapMany(dailyOrders ->
                clientRepository.findByActiveTrue()
                        .filter(client -> forceAllActiveCycles || isClientEligibleForBillingOnDate(client, referenceDate))
                        .concatMap(client -> {
                            final LocalDate startDate = calculateBillingStartDate(client, referenceDate);
                            final Instant periodStart = startDate.atStartOfDay(SAO_PAULO_ZONE).toInstant();

                            return orderRepository.findByClientIdAndStatusAndCreatedAtBetween(
                                            client.getId(), OrderStatus.ENTREGUE, periodStart, periodEnd
                                    )
                                    .collectList()
                                    .filter(orders -> !orders.isEmpty())
                                    .flatMap(orders -> consolidateReportAndOptionalPaymentOrder(
                                            ReportType.CLIENTE,
                                            client.getId(),
                                            periodStart,
                                            periodEnd,
                                            orders,
                                            true,
                                            "SYSTEM_JOB"
                                    )
                                    .flatMap(this::enrichReportResponse)
                                    .map(reportDto -> Map.entry(reportDto, orders)));
                        })
                        .collectList()
                        .flatMapMany(clientResults -> {
                            final List<ReportResponseDTO> clientReports = clientResults.stream()
                                    .map(Map.Entry::getKey)
                                    .toList();

                            final List<Order> allBilledOrders = clientResults.stream()
                                    .flatMap(entry -> entry.getValue().stream())
                                    .toList();

                            // Merge billed orders + daily orders deduplicated by order ID so daily motoboy closing covers all deliveries
                            final Map<String, Order> uniqueOrdersById = new LinkedHashMap<>();
                            Stream.concat(allBilledOrders.stream(), dailyOrders.stream())
                                    .filter(Objects::nonNull)
                                    .forEach(order -> {
                                        final String key = order.getId() != null ? order.getId() : UUID.randomUUID().toString();
                                        uniqueOrdersById.putIfAbsent(key, order);
                                    });

                            final Map<String, List<Order>> ordersByMotoboy = uniqueOrdersById.values().stream()
                                    .filter(o -> o.getMotoboyId() != null && !o.getMotoboyId().isBlank())
                                    .collect(Collectors.groupingBy(
                                            Order::getMotoboyId,
                                            LinkedHashMap::new,
                                            Collectors.toList()
                                    ));

                            final Flux<ReportResponseDTO> motoboyReportsFlux = Flux.fromIterable(ordersByMotoboy.entrySet())
                                    .concatMap(entry -> {
                                        final String motoboyId = entry.getKey();
                                        final List<Order> motoboyOrders = entry.getValue();
                                        final Instant minOrderDate = motoboyOrders.stream()
                                                .map(Order::getCreatedAt)
                                                .filter(Objects::nonNull)
                                                .min(Instant::compareTo)
                                                .orElse(dayStart);

                                        return consolidateReportAndOptionalPaymentOrder(
                                                ReportType.MOTOBOY,
                                                motoboyId,
                                                minOrderDate,
                                                periodEnd,
                                                motoboyOrders,
                                                false,
                                                "SYSTEM_JOB"
                                        ).flatMap(this::enrichReportResponse);
                                    });

                            final List<Order> companyOrders = List.copyOf(uniqueOrdersById.values());
                            final Mono<ReportResponseDTO> companyReportMono = companyOrders.isEmpty()
                                    ? Mono.empty()
                                    : generateAndDispatchCompanyReport(dayStart, periodEnd, companyOrders, "SYSTEM_JOB");

                            return Flux.fromIterable(clientReports)
                                    .concatWith(motoboyReportsFlux)
                                    .concatWith(companyReportMono);
                        })
        );
    }

    private Mono<ReportResponseDTO> generateAndDispatchCompanyReport(
            Instant periodStart,
            Instant periodEnd,
            List<Order> orders,
            String generatedByUserId
    ) {
        return buildCompanyReportSummary(orders, periodStart, periodEnd, false, null)
                .flatMap(initialSummary -> {
                    final Instant now = Instant.now();
                    final List<String> orderIds = orders.stream()
                            .map(Order::getId)
                            .filter(Objects::nonNull)
                            .toList();
                    final double totalDistanceKm = orders.stream()
                            .map(Order::getRouteDistanceKm)
                            .filter(Objects::nonNull)
                            .mapToDouble(Double::doubleValue)
                            .sum();
                    final String codeSuffix = UUID.randomUUID().toString().substring(0, 4).toUpperCase();
                    final String yearMonth = YearMonth.now(SAO_PAULO_ZONE).format(DateTimeFormatter.ofPattern("yyyyMM"));
                    final String reportCode = "REL-" + yearMonth + "-RDO" + codeSuffix;

                    final Report initialReport = Report.builder()
                            .reportCode(reportCode)
                            .type(ReportType.EMPRESA)
                            .targetEntityId("RDO_EXPRESS")
                            .orderIds(orderIds)
                            .periodStart(periodStart)
                            .periodEnd(periodEnd)
                            .totalOrders(orders.size())
                            .totalAmount(initialSummary.totalRevenue())
                            .totalDistanceKm(Math.round(totalDistanceKm * 100.0) / 100.0)
                            .companySummary(initialSummary)
                            .generatedByUserId(generatedByUserId)
                            .createdAt(now)
                            .updatedAt(now)
                            .build();

                    return reportRepository.save(initialReport)
                            .flatMap(this::enrichReportResponse)
                            .flatMap(enrichedDto -> {
                                final Mono<String> emailMono = Optional.ofNullable(emailNotificationService)
                                        .map(svc -> svc.sendDailyCompanyReportToMaster(enrichedDto))
                                        .orElse(Mono.just("admin@rodo.com"))
                                        .defaultIfEmpty("admin@rodo.com");

                                return emailMono.flatMap(recipient -> {
                                    final CompanyReportSummaryDTO s = enrichedDto.companySummary();
                                    final CompanyReportSummaryDTO summaryWithEmail = new CompanyReportSummaryDTO(
                                            s.totalRevenue(),
                                            s.paidRevenue(),
                                            s.pendingRevenue(),
                                            s.netProfit(),
                                            s.totalCommissions(),
                                            s.paidCommissions(),
                                            s.totalExpenses(),
                                            s.collaboratorShare(),
                                            s.totalOrders(),
                                            s.paidOrdersCount(),
                                            s.pendingOrdersCount(),
                                            s.motoboyAggregates(),
                                            s.totalMotoboysAmountToPay(),
                                            true,
                                            recipient
                                    );
                                    return Mono.just(new ReportResponseDTO(
                                            enrichedDto.id(),
                                            enrichedDto.reportCode(),
                                            enrichedDto.type(),
                                            enrichedDto.targetEntityId(),
                                            enrichedDto.targetEntityName(),
                                            enrichedDto.targetEntitySubtype(),
                                            enrichedDto.orderIds(),
                                            enrichedDto.periodStart(),
                                            enrichedDto.periodEnd(),
                                            enrichedDto.totalOrders(),
                                            enrichedDto.totalAmount(),
                                            enrichedDto.totalDistanceKm(),
                                            enrichedDto.paymentOrderId(),
                                            enrichedDto.paymentOrderStatus(),
                                            enrichedDto.generatedByUserId(),
                                            enrichedDto.generatedByUserName(),
                                            enrichedDto.createdAt(),
                                            enrichedDto.updatedAt(),
                                            summaryWithEmail
                                    ));
                                });
                            });
                });
    }

    private Mono<CompanyReportSummaryDTO> buildCompanyReportSummary(
            List<Order> orders,
            Instant periodStart,
            Instant periodEnd,
            boolean emailSent,
            String recipient
    ) {
        final Mono<BigDecimal> expensesMono = Optional.ofNullable(expenseService)
                .map(svc -> svc.sumExpensesInPeriod(periodStart, periodEnd))
                .orElse(Mono.just(BigDecimal.ZERO))
                .defaultIfEmpty(BigDecimal.ZERO);

        final Map<String, List<Order>> groupedByMotoboy = orders.stream()
                .filter(o -> o.getMotoboyId() != null && !o.getMotoboyId().isBlank())
                .collect(Collectors.groupingBy(
                        Order::getMotoboyId,
                        LinkedHashMap::new,
                        Collectors.toList()
                ));

        final Mono<List<MotoboyReportAggregateDTO>> aggregatesMono = Flux.fromIterable(groupedByMotoboy.entrySet())
                .concatMap(entry -> {
                    final String motoboyId = entry.getKey();
                    final List<Order> mOrders = entry.getValue();
                    final BigDecimal grossAmount = mOrders.stream()
                            .map(Order::getAmount)
                            .filter(Objects::nonNull)
                            .reduce(BigDecimal.ZERO, BigDecimal::add);
                    final BigDecimal amountToPay = mOrders.stream()
                            .map(Order::getMotoboyCommissionAmount)
                            .filter(Objects::nonNull)
                            .reduce(BigDecimal.ZERO, BigDecimal::add);
                    final double distKm = Math.round(mOrders.stream()
                            .map(Order::getRouteDistanceKm)
                            .filter(Objects::nonNull)
                            .mapToDouble(Double::doubleValue)
                            .sum() * 100.0) / 100.0;

                    return Optional.ofNullable(motoboyRepository.findById(motoboyId))
                            .orElse(Mono.empty())
                            .map(m -> new String[]{m.getName(), m.getType() != null ? m.getType().name() : "FIXO"})
                            .defaultIfEmpty(new String[]{"Motoboy", "FIXO"})
                            .map(info -> new MotoboyReportAggregateDTO(
                                    motoboyId,
                                    info[0],
                                    info[1],
                                    mOrders.size(),
                                    distKm,
                                    grossAmount,
                                    amountToPay
                            ));
                })
                .collectList();

        return Mono.zip(expensesMono, aggregatesMono)
                .map(tuple -> {
                    final BigDecimal totalExpenses = tuple.getT1();
                    final List<MotoboyReportAggregateDTO> aggregates = tuple.getT2();

                    final List<Order> paidOrders = orders.stream()
                            .filter(o -> o.getStatus() == OrderStatus.PAGO)
                            .toList();
                    final List<Order> pendingOrders = orders.stream()
                            .filter(o -> o.getStatus() != OrderStatus.PAGO)
                            .toList();

                    final BigDecimal totalRevenue = orders.stream()
                            .map(Order::getAmount)
                            .filter(Objects::nonNull)
                            .reduce(BigDecimal.ZERO, BigDecimal::add);
                    final BigDecimal paidRevenue = paidOrders.stream()
                            .map(Order::getAmount)
                            .filter(Objects::nonNull)
                            .reduce(BigDecimal.ZERO, BigDecimal::add);
                    final BigDecimal pendingRevenue = pendingOrders.stream()
                            .map(Order::getAmount)
                            .filter(Objects::nonNull)
                            .reduce(BigDecimal.ZERO, BigDecimal::add);

                    final BigDecimal totalCommissions = orders.stream()
                            .map(Order::getMotoboyCommissionAmount)
                            .filter(Objects::nonNull)
                            .reduce(BigDecimal.ZERO, BigDecimal::add);
                    final BigDecimal paidCommissions = paidOrders.stream()
                            .map(Order::getMotoboyCommissionAmount)
                            .filter(Objects::nonNull)
                            .reduce(BigDecimal.ZERO, BigDecimal::add);

                    // Lucro calculado somente com os pedidos pagos
                    final BigDecimal netProfit = paidRevenue
                            .subtract(paidCommissions)
                            .subtract(totalExpenses);

                    final BigDecimal totalMotoboysAmountToPay = aggregates.stream()
                            .map(MotoboyReportAggregateDTO::amountToPay)
                            .filter(Objects::nonNull)
                            .reduce(BigDecimal.ZERO, BigDecimal::add);

                    return new CompanyReportSummaryDTO(
                            totalRevenue,
                            paidRevenue,
                            pendingRevenue,
                            netProfit,
                            totalCommissions,
                            paidCommissions,
                            totalExpenses,
                            totalCommissions,
                            orders.size(),
                            paidOrders.size(),
                            pendingOrders.size(),
                            aggregates,
                            totalMotoboysAmountToPay,
                            emailSent,
                            recipient
                    );
                });
    }

    public boolean isClientEligibleForBillingOnDate(Client client, LocalDate referenceDate) {
        final BillingCycle cycle = client.getBillingCycle();
        if (cycle == null || cycle.getType() == null || !client.isActive()) {
            return false;
        }
        return switch (cycle.getType()) {
            case DAILY -> true;
            case WEEKLY -> cycle.getEndDayOfWeek() != null
                    && referenceDate.getDayOfWeek() == cycle.getEndDayOfWeek().toJavaDayOfWeek();
            case MONTHLY -> referenceDate.equals(referenceDate.withDayOfMonth(referenceDate.lengthOfMonth()));
        };
    }

    public LocalDate calculateBillingStartDate(Client client, LocalDate referenceDate) {
        final BillingCycle cycle = client.getBillingCycle();
        if (cycle == null || cycle.getType() == null) {
            return referenceDate;
        }
        return switch (cycle.getType()) {
            case DAILY -> referenceDate;
            case WEEKLY -> {
                final LocalDate minusSeven = referenceDate.minusDays(7);
                final DayOfWeek targetStartDay = cycle.getStartDayOfWeek() != null
                        ? cycle.getStartDayOfWeek().toJavaDayOfWeek()
                        : minusSeven.getDayOfWeek();
                yield IntStream.rangeClosed(0, 7)
                        .mapToObj(minusSeven::plusDays)
                        .filter(d -> d.getDayOfWeek() == targetStartDay)
                        .findFirst()
                        .orElse(minusSeven);
            }
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
        final Instant now = Instant.now();
        final List<String> orderIds = orders.stream()
                .map(Order::getId)
                .filter(Objects::nonNull)
                .toList();

        final BigDecimal totalAmount = orders.stream()
                .map(order -> type == ReportType.MOTOBOY ? order.getMotoboyCommissionAmount() : order.getAmount())
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        final double totalDistanceKm = orders.stream()
                .map(Order::getRouteDistanceKm)
                .filter(Objects::nonNull)
                .mapToDouble(Double::doubleValue)
                .sum();

        final String codeSuffix = UUID.randomUUID().toString().substring(0, 4).toUpperCase();
        final String yearMonth = YearMonth.now(SAO_PAULO_ZONE).format(DateTimeFormatter.ofPattern("yyyyMM"));
        final String reportCode = "REL-" + yearMonth + "-" + codeSuffix;

        final Report reportToSave = Report.builder()
                .reportCode(reportCode)
                .type(type)
                .targetEntityId(targetEntityId)
                .orderIds(orderIds)
                .periodStart(periodStart)
                .periodEnd(periodEnd)
                .totalOrders(orders.size())
                .totalAmount(totalAmount)
                .totalDistanceKm(Math.round(totalDistanceKm * 100.0) / 100.0)
                .generatedByUserId(generatedByUserId)
                .createdAt(now)
                .updatedAt(now)
                .build();

        return reportRepository.save(reportToSave)
                .flatMap(savedReport -> {
                    if (!createPaymentOrder || orders.isEmpty()) {
                        return Mono.just(savedReport);
                    }
                    final PaymentOrder paymentOrder = PaymentOrder.builder()
                            .reportId(savedReport.getId())
                            .clientId(targetEntityId)
                            .orderIds(orderIds)
                            .totalAmount(totalAmount)
                            .status(PaymentOrderStatus.AGUARDANDO_PAGAMENTO)
                            .issueDate(now)
                            .createdAt(now)
                            .updatedAt(now)
                            .build();

                    return paymentOrderRepository.save(paymentOrder)
                            .flatMap(savedPo -> Flux.fromIterable(orders)
                                    .map(order -> order.toBuilder()
                                            .status(OrderStatus.AGUARDANDO_PAGAMENTO)
                                            .paymentOrderId(savedPo.getId())
                                            .updatedAt(now)
                                            .build())
                                    .flatMap(orderRepository::save)
                                    .then(Mono.just(savedReport)));
                });
    }

    private Mono<ReportResponseDTO> enrichReportResponse(Report report) {
        final Mono<String[]> entityInfoMono = switch (report.getType()) {
            case CLIENTE -> report.getTargetEntityId() != null
                    ? clientRepository.findById(report.getTargetEntityId())
                            .map(c -> new String[]{c.getName(), c.getBillingCycle() != null && c.getBillingCycle().getType() != null ? c.getBillingCycle().getType().name() : "CLIENTE"})
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

        final Mono<PaymentOrder> paymentOrderMono = report.getType() == ReportType.CLIENTE && report.getId() != null
                ? paymentOrderRepository.findByReportId(report.getId()).defaultIfEmpty(PaymentOrder.builder().build())
                : Mono.just(PaymentOrder.builder().build());

        final Mono<String> generatedByNameMono = (report.getGeneratedByUserId() != null && !report.getGeneratedByUserId().equals("SYSTEM_JOB"))
                ? userRepository.findById(report.getGeneratedByUserId()).map(User::getName).defaultIfEmpty("Administrador")
                : Mono.just("Robô Automático");

        return Mono.zip(entityInfoMono, paymentOrderMono, generatedByNameMono)
                .map(tuple -> {
                    final String[] entityInfo = tuple.getT1();
                    final PaymentOrder po = tuple.getT2();
                    final String generatedByName = tuple.getT3();
                    final List<String> orderIds = report.getOrderIds() != null ? report.getOrderIds() : List.of();
                    final Instant effectiveUpdatedAt = po.getUpdatedAt() != null
                            ? po.getUpdatedAt()
                            : (report.getUpdatedAt() != null ? report.getUpdatedAt() : report.getCreatedAt());
                    return new ReportResponseDTO(
                            report.getId(),
                            report.getReportCode(),
                            report.getType(),
                            report.getTargetEntityId(),
                            entityInfo[0],
                            entityInfo[1],
                            orderIds,
                            report.getPeriodStart(),
                            report.getPeriodEnd(),
                            report.getTotalOrders(),
                            report.getTotalAmount(),
                            report.getTotalDistanceKm(),
                            po.getId(),
                            po.getStatus(),
                            report.getGeneratedByUserId(),
                            generatedByName,
                            report.getCreatedAt(),
                            effectiveUpdatedAt,
                            report.getCompanySummary()
                    );
                });
    }
}
