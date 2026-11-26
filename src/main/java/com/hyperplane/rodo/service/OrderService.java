package com.hyperplane.rodo.service;

import com.hyperplane.rodo.dto.FinancialSummaryDTO;
import com.hyperplane.rodo.dto.OrderRequestDTO;
import com.hyperplane.rodo.dto.OrderResponseDTO;
import com.hyperplane.rodo.dto.OrderStatusUpdateDTO;
import com.hyperplane.rodo.dto.PagedResponseDTO;
import com.hyperplane.rodo.exception.BusinessRuleException;
import com.hyperplane.rodo.exception.ResourceNotFoundException;
import com.hyperplane.rodo.mapper.OrderMapper;
import com.hyperplane.rodo.model.Client;
import com.hyperplane.rodo.model.Motoboy;
import com.hyperplane.rodo.model.Order;
import com.hyperplane.rodo.model.User;
import com.hyperplane.rodo.model.enums.OrderStatus;
import com.hyperplane.rodo.repository.ClientRepository;
import com.hyperplane.rodo.repository.MotoboyRepository;
import com.hyperplane.rodo.repository.OrderRepository;
import com.hyperplane.rodo.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

@Service
@Slf4j
@RequiredArgsConstructor
public class OrderService {

    private final OrderRepository orderRepository;
    private final ClientRepository clientRepository;
    private final MotoboyRepository motoboyRepository;
    private final UserRepository userRepository;
    private final SettingsService settingsService;
    private final SequenceGeneratorService sequenceGeneratorService;
    private final ExpenseService expenseService;
    private final OrderMapper orderMapper;
    private final ReactiveMongoTemplate mongoTemplate;

    public Mono<PagedResponseDTO<OrderResponseDTO>> findPaginated(
            Instant startDate,
            Instant endDate,
            String clientId,
            String motoboyId,
            OrderStatus status,
            String createdByUserId,
            int page,
            int size
    ) {
        return Mono.zip(
                mongoTemplate.find(
                        buildFilterQuery(startDate, endDate, clientId, motoboyId, status, createdByUserId)
                                .with(Sort.by(Sort.Direction.DESC, "createdAt"))
                                .skip((long) page * size)
                                .limit(size),
                        Order.class
                ).concatMap(this::enrichOrderResponse).collectList(),
                mongoTemplate.count(
                        buildFilterQuery(startDate, endDate, clientId, motoboyId, status, createdByUserId),
                        Order.class
                )
        ).map(tuple -> new PagedResponseDTO<>(
                tuple.getT1(),
                page,
                size,
                tuple.getT2(),
                Math.max(size > 0 ? (int) Math.ceil((double) tuple.getT2() / size) : 1, 1)
        ));
    }

    public Flux<OrderResponseDTO> findByIds(Collection<String> ids) {
        return (ids == null || ids.isEmpty())
                ? Flux.empty()
                : orderRepository.findByIdIn(ids)
                        .sort(Comparator.comparing(Order::getCreatedAt, Comparator.nullsLast(Comparator.naturalOrder())))
                        .concatMap(this::enrichOrderResponse);
    }

    public Flux<OrderResponseDTO> findFilteredList(
            Instant startDate,
            Instant endDate,
            String clientId,
            String motoboyId,
            OrderStatus status,
            String createdByUserId
    ) {
        return mongoTemplate.find(
                buildFilterQuery(startDate, endDate, clientId, motoboyId, status, createdByUserId)
                        .with(Sort.by(Sort.Direction.DESC, "createdAt")),
                Order.class
        ).concatMap(this::enrichOrderResponse);
    }

    public Mono<FinancialSummaryDTO> getFinancialSummary(Instant startDate, Instant endDate) {
        return Mono.zip(
                mongoTemplate.find(buildFilterQuery(startDate, endDate, null, null, null, null), Order.class)
                        .filter(order -> order.getStatus() != OrderStatus.CANCELADO)
                        .collectList(),
                expenseService.sumExpensesInPeriod(startDate, endDate).defaultIfEmpty(BigDecimal.ZERO)
        ).map(tuple -> buildFinancialSummaryDTO(tuple.getT1(), tuple.getT2()));
    }

    private FinancialSummaryDTO buildFinancialSummaryDTO(List<Order> orders, BigDecimal totalExpenses) {
        return new FinancialSummaryDTO(
                sumTotalRevenue(orders),
                sumTotalCommissions(orders),
                totalExpenses,
                sumTotalCommissions(orders),
                orders.size(),
                calculateNetProfit(orders, totalExpenses),
                totalExpenses,
                sumPaidRevenue(orders),
                sumPendingRevenue(orders),
                sumPaidCommissions(orders),
                countPaidOrders(orders),
                countPendingOrders(orders),
                calculateEstimatedProfit(orders, totalExpenses)
        );
    }

