package com.hyperplane.rodo.service;

import com.hyperplane.rodo.dto.AddressDTO;
import com.hyperplane.rodo.dto.FinancialSummaryDTO;
import com.hyperplane.rodo.dto.OrderRequestDTO;
import com.hyperplane.rodo.dto.OrderResponseDTO;
import com.hyperplane.rodo.dto.OrderStatusUpdateDTO;
import com.hyperplane.rodo.dto.SystemSettingsResponseDTO;
import com.hyperplane.rodo.exception.BusinessRuleException;
import com.hyperplane.rodo.mapper.OrderMapper;
import com.hyperplane.rodo.model.Address;
import com.hyperplane.rodo.model.Client;
import com.hyperplane.rodo.model.Motoboy;
import com.hyperplane.rodo.model.Order;
import com.hyperplane.rodo.model.User;
import com.hyperplane.rodo.model.enums.OrderStatus;
import com.hyperplane.rodo.repository.ClientRepository;
import com.hyperplane.rodo.repository.MotoboyRepository;
import com.hyperplane.rodo.repository.OrderRepository;
import com.hyperplane.rodo.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.math.BigDecimal;
import java.time.Instant;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private ClientRepository clientRepository;

    @Mock
    private MotoboyRepository motoboyRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private SettingsService settingsService;

    @Mock
    private SequenceGeneratorService sequenceGeneratorService;

    @Mock
    private ExpenseService expenseService;

    @Mock
    private OrderMapper orderMapper;

    @Mock
    private ReactiveMongoTemplate mongoTemplate;

    @InjectMocks
    private OrderService orderService;

    @Test
    @DisplayName("Should create order keeping requesterName separate from createdByUserName (attendant)")
    void shouldCreateOrderWithAutomaticMotoboyCommissionAndCurrentUserId() {
        // Arrange
        final AddressDTO addressDTO = new AddressDTO("Rua A", "100", "Centro", "Belo Horizonte", "MG", "30110-000", -19.9, -43.9);
        final OrderRequestDTO request = new OrderRequestDTO("client-1", "moto-1", "Mariana Solicitante", addressDTO, addressDTO, 12.5, new BigDecimal("50.00"));
        final SystemSettingsResponseDTO settings = new SystemSettingsResponseDTO(
                "GLOBAL_CONFIG", new BigDecimal("2.50"), new BigDecimal("10.00"), 80.0, "admin", Instant.now(), false, null, "20:00"
        );
        final Order mappedOrder = Order.builder()
                .clientId("client-1")
                .motoboyId("moto-1")
                .requesterName("Mariana Solicitante")
                .originAddress(Address.builder().street("Rua A").number("100").city("Belo Horizonte").state("MG").build())
                .destinationAddress(Address.builder().street("Rua A").number("100").city("Belo Horizonte").state("MG").build())
                .routeDistanceKm(12.5)
                .amount(new BigDecimal("50.00"))
                .status(OrderStatus.PENDENTE)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        when(settingsService.getSettings()).thenReturn(Mono.just(settings));
        when(sequenceGeneratorService.generateNextOrderNumber()).thenReturn(Mono.just(1001L));
        when(orderMapper.toEntity(request)).thenReturn(mappedOrder);
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> {
            final Order saved = inv.getArgument(0);
            return Mono.just(saved.toBuilder().id("order-1").build());
        });
        when(clientRepository.findById("client-1")).thenReturn(Mono.just(Client.builder().id("client-1").name("Cliente Alpha").build()));
        when(motoboyRepository.findById("moto-1")).thenReturn(Mono.just(Motoboy.builder().id("moto-1").name("Carlos Moto").build()));
        when(userRepository.findById("user-atendente-1")).thenReturn(Mono.just(User.builder().id("user-atendente-1").name("Maria Atendente").build()));
        when(orderMapper.toAddressDTO(any())).thenReturn(addressDTO);

        // Act
        final Mono<OrderResponseDTO> result = orderService.create(request, "user-atendente-1");

        // Assert
        StepVerifier.create(result)
                .expectNextMatches(dto ->
                        dto.id().equals("order-1")
                                && dto.orderNumber().equals(1001L)
                                && dto.requesterName().equals("Mariana Solicitante")
                                && dto.createdByUserId().equals("user-atendente-1")
                                && dto.createdByUserName().equals("Maria Atendente")
                                && dto.motoboyCommissionAmount().compareTo(new BigDecimal("40.00")) == 0
                                && dto.status() == OrderStatus.PENDENTE
                                && dto.clientName().equals("Cliente Alpha")
                )
                .verifyComplete();
    }

    @Test
    @DisplayName("Should NOT populate requesterName with attendant name when requesterName is null")
    void shouldNotFallbackRequesterNameToAttendantName() {
        // Arrange
        final Order existing = Order.builder()
                .id("order-2")
                .orderNumber(1026L)
                .clientId("client-1")
                .motoboyId("moto-1")
                .requesterName(null)
                .createdByUserId("user-master-1")
                .status(OrderStatus.PENDENTE)
                .build();

        when(clientRepository.findById("client-1")).thenReturn(Mono.just(Client.builder().id("client-1").name("Cliente Alpha").build()));
        when(motoboyRepository.findById("moto-1")).thenReturn(Mono.just(Motoboy.builder().id("moto-1").name("Carlos Moto").build()));
        when(userRepository.findById("user-master-1")).thenReturn(Mono.just(User.builder().id("user-master-1").name("Rodrigo (Master)").build()));

        // Act
        final Mono<OrderResponseDTO> result = orderService.enrichOrderResponse(existing);

        // Assert
        StepVerifier.create(result)
                .expectNextMatches(dto ->
                        dto.requesterName() == null
                                && dto.createdByUserName().equals("Rodrigo (Master)")
                )
                .verifyComplete();
    }

    @Test
    @DisplayName("Should transition order from PENDENTE to COLETADO and set collectedAt")
    void shouldTransitionOrderFromPendenteToColetadoAndSetCollectedAt() {
        // Arrange
        final Order existing = Order.builder()
                .id("order-1")
                .orderNumber(1001L)
                .clientId("client-1")
                .motoboyId("moto-1")
                .createdByUserId("user-1")
                .amount(new BigDecimal("50.00"))
                .motoboyCommissionAmount(new BigDecimal("40.00"))
                .status(OrderStatus.PENDENTE)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        when(orderRepository.findById("order-1")).thenReturn(Mono.just(existing));
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> Mono.just(inv.getArgument(0)));
        when(clientRepository.findById("client-1")).thenReturn(Mono.empty());
        when(motoboyRepository.findById("moto-1")).thenReturn(Mono.empty());
        when(userRepository.findById("user-1")).thenReturn(Mono.empty());

        // Act
        final Mono<OrderResponseDTO> result = orderService.updateStatus("order-1", new OrderStatusUpdateDTO(OrderStatus.COLETADO));

        // Assert
        StepVerifier.create(result)
                .expectNextMatches(dto -> dto.status() == OrderStatus.COLETADO && dto.collectedAt() != null)
                .verifyComplete();
    }

    @Test
    @DisplayName("Should transition order from ENTREGUE to PAGO and set paidAt")
    void shouldTransitionOrderFromEntregueToPagoAndSetPaidAt() {
        // Arrange
        final Order existing = Order.builder()
                .id("order-entregue-1")
                .orderNumber(1002L)
                .clientId("client-1")
                .motoboyId("moto-1")
                .createdByUserId("user-1")
                .amount(new BigDecimal("50.00"))
                .motoboyCommissionAmount(new BigDecimal("40.00"))
                .status(OrderStatus.ENTREGUE)
                .collectedAt(Instant.now())
                .deliveredAt(Instant.now())
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        when(orderRepository.findById("order-entregue-1")).thenReturn(Mono.just(existing));
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> Mono.just(inv.getArgument(0)));
        when(clientRepository.findById("client-1")).thenReturn(Mono.empty());
        when(motoboyRepository.findById("moto-1")).thenReturn(Mono.empty());
        when(userRepository.findById("user-1")).thenReturn(Mono.empty());

        // Act
        final Mono<OrderResponseDTO> result = orderService.updateStatus("order-entregue-1", new OrderStatusUpdateDTO(OrderStatus.PAGO));

        // Assert
        StepVerifier.create(result)
                .expectNextMatches(dto -> dto.status() == OrderStatus.PAGO && dto.paidAt() != null)
                .verifyComplete();
    }

    @Test
    @DisplayName("Should emit BusinessRuleException when cancelling an order already linked to a PaymentOrder")
    void shouldThrowBusinessRuleExceptionWhenCancellingOrderAlreadyInPaymentOrder() {
        // Arrange
        final Order existing = Order.builder()
                .id("order-1")
                .status(OrderStatus.AGUARDANDO_PAGAMENTO)
                .paymentOrderId("po-99")
                .build();

        when(orderRepository.findById("order-1")).thenReturn(Mono.just(existing));

        // Act
        final Mono<OrderResponseDTO> result = orderService.updateStatus("order-1", new OrderStatusUpdateDTO(OrderStatus.CANCELADO));

        // Assert
        StepVerifier.create(result)
                .expectError(BusinessRuleException.class)
                .verify();
    }

    @Test
    @DisplayName("Should preserve createdByUserId immutability and update requesterName when updating an order")
    void shouldUpdateOrderWhilePreservingCreatedByUserIdImmutability() {
        // Arrange
        final AddressDTO addressDTO = new AddressDTO("Rua Nova", "200", "Savassi", "Belo Horizonte", "MG", "30110-000", -19.9, -43.9);
        final OrderRequestDTO request = new OrderRequestDTO("client-1", "moto-1", "Juliana", addressDTO, addressDTO, 15.0, new BigDecimal("100.00"));
        final Order existing = Order.builder()
                .id("order-1")
                .orderNumber(1001L)
                .clientId("client-1")
                .motoboyId("moto-1")
                .requesterName("Antigo Solicitante")
                .createdByUserId("original-user-id")
                .status(OrderStatus.PENDENTE)
                .createdAt(Instant.now())
                .build();
        final SystemSettingsResponseDTO settings = new SystemSettingsResponseDTO(
                "GLOBAL_CONFIG", new BigDecimal("2.50"), new BigDecimal("10.00"), 80.0, "admin", Instant.now(), false, null, "20:00"
        );

        when(orderRepository.findById("order-1")).thenReturn(Mono.just(existing));
        when(settingsService.getSettings()).thenReturn(Mono.just(settings));
        when(orderMapper.toAddress(addressDTO)).thenReturn(Address.builder().street("Rua Nova").number("200").build());
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> Mono.just(inv.getArgument(0)));
        when(clientRepository.findById("client-1")).thenReturn(Mono.empty());
        when(motoboyRepository.findById("moto-1")).thenReturn(Mono.empty());
        when(userRepository.findById("original-user-id")).thenReturn(Mono.just(User.builder().id("original-user-id").name("Maria Atendente").build()));
        when(orderMapper.toAddressDTO(any())).thenReturn(addressDTO);

        // Act
        final Mono<OrderResponseDTO> result = orderService.update("order-1", request);

        // Assert
        StepVerifier.create(result)
                .expectNextMatches(dto ->
                        dto.createdByUserId().equals("original-user-id")
                                && dto.createdByUserName().equals("Maria Atendente")
                                && dto.requesterName().equals("Juliana")
                                && dto.amount().compareTo(new BigDecimal("100.00")) == 0
                                && dto.motoboyCommissionAmount().compareTo(new BigDecimal("80.00")) == 0
                )
                .verifyComplete();
    }

    @Test
    @DisplayName("Should calculate FinancialSummary distinguishing totalRevenue, paidRevenue, pendingRevenue (a receber), pendingOrdersCount, and Lucro ONLY from PAGO orders")
    void shouldCalculateFinancialSummaryWithExpensesAndProfitOnlyFromPaidOrders() {
        // Arrange
        final Instant start = Instant.parse("2026-10-01T00:00:00Z");
        final Instant end = Instant.parse("2026-10-31T23:59:59Z");
        final Order o1 = Order.builder()
                .id("o-1")
                .amount(new BigDecimal("500.00"))
                .motoboyCommissionAmount(new BigDecimal("400.00"))
                .status(OrderStatus.ENTREGUE)
                .build();
        final Order o2 = Order.builder()
                .id("o-2")
                .amount(new BigDecimal("277.00"))
                .motoboyCommissionAmount(new BigDecimal("221.60"))
                .status(OrderStatus.PAGO)
                .build();
        final Order o3 = Order.builder()
                .id("o-3")
                .amount(new BigDecimal("100.00"))
                .motoboyCommissionAmount(new BigDecimal("80.00"))
                .status(OrderStatus.PENDENTE)
                .build();

        when(mongoTemplate.find(any(Query.class), eq(Order.class))).thenReturn(Flux.just(o1, o2, o3));
        when(expenseService.sumExpensesInPeriod(start, end)).thenReturn(Mono.just(new BigDecimal("15.40")));

        // Act
        final Mono<FinancialSummaryDTO> result = orderService.getFinancialSummary(start, end);

        // Assert:
        // Valor Total de Pedidos (totalRevenue) = 500.00 + 277.00 + 100.00 = 877.00
        // Valor Já Recebido (paidRevenue - status PAGO) = 277.00
        // Valor A Receber (pendingRevenue - status != PAGO) = 600.00
        // Repasses dos Pagos (paidCommissions) = 221.60
        // Despesas (Lançamentos) = 15.40
        // Quantidade Pagos (paidOrdersCount) = 1
        // Quantidade A Receber (pendingOrdersCount) = 2
        // Lucro (netBalance) = SOMENTE pedidos PAGOS: 277.00 - 221.60 - 15.40 = 40.00
        // Lucro Estimado (estimatedProfit) = Pedidos do status COLETADO para frente (o1 ENTREGUE + o2 PAGO, excluindo o3 PENDENTE):
        //   (500.00 + 277.00) - (400.00 + 221.60) - 15.40 = 777.00 - 621.60 - 15.40 = 140.00
        StepVerifier.create(result)
                .expectNextMatches(summary ->
                        summary.totalRevenue().compareTo(new BigDecimal("877.00")) == 0
                                && summary.paidRevenue().compareTo(new BigDecimal("277.00")) == 0
                                && summary.pendingRevenue().compareTo(new BigDecimal("600.00")) == 0
                                && summary.totalCommissions().compareTo(new BigDecimal("701.60")) == 0
                                && summary.paidCommissions().compareTo(new BigDecimal("221.60")) == 0
                                && summary.totalExpenses().compareTo(new BigDecimal("15.40")) == 0
                                && summary.totalOrders() == 3L
                                && summary.paidOrdersCount() == 1L
                                && summary.pendingOrdersCount() == 2L
                                && summary.netBalance().compareTo(new BigDecimal("40.00")) == 0
                                && summary.estimatedProfit() != null
                                && summary.estimatedProfit().compareTo(new BigDecimal("140.00")) == 0
                )
                .verifyComplete();
    }
}
