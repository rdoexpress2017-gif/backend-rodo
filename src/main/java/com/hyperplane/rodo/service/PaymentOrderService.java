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
        final Flux<PaymentOrder> source = (clientId != null && !clientId.isBlank() && status != null)
                ? paymentOrderRepository.findByClientIdAndStatus(clientId, status)
                : (clientId != null && !clientId.isBlank())
                ? paymentOrderRepository.findByClientId(clientId)
                : (status != null)
                ? paymentOrderRepository.findByStatus(status)
                : paymentOrderRepository.findAll();

        return source.concatMap(this::enrichPaymentOrder);
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
                .flatMap(po -> {
                    if (po.getStatus() == PaymentOrderStatus.PAGO) {
                        return Mono.error(new BusinessRuleException("Esta Ordem de Pagamento já se encontra PAGA."));
                    }
                    final Instant now = Instant.now();
                    final List<String> orderIds = po.getOrderIds() != null ? po.getOrderIds() : List.of();

                    return orderRepository.findByIdIn(orderIds)
                            .map(order -> order.toBuilder()
                                    .status(OrderStatus.PAGO)
                                    .paidAt(now)
                                    .updatedAt(now)
                                    .build())
                            .flatMap(orderRepository::save)
                            .then(Mono.defer(() -> paymentOrderRepository.save(
                                    po.toBuilder()
                                            .status(PaymentOrderStatus.PAGO)
                                            .settledAt(now)
                                            .updatedAt(now)
                                            .build()
                            )));
                })
                .flatMap(this::enrichPaymentOrder);
    }

    private Mono<PaymentOrderResponseDTO> enrichPaymentOrder(PaymentOrder po) {
        final Mono<String> clientNameMono = po.getClientId() != null
                ? clientRepository.findById(po.getClientId()).map(Client::getName).defaultIfEmpty("Cliente")
                : Mono.just("-");

        return clientNameMono.map(clientName -> new PaymentOrderResponseDTO(
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
