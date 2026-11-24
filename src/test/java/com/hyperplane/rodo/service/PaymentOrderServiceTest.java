package com.hyperplane.rodo.service;

import com.hyperplane.rodo.dto.PaymentOrderResponseDTO;
import com.hyperplane.rodo.exception.BusinessRuleException;
import com.hyperplane.rodo.exception.ResourceNotFoundException;
import com.hyperplane.rodo.model.Client;
import com.hyperplane.rodo.model.Order;
import com.hyperplane.rodo.model.PaymentOrder;
import com.hyperplane.rodo.model.enums.OrderStatus;
import com.hyperplane.rodo.model.enums.PaymentOrderStatus;
import com.hyperplane.rodo.repository.ClientRepository;
import com.hyperplane.rodo.repository.OrderRepository;
import com.hyperplane.rodo.repository.PaymentOrderRepository;
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
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentOrderServiceTest {

    @Mock
    private PaymentOrderRepository paymentOrderRepository;

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private ClientRepository clientRepository;

    @InjectMocks
    private PaymentOrderService paymentOrderService;

    @Test
    @DisplayName("Should settle payment order atomically and cascade update linked orders to PAGO")
    void shouldSettlePaymentOrderAndCascadeUpdateOrdersToPago() {
        // Arrange
        final PaymentOrder po = PaymentOrder.builder()
                .id("po-1")
                .reportId("rep-1")
                .clientId("client-1")
                .orderIds(List.of("ord-1", "ord-2"))
                .totalAmount(new BigDecimal("120.00"))
                .status(PaymentOrderStatus.AGUARDANDO_PAGAMENTO)
                .issueDate(Instant.now())
                .createdAt(Instant.now())
                .build();

        final Order ord1 = Order.builder().id("ord-1").status(OrderStatus.AGUARDANDO_PAGAMENTO).build();
        final Order ord2 = Order.builder().id("ord-2").status(OrderStatus.AGUARDANDO_PAGAMENTO).build();

        when(paymentOrderRepository.findById("po-1")).thenReturn(Mono.just(po));
        when(orderRepository.findByIdIn(List.of("ord-1", "ord-2"))).thenReturn(Flux.just(ord1, ord2));
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> Mono.just(inv.getArgument(0)));
        when(paymentOrderRepository.save(any(PaymentOrder.class))).thenAnswer(inv -> Mono.just(inv.getArgument(0)));
        when(clientRepository.findById("client-1")).thenReturn(Mono.just(Client.builder().id("client-1").name("Mercado Central").build()));

        // Act
        final Mono<PaymentOrderResponseDTO> result = paymentOrderService.settle("po-1");

        // Assert
        StepVerifier.create(result)
                .expectNextMatches(dto ->
                        dto.id().equals("po-1")
                                && dto.status() == PaymentOrderStatus.PAGO
                                && dto.settledAt() != null
                                && dto.clientName().equals("Mercado Central")
                )
                .verifyComplete();
    }

    @Test
    @DisplayName("Should emit BusinessRuleException when settling an already paid PaymentOrder")
    void shouldThrowBusinessRuleExceptionWhenSettlingAlreadyPaidOrder() {
        // Arrange
        final PaymentOrder po = PaymentOrder.builder()
                .id("po-1")
                .status(PaymentOrderStatus.PAGO)
                .settledAt(Instant.now())
                .build();

        when(paymentOrderRepository.findById("po-1")).thenReturn(Mono.just(po));

        // Act
        final Mono<PaymentOrderResponseDTO> result = paymentOrderService.settle("po-1");

        // Assert
        StepVerifier.create(result)
                .expectError(BusinessRuleException.class)
                .verify();
    }

    @Test
    @DisplayName("Should emit ResourceNotFoundException when PaymentOrder does not exist")
    void shouldThrowResourceNotFoundWhenPaymentOrderDoesNotExist() {
        // Arrange
        when(paymentOrderRepository.findById("missing")).thenReturn(Mono.empty());

        // Act
        final Mono<PaymentOrderResponseDTO> result = paymentOrderService.settle("missing");

        // Assert
        StepVerifier.create(result)
                .expectError(ResourceNotFoundException.class)
                .verify();
    }
}