    private BigDecimal sumTotalRevenue(List<Order> orders) {
        return orders.stream()
                .map(Order::getAmount)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private BigDecimal sumPaidRevenue(List<Order> orders) {
        return orders.stream()
                .filter(order -> order.getStatus() == OrderStatus.PAGO)
                .map(Order::getAmount)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private BigDecimal sumPendingRevenue(List<Order> orders) {
        return orders.stream()
                .filter(order -> order.getStatus() != OrderStatus.PAGO)
                .map(Order::getAmount)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private BigDecimal sumTotalCommissions(List<Order> orders) {
        return orders.stream()
                .map(Order::getMotoboyCommissionAmount)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private BigDecimal sumPaidCommissions(List<Order> orders) {
        return orders.stream()
                .filter(order -> order.getStatus() == OrderStatus.PAGO)
                .map(Order::getMotoboyCommissionAmount)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private long countPaidOrders(List<Order> orders) {
        return orders.stream()
                .filter(order -> order.getStatus() == OrderStatus.PAGO)
                .count();
    }

    private long countPendingOrders(List<Order> orders) {
        return orders.stream()
                .filter(order -> order.getStatus() != OrderStatus.PAGO)
                .count();
    }

    private BigDecimal calculateNetProfit(List<Order> orders, BigDecimal totalExpenses) {
        return sumPaidRevenue(orders)
                .subtract(sumPaidCommissions(orders))
                .subtract(totalExpenses);
    }

    private BigDecimal calculateEstimatedProfit(List<Order> orders, BigDecimal totalExpenses) {
        return orders.stream()
                .filter(this::isOrderCollectedOrForward)
                .map(Order::getAmount)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .subtract(
                        orders.stream()
                                .filter(this::isOrderCollectedOrForward)
                                .map(Order::getMotoboyCommissionAmount)
                                .filter(Objects::nonNull)
                                .reduce(BigDecimal.ZERO, BigDecimal::add)
                )
                .subtract(totalExpenses);
    }

    private boolean isOrderCollectedOrForward(Order order) {
        return order != null && order.getStatus() != null && switch (order.getStatus()) {
            case COLETADO, ENTREGUE, AGUARDANDO_PAGAMENTO, PAGO -> true;
            case PENDENTE, CANCELADO -> false;
        };
    }

    public Mono<OrderResponseDTO> findById(String id) {
        return orderRepository.findById(id)
                .switchIfEmpty(Mono.error(new ResourceNotFoundException("Pedido não encontrado: " + id)))
                .flatMap(this::enrichOrderResponse);
    }

    public Mono<OrderResponseDTO> create(OrderRequestDTO requestDTO, String currentUserId) {
        return Mono.zip(
                settingsService.getSettings(),
                sequenceGeneratorService.generateNextOrderNumber()
        ).flatMap(tuple -> orderRepository.save(
                orderMapper.toEntity(requestDTO).toBuilder()
                        .orderNumber(tuple.getT2())
                        .requesterName(requestDTO.requesterName())
                        .createdByUserId(currentUserId)
                        .motoboyCommissionAmount(calculateCommission(requestDTO.amount(), tuple.getT1().defaultMotoboyCommissionPercentage()))
                        .status(OrderStatus.PENDENTE)
                        .createdAt(Instant.now())
                        .updatedAt(Instant.now())
                        .build()
        )).flatMap(this::enrichOrderResponse);
    }

    public Mono<OrderResponseDTO> update(String id, OrderRequestDTO requestDTO) {
        return orderRepository.findById(id)
                .switchIfEmpty(Mono.error(new ResourceNotFoundException("Pedido não encontrado: " + id)))
                .flatMap(existing -> settingsService.getSettings()
                        .flatMap(settings -> orderRepository.save(
                                existing.toBuilder()
                                        .clientId(requestDTO.clientId())
                                        .motoboyId(requestDTO.motoboyId())
                                        .requesterName(requestDTO.requesterName())
                                        .createdByUserId(existing.getCreatedByUserId())
                                        .originAddress(orderMapper.toAddress(requestDTO.originAddress()))
                                        .destinationAddress(orderMapper.toAddress(requestDTO.destinationAddress()))
                                        .routeDistanceKm(requestDTO.routeDistanceKm())
                                        .amount(requestDTO.amount())
                                        .motoboyCommissionAmount(calculateCommission(
                                                requestDTO.amount(),
                                                settings.defaultMotoboyCommissionPercentage()
                                        ))
                                        .updatedAt(Instant.now())
                                        .build()
                        )))
                .flatMap(this::enrichOrderResponse);
    }

    public Mono<OrderResponseDTO> updateStatus(String id, OrderStatusUpdateDTO statusUpdateDTO) {
        return orderRepository.findById(id)
                .switchIfEmpty(Mono.error(new ResourceNotFoundException("Pedido não encontrado: " + id)))
                .flatMap(existing -> validateAndApplyStatusTransition(existing, statusUpdateDTO.status()))
                .flatMap(orderRepository::save)
                .flatMap(this::enrichOrderResponse);
    }

    public Mono<OrderResponseDTO> enrichOrderResponse(Order order) {
        return Mono.zip(
                order.getClientId() != null
                        ? clientRepository.findById(order.getClientId()).map(Client::getName).defaultIfEmpty("Cliente Removido")
                        : Mono.just("-"),
                order.getMotoboyId() != null
                        ? motoboyRepository.findById(order.getMotoboyId()).map(Motoboy::getName).defaultIfEmpty("Motoboy Removido")
                        : Mono.just("-"),
                order.getCreatedByUserId() != null
                        ? userRepository.findById(order.getCreatedByUserId()).map(User::getName).defaultIfEmpty("Sistema")
                        : Mono.just("Sistema")
        ).map(tuple -> new OrderResponseDTO(
                order.getId(),
                order.getOrderNumber(),
                order.getClientId(),
                tuple.getT1(),
                order.getMotoboyId(),
                tuple.getT2(),
                order.getRequesterName(),
                order.getCreatedByUserId(),
                tuple.getT3(),
                orderMapper.toAddressDTO(order.getOriginAddress()),
                orderMapper.toAddressDTO(order.getDestinationAddress()),
                order.getRouteDistanceKm(),
                order.getAmount(),
                order.getMotoboyCommissionAmount(),
                order.getStatus(),
                order.getPaymentOrderId(),
                order.getCreatedAt(),
                order.getCollectedAt(),
                order.getDeliveredAt(),
                order.getPaidAt(),
                order.getUpdatedAt()
        ));
    }

    private Mono<Order> validateAndApplyStatusTransition(Order existing, OrderStatus targetStatus) {
        if (targetStatus == OrderStatus.CANCELADO) {
            return (existing.getPaymentOrderId() != null
                    || existing.getStatus() == OrderStatus.AGUARDANDO_PAGAMENTO
                    || existing.getStatus() == OrderStatus.PAGO)
                    ? Mono.error(new BusinessRuleException("Não é permitido cancelar um pedido que já foi incluído em uma Ordem de Pagamento ou pago."))
                    : Mono.just(existing.toBuilder().status(OrderStatus.CANCELADO).updatedAt(Instant.now()).build());
        }

        if (existing.getStatus() == OrderStatus.PENDENTE && targetStatus == OrderStatus.COLETADO) {
            return Mono.just(existing.toBuilder()
                    .status(OrderStatus.COLETADO)
                    .collectedAt(Instant.now())
                    .updatedAt(Instant.now())
                    .build());
        }

        if ((existing.getStatus() == OrderStatus.COLETADO || existing.getStatus() == OrderStatus.PENDENTE)
                && targetStatus == OrderStatus.ENTREGUE) {
            return Mono.just(existing.toBuilder()
                    .status(OrderStatus.ENTREGUE)
                    .collectedAt(existing.getCollectedAt() != null ? existing.getCollectedAt() : Instant.now())
                    .deliveredAt(Instant.now())
                    .updatedAt(Instant.now())
                    .build());
        }

        if ((existing.getStatus() == OrderStatus.ENTREGUE || existing.getStatus() == OrderStatus.AGUARDANDO_PAGAMENTO)
                && targetStatus == OrderStatus.PAGO) {
            return Mono.just(existing.toBuilder()
                    .status(OrderStatus.PAGO)
                    .paidAt(Instant.now())
                    .updatedAt(Instant.now())
                    .build());
        }

        if (targetStatus == OrderStatus.PAGO) {
            return Mono.error(new BusinessRuleException("O pedido só pode ser marcado como PAGO após estar marcado como ENTREGUE."));
        }

        return Mono.error(new BusinessRuleException("Transição de status inválida de " + existing.getStatus() + " para " + targetStatus));
    }

    private BigDecimal calculateCommission(BigDecimal amount, Double percentage) {
        return (amount == null || percentage == null)
                ? BigDecimal.ZERO
                : amount.multiply(BigDecimal.valueOf(percentage)).divide(BigDecimal.valueOf(100.0), 2, RoundingMode.HALF_UP);
    }

    private Query buildFilterQuery(
            Instant startDate,
            Instant endDate,
            String clientId,
            String motoboyId,
            OrderStatus status,
            String createdByUserId
    ) {
        final List<Criteria> criteriaList = Stream.of(
                (startDate != null && endDate != null)
                        ? Criteria.where("created_at").gte(startDate).lte(endDate)
                        : (startDate != null ? Criteria.where("created_at").gte(startDate)
                        : (endDate != null ? Criteria.where("created_at").lte(endDate) : null)),
                (clientId != null && !clientId.isBlank()) ? Criteria.where("client_id").is(clientId) : null,
                (motoboyId != null && !motoboyId.isBlank()) ? Criteria.where("motoboy_id").is(motoboyId) : null,
                status != null ? Criteria.where("status").is(status) : null,
                (createdByUserId != null && !createdByUserId.isBlank()) ? Criteria.where("created_by_user_id").is(createdByUserId) : null
        ).filter(Objects::nonNull).toList();

        return criteriaList.isEmpty()
                ? new Query()
                : new Query(new Criteria().andOperator(criteriaList.toArray(new Criteria[0])));
    }
}
