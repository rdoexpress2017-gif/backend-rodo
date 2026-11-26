package com.hyperplane.rodo.service;

import com.hyperplane.rodo.dto.PaymentOrderResponseDTO;
import com.hyperplane.rodo.exception.BusinessRuleException;
import com.hyperplane.rodo.exception.ResourceNotFoundException;
import com.hyperplane.rodo.model.Client;
import com.hyperplane.rodo.model.PaymentOrder;
import com.hyperplane.rodo.model.enums.OrderStatus;
import com.hyperplane.rodo.model.enums.PaymentOrderStatus;
import com.hyperplane.rodo.repository.ClientRepository;
import com.hyperplane.rodo.repository.OrderRepository;
import com.hyperplane.rodo.repository.PaymentOrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.List;

@Service
@Slf4j
@RequiredArgsConstructor
public class PaymentOrderService {

    private final PaymentOrderRepository paymentOrderRepository;
    private final OrderRepository orderRepository;
    private final ClientRepository clientRepository;

    public Flux<PaymentOrderResponseDTO> findAll(String clientId, PaymentOrderStatus status) {
        return ((clientId != null && !clientId.isBlank() && status != null)
                ? paymentOrderRepository.findByClientIdAndStatus(clientId, status)
                : (clientId != null && !clientId.isBlank())
                ? paymentOrderRepository.findByClientId(clientId)
                : (status != null)
                ? paymentOrderRepository.findByStatus(status)
                : paymentOrderRepository.findAll())
                .concatMap(this::enrichPaymentOrder);
    }

    public Mono<PaymentOrderResponseDTO> findById(String id) {
        return paymentOrderRepository.findById(id)
                .switchIfEmpty(Mono.error(new ResourceNotFoundException("Ordem de Pagamento não encontrada: " + id)))
                .flatMap(this::enrichPaymentOrder);
    }

    @Transactional
    public Mono<PaymentOrderResponseDTO> settle(String id) {
        return paymentOrderRepository.findById(id)
                .switchIfEmpty(Mono.error(new ResourceNotFoundException("Ordem de Pagamento não encontrada: " + id)))
                .flatMap(po -> po.getStatus() == PaymentOrderStatus.PAGO
                        ? Mono.error(new BusinessRuleException("Esta Ordem de Pagamento já se encontra PAGA."))
                        : settleOrdersAndPaymentOrder(po, Instant.now()))
                .flatMap(this::enrichPaymentOrder);
    }

    private Mono<PaymentOrder> settleOrdersAndPaymentOrder(PaymentOrder po, Instant settledAt) {
        return orderRepository.findByIdIn(po.getOrderIds() != null ? po.getOrderIds() : List.of())
                .map(order -> order.toBuilder()
                        .status(OrderStatus.PAGO)
                        .paidAt(settledAt)
                        .updatedAt(settledAt)
                        .build())
                .flatMap(orderRepository::save)
                .then(Mono.defer(() -> paymentOrderRepository.save(
                        po.toBuilder()
                                .status(PaymentOrderStatus.PAGO)
                                .settledAt(settledAt)
                                .updatedAt(settledAt)
                                .build()
                )));
    }

    private Mono<PaymentOrderResponseDTO> enrichPaymentOrder(PaymentOrder po) {
        return (po.getClientId() != null
                ? clientRepository.findById(po.getClientId()).map(Client::getName).defaultIfEmpty("Cliente")
                : Mono.just("-"))
                .map(clientName -> new PaymentOrderResponseDTO(
                        po.getId(),
                        po.getReportId(),
                        po.getClientId(),
                        clientName,
                        po.getOrderIds(),
                        po.getTotalAmount(),
                        po.getStatus(),
                        po.getIssueDate(),
                        po.getSettledAt(),
                        po.getCreatedAt(),
                        po.getUpdatedAt()
                ));
    }
}
