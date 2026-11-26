package com.hyperplane.rodo.service;

import com.hyperplane.rodo.dto.CustomReportRequestDTO;
import com.hyperplane.rodo.dto.OrderResponseDTO;
import com.hyperplane.rodo.dto.ReportDetailResponseDTO;
import com.hyperplane.rodo.dto.ReportResponseDTO;
import com.hyperplane.rodo.model.BillingCycle;
import com.hyperplane.rodo.model.Client;
import com.hyperplane.rodo.model.Motoboy;
import com.hyperplane.rodo.model.Order;
import com.hyperplane.rodo.model.PaymentOrder;
import com.hyperplane.rodo.model.Report;
import com.hyperplane.rodo.model.User;
import com.hyperplane.rodo.model.enums.BillingCycleType;
import com.hyperplane.rodo.model.enums.DayOfWeekEnum;
import com.hyperplane.rodo.model.enums.MotoboyType;
import com.hyperplane.rodo.model.enums.OrderStatus;
import com.hyperplane.rodo.model.enums.PaymentOrderStatus;
import com.hyperplane.rodo.model.enums.ReportType;
import com.hyperplane.rodo.repository.ClientRepository;
import com.hyperplane.rodo.repository.MotoboyRepository;
import com.hyperplane.rodo.repository.OrderRepository;
import com.hyperplane.rodo.repository.PaymentOrderRepository;
import com.hyperplane.rodo.repository.ReportRepository;
import com.hyperplane.rodo.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReportServiceTest {

    @Mock
    private ReportRepository reportRepository;

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private PaymentOrderRepository paymentOrderRepository;

    @Mock
    private ClientRepository clientRepository;

    @Mock
    private MotoboyRepository motoboyRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private OrderService orderService;

    @Mock
    private SettingsService settingsService;

    @Mock
    private ExpenseService expenseService;

    @Mock
    private EmailNotificationService emailNotificationService;

    @InjectMocks
    private ReportService reportService;

    @Test
    @DisplayName("Should generate custom CLIENTE report storing orderIds and PaymentOrder and update ENTREGUE orders to AGUARDANDO_PAGAMENTO")
    void shouldGenerateCustomClientReportWithPaymentOrderAndUpdateOrdersStatus() {
        // Arrange
        final Instant start = Instant.parse("2026-10-01T00:00:00Z");
        final Instant end = Instant.parse("2026-10-23T23:59:59Z");
        final CustomReportRequestDTO request = new CustomReportRequestDTO(
                ReportType.CLIENTE, "client-1", start, end, true
        );

        final Order ord1 = Order.builder()
                .id("ord-1")
                .clientId("client-1")
                .routeDistanceKm(10.0)
                .amount(new BigDecimal("45.00"))
                .motoboyCommissionAmount(new BigDecimal("36.00"))
                .status(OrderStatus.ENTREGUE)
                .createdAt(Instant.parse("2026-10-10T12:00:00Z"))
                .build();

        when(orderRepository.findByClientIdAndStatusAndCreatedAtBetween("client-1", OrderStatus.ENTREGUE, start, end))
                .thenReturn(Flux.just(ord1));
        when(reportRepository.save(any(Report.class))).thenAnswer(inv -> {
            final Report r = inv.getArgument(0);
            return Mono.just(r.toBuilder().id("rep-1").build());
        });
        when(paymentOrderRepository.save(any(PaymentOrder.class))).thenAnswer(inv -> {
            final PaymentOrder po = inv.getArgument(0);
            return Mono.just(po.toBuilder().id("po-1").build());
        });
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> Mono.just(inv.getArgument(0)));
        when(clientRepository.findById("client-1")).thenReturn(Mono.just(Client.builder().id("client-1").name("INCLOSET").build()));
        when(userRepository.findById("user-master")).thenReturn(Mono.just(User.builder().id("user-master").name("Renata").build()));
        when(paymentOrderRepository.findByReportId("rep-1")).thenReturn(Mono.just(
                PaymentOrder.builder().id("po-1").reportId("rep-1").status(PaymentOrderStatus.AGUARDANDO_PAGAMENTO).build()
        ));

        // Act
        final Mono<ReportResponseDTO> result = reportService.generateCustomReport(request, "user-master");

        // Assert
        StepVerifier.create(result)
                .expectNextMatches(dto ->
                        dto.id().equals("rep-1")
                                && dto.type() == ReportType.CLIENTE
                                && dto.orderIds().equals(List.of("ord-1"))
                                && dto.totalOrders() == 1
                                && dto.totalAmount().compareTo(new BigDecimal("45.00")) == 0
                                && dto.paymentOrderStatus() == PaymentOrderStatus.AGUARDANDO_PAGAMENTO
                                && dto.targetEntityName().equals("INCLOSET")
                                && dto.generatedByUserName().equals("Renata")
                )
                .verifyComplete();
    }

    @Test
    @DisplayName("Should generate custom MOTOBOY report storing orderIds and summing motoboy commissions")
    void shouldGenerateCustomMotoboyReportUsingCommissionSumWithoutPaymentOrder() {
        // Arrange
        final Instant start = Instant.parse("2026-10-01T00:00:00Z");
        final Instant end = Instant.parse("2026-10-23T23:59:59Z");
        final CustomReportRequestDTO request = new CustomReportRequestDTO(
                ReportType.MOTOBOY, "moto-1", start, end, false
        );

        final Order ord1 = Order.builder()
                .id("ord-1")
                .motoboyId("moto-1")
                .routeDistanceKm(8.5)
                .amount(new BigDecimal("50.00"))
                .motoboyCommissionAmount(new BigDecimal("40.00"))
                .status(OrderStatus.ENTREGUE)
                .createdAt(Instant.parse("2026-10-12T14:00:00Z"))
                .build();

        when(orderRepository.findByMotoboyIdAndCreatedAtBetween("moto-1", start, end))
                .thenReturn(Flux.just(ord1));
        when(reportRepository.save(any(Report.class))).thenAnswer(inv -> {
            final Report r = inv.getArgument(0);
            return Mono.just(r.toBuilder().id("rep-moto-1").build());
        });
        when(motoboyRepository.findById("moto-1")).thenReturn(
                Mono.just(Motoboy.builder().id("moto-1").name("João Motoboy").type(MotoboyType.FIXO).build())
        );
        when(userRepository.findById("user-master")).thenReturn(Mono.empty());

        // Act
        final Mono<ReportResponseDTO> result = reportService.generateCustomReport(request, "user-master");

        // Assert
        StepVerifier.create(result)
                .expectNextMatches(dto ->
                        dto.id().equals("rep-moto-1")
                                && dto.type() == ReportType.MOTOBOY
                                && dto.orderIds().equals(List.of("ord-1"))
                                && dto.totalAmount().compareTo(new BigDecimal("40.00")) == 0
                                && dto.targetEntityName().equals("João Motoboy")
                                && dto.targetEntitySubtype().equals("FIXO")
                )
                .verifyComplete();
    }

    @Test
    @DisplayName("Should load report details by querying OrderService with report.orderIds list directly")
    void shouldLoadReportDetailUsingOrderIdsList() {
        // Arrange
        final List<String> orderIds = List.of("ord-10", "ord-11");
        final Report report = Report.builder()
                .id("rep-100")
                .reportCode("REL-202610-0100")
                .type(ReportType.CLIENTE)
                .targetEntityId("client-1")
                .orderIds(orderIds)
                .totalOrders(2)
                .totalAmount(new BigDecimal("54.00"))
                .generatedByUserId("user-1")
                .build();

        final OrderResponseDTO dto1 = new OrderResponseDTO(
                "ord-10", 1010L, "client-1", "INCLOSET", "moto-1", "Carlos", "A. Junqueira",
                "user-1", "Renata", null, null, 5.0, new BigDecimal("36.00"), new BigDecimal("28.80"),
                OrderStatus.AGUARDANDO_PAGAMENTO, "po-1", Instant.now(), null, null, null, Instant.now()
        );
        final OrderResponseDTO dto2 = new OrderResponseDTO(
                "ord-11", 1011L, "client-1", "INCLOSET", "moto-1", "Carlos", "MARIANA",
                "user-1", "Renata", null, null, 4.0, new BigDecimal("18.00"), new BigDecimal("14.40"),
                OrderStatus.AGUARDANDO_PAGAMENTO, "po-1", Instant.now(), null, null, null, Instant.now()
        );

        when(reportRepository.findById("rep-100")).thenReturn(Mono.just(report));
        when(clientRepository.findById("client-1")).thenReturn(Mono.just(Client.builder().id("client-1").name("INCLOSET").build()));
        when(userRepository.findById("user-1")).thenReturn(Mono.just(User.builder().id("user-1").name("Renata").build()));
        when(paymentOrderRepository.findByReportId("rep-100")).thenReturn(Mono.empty());
        when(orderService.findByIds(orderIds)).thenReturn(Flux.just(dto1, dto2));

        // Act
        final Mono<ReportDetailResponseDTO> result = reportService.findReportDetail("rep-100");

        // Assert
        StepVerifier.create(result)
                .expectNextMatches(detail ->
                        detail.report().orderIds().equals(orderIds)
                                && detail.orders().size() == 2
                                && detail.orders().get(0).requesterName().equals("A. Junqueira")
                )
                .verifyComplete();

        verify(orderService).findByIds(orderIds);
    }

    @Test
    @DisplayName("Should verify weekly billing eligibility and calculate start date accurately")
    void shouldCalculateWeeklyBillingWindowCorrectly() {
        // Arrange: Friday 2026-10-23
        final LocalDate friday = LocalDate.of(2026, 10, 23);
        final Client weeklyClient = Client.builder()
                .id("client-weekly")
                .active(true)
                .billingCycle(BillingCycle.builder()
                        .type(BillingCycleType.WEEKLY)
                        .startDayOfWeek(DayOfWeekEnum.SEGUNDA)
                        .endDayOfWeek(DayOfWeekEnum.SEXTA)
                        .build())
                .build();

        // Act & Assert
        assertTrue(reportService.isClientEligibleForBillingOnDate(weeklyClient, friday));
        final LocalDate startDate = reportService.calculateBillingStartDate(weeklyClient, friday);
        assertEquals(LocalDate.of(2026, 10, 19), startDate);
    }

    @Test
    @DisplayName("Should simulate 10 clients (3 DAILY, 4 WEEKLY, 3 MONTHLY) with 12 orders each (5 for motoboy 1, 7 for motoboy 2 across different localities) and emit reports for all 10 clients and 2 motoboys")
    void shouldEmitAutomatedReportsForTenClientsAcrossThreeCyclesAndTwoParticipatingMotoboys() {
        // Arrange: Reference date 2026-07-31 (Friday AND last day of July, so DAILY, WEEKLY [SEXTA], and MONTHLY are all eligible)
        final LocalDate referenceDate = LocalDate.of(2026, 7, 31);

        final List<Client> tenClients = java.util.stream.IntStream.rangeClosed(1, 10)
                .mapToObj(i -> {
                    final BillingCycle cycle;
                    if (i <= 3) {
                        cycle = BillingCycle.builder().type(BillingCycleType.DAILY).build();
                    } else if (i <= 7) {
                        cycle = BillingCycle.builder()
                                .type(BillingCycleType.WEEKLY)
                                .startDayOfWeek(DayOfWeekEnum.SEGUNDA)
                                .endDayOfWeek(DayOfWeekEnum.SEXTA)
                                .build();
                    } else {
                        cycle = BillingCycle.builder().type(BillingCycleType.MONTHLY).build();
                    }
                    return Client.builder()
                            .id("client-" + i)
                            .name("Cliente Simulado " + i)
                            .active(true)
                            .billingCycle(cycle)
                            .build();
                })
                .toList();

        final Motoboy moto1 = Motoboy.builder()
                .id("moto-1")
                .name("Carlos Eduardo (Entregador 1)")
                .type(MotoboyType.FIXO)
                .active(true)
                .build();

        final Motoboy moto2 = Motoboy.builder()
                .id("moto-2")
                .name("Marcos Vinícius (Entregador 2)")
                .type(MotoboyType.TERCEIRIZADO)
                .active(true)
                .build();

        final String[] localities = {
                "Savassi", "Lourdes", "Funcionários", "Pampulha", "Barreiro", "Buritis",
                "Santa Efigênia", "Belvedere", "Serra", "Centro", "Mangabeiras", "Vila da Serra"
        };

        when(clientRepository.findByActiveTrue()).thenReturn(Flux.fromIterable(tenClients));
        for (final Client client : tenClients) {
            when(clientRepository.findById(client.getId())).thenReturn(Mono.just(client));

            // 12 orders per client: 5 for moto-1 and 7 for moto-2 across 12 distinct localities
            final List<Order> clientOrders = java.util.stream.IntStream.rangeClosed(1, 12)
                    .mapToObj(orderIdx -> {
                        final String assignedMotoboyId = orderIdx <= 5 ? "moto-1" : "moto-2";
                        final String locality = localities[orderIdx - 1];
                        return Order.builder()
                                .id(client.getId() + "-ord-" + orderIdx)
                                .orderNumber(1000L + orderIdx)
                                .clientId(client.getId())
                                .motoboyId(assignedMotoboyId)
                                .destinationAddress(com.hyperplane.rodo.model.Address.builder()
                                        .street("Rua " + locality)
                                        .number(String.valueOf(100 + orderIdx))
                                        .neighborhood(locality)
                                        .city("Belo Horizonte")
                                        .state("MG")
                                        .build())
                                .routeDistanceKm(5.0 + orderIdx)
                                .amount(new BigDecimal("30.00"))
                                .motoboyCommissionAmount(new BigDecimal("24.00"))
                                .status(OrderStatus.ENTREGUE)
                                .createdAt(Instant.parse("2026-07-31T12:00:00Z"))
                                .build();
                    })
                    .toList();

            when(orderRepository.findByClientIdAndStatusAndCreatedAtBetween(
                    org.mockito.ArgumentMatchers.eq(client.getId()),
                    org.mockito.ArgumentMatchers.eq(OrderStatus.ENTREGUE),
                    any(Instant.class),
                    any(Instant.class)
            )).thenReturn(Flux.fromIterable(clientOrders));
        }

        when(motoboyRepository.findById("moto-1")).thenReturn(Mono.just(moto1));
        when(motoboyRepository.findById("moto-2")).thenReturn(Mono.just(moto2));

        final java.util.concurrent.atomic.AtomicInteger repCounter = new java.util.concurrent.atomic.AtomicInteger(1);
        when(reportRepository.save(any(Report.class))).thenAnswer(inv -> {
            final Report r = inv.getArgument(0);
            return Mono.just(r.toBuilder().id("rep-" + repCounter.getAndIncrement()).build());
        });

        final java.util.concurrent.atomic.AtomicInteger poCounter = new java.util.concurrent.atomic.AtomicInteger(1);
        when(paymentOrderRepository.save(any(PaymentOrder.class))).thenAnswer(inv -> {
            final PaymentOrder po = inv.getArgument(0);
            return Mono.just(po.toBuilder().id("po-" + poCounter.getAndIncrement()).build());
        });

        when(paymentOrderRepository.findByReportId(any(String.class))).thenAnswer(inv -> {
            final String repId = inv.getArgument(0);
            return Mono.just(PaymentOrder.builder()
                    .id("po-for-" + repId)
                    .reportId(repId)
                    .status(PaymentOrderStatus.AGUARDANDO_PAGAMENTO)
                    .build());
        });

        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> Mono.just(inv.getArgument(0)));

        // Act
        final Flux<ReportResponseDTO> result = reportService.processDailyBillingForDate(referenceDate);

        // Assert: 10 CLIENTE reports (3 DAILY + 4 WEEKLY + 3 MONTHLY) + 2 MOTOBOY reports + 1 EMPRESA report = 13 reports total
        StepVerifier.create(result.collectList())
                .expectNextMatches(reports -> {
                    if (reports.size() != 13) {
                        return false;
                    }
                    final List<ReportResponseDTO> clientReports = reports.stream()
                            .filter(r -> r.type() == ReportType.CLIENTE)
                            .toList();
                    final List<ReportResponseDTO> motoboyReports = reports.stream()
                            .filter(r -> r.type() == ReportType.MOTOBOY)
                            .toList();
                    final List<ReportResponseDTO> empresaReports = reports.stream()
                            .filter(r -> r.type() == ReportType.EMPRESA)
                            .toList();

                    final long dailyCount = clientReports.stream()
                            .filter(r -> "DAILY".equals(r.targetEntitySubtype()))
                            .count();
                    final long weeklyCount = clientReports.stream()
                            .filter(r -> "WEEKLY".equals(r.targetEntitySubtype()))
                            .count();
                    final long monthlyCount = clientReports.stream()
                            .filter(r -> "MONTHLY".equals(r.targetEntitySubtype()))
                            .count();

                    final boolean allClientsHave12Orders = clientReports.stream()
                            .allMatch(r -> r.totalOrders() == 12
                                    && r.orderIds().size() == 12
                                    && r.totalAmount().compareTo(new BigDecimal("360.00")) == 0
                                    && r.paymentOrderStatus() == PaymentOrderStatus.AGUARDANDO_PAGAMENTO);

                    // Moto 1 delivered 5 orders * 10 clients = 50 orders (50 * R$ 24.00 = R$ 1200.00)
                    final boolean moto1Valid = motoboyReports.stream()
                            .anyMatch(r -> "moto-1".equals(r.targetEntityId())
                                    && r.totalOrders() == 50
                                    && r.orderIds().size() == 50
                                    && r.totalAmount().compareTo(new BigDecimal("1200.00")) == 0);

                    // Moto 2 delivered 7 orders * 10 clients = 70 orders (70 * R$ 24.00 = R$ 1680.00)
                    final boolean moto2Valid = motoboyReports.stream()
                            .anyMatch(r -> "moto-2".equals(r.targetEntityId())
                                    && r.totalOrders() == 70
                                    && r.orderIds().size() == 70
                                    && r.totalAmount().compareTo(new BigDecimal("1680.00")) == 0);

                    return clientReports.size() == 10
                            && motoboyReports.size() == 2
                            && empresaReports.size() == 1
                            && empresaReports.get(0).companySummary() != null
                            && dailyCount == 3
                            && weeklyCount == 4
                            && monthlyCount == 3
                            && allClientsHave12Orders
                            && moto1Valid
                            && moto2Valid;
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("Should emit daily MOTOBOY report and daily EMPRESA report at 20:00 even on a weekday when only WEEKLY/MONTHLY clients had deliveries")
    void shouldEmitDailyMotoboyReportEvenWhenNoClientCycleClosesOnThatDay() {
        // Arrange: Tuesday 2026-11-24 (no WEEKLY [SEXTA] or MONTHLY client closes on Tuesday, but Motoboys and Empresa close DAILY at 20:00)
        final LocalDate tuesday = LocalDate.of(2026, 11, 24);
        final Client weeklyClient = Client.builder()
                .id("client-weekly-1")
                .name("Cliente Semanal")
                .active(true)
                .billingCycle(BillingCycle.builder()
                        .type(BillingCycleType.WEEKLY)
                        .startDayOfWeek(DayOfWeekEnum.SEGUNDA)
                        .endDayOfWeek(DayOfWeekEnum.SEXTA)
                        .build())
                .build();

        final Motoboy moto1 = Motoboy.builder()
                .id("moto-daily-1")
                .name("Carlos Eduardo")
                .type(MotoboyType.FIXO)
                .active(true)
                .build();

        final Order deliveredOrder = Order.builder()
                .id("ord-tue-1")
                .orderNumber(3001L)
                .clientId("client-weekly-1")
                .motoboyId("moto-daily-1")
                .routeDistanceKm(10.0)
                .amount(new BigDecimal("100.00"))
                .motoboyCommissionAmount(new BigDecimal("70.00"))
                .status(OrderStatus.ENTREGUE)
                .createdAt(Instant.parse("2026-11-24T15:00:00Z"))
                .build();

        when(clientRepository.findByActiveTrue()).thenReturn(Flux.just(weeklyClient));
        when(orderRepository.findByCreatedAtBetween(any(Instant.class), any(Instant.class)))
                .thenReturn(Flux.just(deliveredOrder));
        when(motoboyRepository.findById("moto-daily-1")).thenReturn(Mono.just(moto1));
        when(reportRepository.save(any(Report.class))).thenAnswer(inv -> {
            final Report r = inv.getArgument(0);
            return Mono.just(r.toBuilder().id("rep-daily-" + r.getType()).build());
        });

        // Act
        final Flux<ReportResponseDTO> result = reportService.processDailyBillingForDate(tuesday);

        // Assert: 0 CLIENTE reports (weekly client closes on Friday), 1 MOTOBOY daily report + 1 EMPRESA daily report
        StepVerifier.create(result.collectList())
                .expectNextMatches(reports ->
                        reports.size() == 2
                                && reports.get(0).type() == ReportType.MOTOBOY
                                && reports.get(0).targetEntityId().equals("moto-daily-1")
                                && reports.get(0).totalOrders() == 1
                                && reports.get(0).totalAmount().compareTo(new BigDecimal("70.00")) == 0
                                && reports.get(1).type() == ReportType.EMPRESA
                                && reports.get(1).companySummary() != null
                                && reports.get(1).companySummary().totalMotoboysAmountToPay().compareTo(new BigDecimal("70.00")) == 0
                )
                .verifyComplete();
    }

    @Test
    @DisplayName("Should generate EMPRESA (RDO) report with all 8 RESUMO GERAL fields, per-motoboy aggregates, total motoboys amount to pay, and dispatch email to MASTER")
    void shouldGenerateDailyCompanyReportWithResumoGeralMotoboyAggregatesAndEmailToMaster() {
        final LocalDate today = LocalDate.of(2026, 11, 23);

        final Motoboy moto1 = Motoboy.builder()
                .id("moto-1")
                .name("Carlos Eduardo Silva")
                .type(MotoboyType.FIXO)
                .active(true)
                .build();
        final Motoboy moto2 = Motoboy.builder()
                .id("moto-2")
                .name("Marcos Vinícius Souza")
                .type(MotoboyType.TERCEIRIZADO)
                .active(true)
                .build();

        // 1 paid order (R$ 4035.00, commission R$ 3228.00) for moto-1
        // + 1 awaiting payment order (R$ 400.00, commission R$ 320.00) for moto-2
        // + 1 collected order (R$ 200.00, commission R$ 140.00) for moto-1
        // + 1 pending order (R$ 100.00, commission R$ 70.00) for moto-2 (PENDENTE should NOT count in Lucro Estimado!)
        final Order paidOrder = Order.builder()
                .id("ord-paid-1")
                .clientId("c-1")
                .motoboyId("moto-1")
                .routeDistanceKm(100.0)
                .amount(new BigDecimal("4035.00"))
                .motoboyCommissionAmount(new BigDecimal("3228.00"))
                .status(OrderStatus.PAGO)
                .createdAt(Instant.parse("2026-11-23T12:00:00Z"))
                .build();
        final Order awaitingOrder = Order.builder()
                .id("ord-await-1")
                .clientId("c-1")
                .motoboyId("moto-2")
                .routeDistanceKm(20.0)
                .amount(new BigDecimal("400.00"))
                .motoboyCommissionAmount(new BigDecimal("320.00"))
                .status(OrderStatus.AGUARDANDO_PAGAMENTO)
                .createdAt(Instant.parse("2026-11-23T14:00:00Z"))
                .build();
        final Order collectedOrder = Order.builder()
                .id("ord-col-1")
                .clientId("c-1")
                .motoboyId("moto-1")
                .routeDistanceKm(10.0)
                .amount(new BigDecimal("200.00"))
                .motoboyCommissionAmount(new BigDecimal("140.00"))
                .status(OrderStatus.COLETADO)
                .createdAt(Instant.parse("2026-11-23T15:00:00Z"))
                .build();
        final Order pendenteOrder = Order.builder()
                .id("ord-pendente-1")
                .clientId("c-1")
                .motoboyId("moto-2")
                .routeDistanceKm(5.0)
                .amount(new BigDecimal("100.00"))
                .motoboyCommissionAmount(new BigDecimal("70.00"))
                .status(OrderStatus.PENDENTE)
                .createdAt(Instant.parse("2026-11-23T16:00:00Z"))
                .build();

        when(clientRepository.findByActiveTrue()).thenReturn(Flux.empty());
        when(orderRepository.findByCreatedAtBetween(any(Instant.class), any(Instant.class)))
                .thenReturn(Flux.just(paidOrder, awaitingOrder, collectedOrder, pendenteOrder));
        when(expenseService.sumExpensesInPeriod(any(Instant.class), any(Instant.class)))
                .thenReturn(Mono.just(new BigDecimal("330.00")));
        when(motoboyRepository.findById("moto-1")).thenReturn(Mono.just(moto1));
        when(motoboyRepository.findById("moto-2")).thenReturn(Mono.just(moto2));
        when(emailNotificationService.sendDailyCompanyReportToMaster(any(ReportResponseDTO.class)))
                .thenReturn(Mono.just("admin@rodo.com"));
        when(reportRepository.save(any(Report.class))).thenAnswer(inv -> {
            final Report r = inv.getArgument(0);
            return Mono.just(r.toBuilder().id("rep-" + r.getType()).build());
        });

        StepVerifier.create(reportService.processDailyBillingForDate(today).collectList())
                .expectNextMatches(reports -> {
                    final ReportResponseDTO emp = reports.stream()
                            .filter(r -> r.type() == ReportType.EMPRESA)
                            .findFirst()
                            .orElse(null);
                    if (emp == null || emp.companySummary() == null) {
                        return false;
                    }
                    final var s = emp.companySummary();
                    // Receitas = 4035 + 400 + 200 + 100 = 4735.00
                    // Já Recebido = 4035.00 (1 pago)
                    // A Receber = 400 + 200 + 100 = 700.00 (3 a receber)
                    // Repasses = 3228 + 320 + 140 + 70 = 3758.00 (Pagos: 3228.00)
                    // Despesas = 330.00
                    // Lucro (somente pagos) = 4035.00 - 3228.00 - 330.00 = 477.00
                    // Lucro Estimado (COLETADO para frente: PAGO + AGUARDANDO_PAGAMENTO + ENTREGUE + COLETADO)
                    //   Receita elegível = 4035 + 400 + 200 = 4635.00 (exclui PENDENTE de 100.00)
                    //   Repasse elegível = 3228 + 320 + 140 = 3688.00 (exclui PENDENTE de 70.00)
                    //   Despesas = 330.00
                    //   Lucro Estimado = 4635.00 - 3688.00 - 330.00 = 617.00
                    return s.totalRevenue().compareTo(new BigDecimal("4735.00")) == 0
                            && s.paidRevenue().compareTo(new BigDecimal("4035.00")) == 0
                            && s.pendingRevenue().compareTo(new BigDecimal("700.00")) == 0
                            && s.netProfit().compareTo(new BigDecimal("477.00")) == 0
                            && s.estimatedProfit() != null
                            && s.estimatedProfit().compareTo(new BigDecimal("617.00")) == 0
                            && s.totalCommissions().compareTo(new BigDecimal("3758.00")) == 0
                            && s.paidCommissions().compareTo(new BigDecimal("3228.00")) == 0
                            && s.totalExpenses().compareTo(new BigDecimal("330.00")) == 0
                            && s.collaboratorShare().compareTo(new BigDecimal("3758.00")) == 0
                            && s.totalOrders() == 4L
                            && s.paidOrdersCount() == 1L
                            && s.pendingOrdersCount() == 3L
                            && s.motoboyAggregates().size() == 2
                            && s.totalMotoboysAmountToPay().compareTo(new BigDecimal("3758.00")) == 0
                            && Boolean.TRUE.equals(s.emailSentToMaster())
                            && "admin@rodo.com".equals(s.masterEmailRecipient());
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("Should allow deleting MOTOBOY and EMPRESA reports and reject deleting CLIENTE reports")
    void shouldAllowDeletingMotoboyAndEmpresaReportsAndRejectClientReports() {
        final Report motoReport = Report.builder()
                .id("rep-moto-del")
                .type(ReportType.MOTOBOY)
                .build();
        final Report empReport = Report.builder()
                .id("rep-emp-del")
                .type(ReportType.EMPRESA)
                .build();
        final Report clientReport = Report.builder()
                .id("rep-cli-del")
                .type(ReportType.CLIENTE)
                .build();

        when(reportRepository.findById("rep-moto-del")).thenReturn(Mono.just(motoReport));
        when(reportRepository.findById("rep-emp-del")).thenReturn(Mono.just(empReport));
        when(reportRepository.findById("rep-cli-del")).thenReturn(Mono.just(clientReport));
        when(reportRepository.delete(any(Report.class))).thenReturn(Mono.empty());

        StepVerifier.create(reportService.deleteReport("rep-moto-del"))
                .verifyComplete();

        StepVerifier.create(reportService.deleteReport("rep-emp-del"))
                .verifyComplete();

        StepVerifier.create(reportService.deleteReport("rep-cli-del"))
                .expectError(com.hyperplane.rodo.exception.BusinessRuleException.class)
                .verify();
    }

    @Test
    @DisplayName("Should skip automatic 20:00 daily billing when dailyJobExecutedToday flag is already true for today")
    void shouldSkipAutomaticDailyBillingWhenAlreadyExecutedToday() {
        final LocalDate today = LocalDate.of(2026, 11, 23);
        when(settingsService.hasDailyJobExecutedOnDate(today)).thenReturn(Mono.just(true));

        StepVerifier.create(reportService.processDailyBillingForDate(today))
                .verifyComplete();
    }

    @Test
    @DisplayName("Should emit BusinessRuleException alert when user tries to run manual job on same day after automatic or manual job already ran")
    void shouldThrowBusinessRuleExceptionWhenManualJobTriggeredAndAlreadyExecutedToday() {
        final LocalDate today = LocalDate.of(2026, 11, 23);
        when(settingsService.hasDailyJobExecutedOnDate(today)).thenReturn(Mono.just(true));

        StepVerifier.create(reportService.triggerManualDailyBilling(today))
                .expectErrorMatches(err ->
                        err instanceof com.hyperplane.rodo.exception.BusinessRuleException
                                && err.getMessage().contains("já foi executado hoje")
                )
                .verify();
    }

    @Test
    @DisplayName("Should configure 6-month TTL index (15552000 seconds) on Report.createdAt and delete all reports older than 6 months")
    void shouldEnforceSixMonthTtlPolicyOnAllReports() throws Exception {
        final org.springframework.data.mongodb.core.index.Indexed ttlIndex =
                Report.class.getDeclaredField("createdAt")
                        .getAnnotation(org.springframework.data.mongodb.core.index.Indexed.class);
        org.junit.jupiter.api.Assertions.assertNotNull(ttlIndex);
        org.junit.jupiter.api.Assertions.assertEquals(15552000, ttlIndex.expireAfterSeconds());

        final Instant referenceNow = Instant.parse("2026-11-26T23:00:00Z");
        final Instant expectedCutoff = java.time.ZonedDateTime
                .ofInstant(referenceNow, java.time.ZoneId.of("America/Sao_Paulo"))
                .minusMonths(6)
                .toInstant();

        when(reportRepository.deleteByCreatedAtBefore(expectedCutoff)).thenReturn(Mono.just(3L));

        StepVerifier.create(reportService.purgeExpiredReports(referenceNow))
                .expectNext(3L)
                .verifyComplete();
    }
}
