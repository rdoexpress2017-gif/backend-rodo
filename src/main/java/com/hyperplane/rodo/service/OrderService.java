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
        final Query countQuery = buildFilterQuery(startDate, endDate, clientId, motoboyId, status, createdByUserId);
        final Query pageQuery = buildFilterQuery(startDate, endDate, clientId, motoboyId, status, createdByUserId)
                .with(Sort.by(Sort.Direction.DESC, "createdAt"))
                .skip((long) page * size)
                .limit(size);

        return Mono.zip(
                mongoTemplate.find(pageQuery, Order.class).concatMap(this::enrichOrderResponse).collectList(),
                mongoTemplate.count(countQuery, Order.class)
        ).map(tuple -> {
            final List<OrderResponseDTO> items = tuple.getT1();
            final long total = tuple.getT2();
            final int totalPages = size > 0 ? (int) Math.ceil((double) total / size) : 1;
            return new PagedResponseDTO<>(items, page, size, total, Math.max(totalPages, 1));
        });
    }

    public Flux<OrderResponseDTO> findByIds(Collection<String> ids) {
        if (ids == null || ids.isEmpty()) {
            return Flux.empty();
        }
        return orderRepository.findByIdIn(ids)
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
        final Query query = buildFilterQuery(startDate, endDate, clientId, motoboyId, status, createdByUserId)
                .with(Sort.by(Sort.Direction.DESC, "createdAt"));
        return mongoTemplate.find(query, Order.class)
                .concatMap(this::enrichOrderResponse);
    }

    public Mono<FinancialSummaryDTO> getFinancialSummary(Instant startDate, Instant endDate) {
        final Query query = buildFilterQuery(startDate, endDate, null, null, null, null);
        final Mono<List<Order>> ordersMono = mongoTemplate.find(query, Order.class)
                .filter(order -> order.getStatus() != OrderStatus.CANCELADO)
                .collectList();
        final Mono<BigDecimal> expensesMono = expenseService.sumExpensesInPeriod(startDate, endDate)
                .defaultIfEmpty(BigDecimal.ZERO);

        return Mono.zip(ordersMono, expensesMono)
                .map(tuple -> {
                    final List<Order> orders = tuple.getT1();
                    final BigDecimal totalExpenses = tuple.getT2();

                    final List<Order> paidOrders = orders.stream()
                            .filter(order -> order.getStatus() == OrderStatus.PAGO)
                            .toList();
                    final List<Order> pendingOrders = orders.stream()
                            .filter(order -> order.getStatus() != OrderStatus.PAGO)
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

                    // Lucro (netBalance) é calculado SOMENTE com os pedidos em status PAGO
                    final BigDecimal profit = paidRevenue
                            .subtract(paidCommissions)
                            .subtract(totalExpenses);

                    return new FinancialSummaryDTO(
                            totalRevenue,
                            totalCommissions,
                            totalExpenses,
                            totalCommissions,
                            orders.size(),
                            profit,
                            totalExpenses,
                            paidRevenue,
                            pendingRevenue,
                            paidCommissions,
                            paidOrders.size(),
                            pendingOrders.size()
                    );
                });
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
                )
                .map(tuple -> {
                    final Double commissionPct = tuple.getT1().defaultMotoboyCommissionPercentage();
                    final Long nextOrderNumber = tuple.getT2();
                    final BigDecimal commissionAmount = calculateCommission(requestDTO.amount(), commissionPct);
                    final Instant now = Instant.now();

                    return orderMapper.toEntity(requestDTO).toBuilder()
                            .orderNumber(nextOrderNumber)
                            .requesterName(requestDTO.requesterName())
                            .createdByUserId(currentUserId)
                            .motoboyCommissionAmount(commissionAmount)
                            .status(OrderStatus.PENDENTE)
                            .createdAt(now)
                            .updatedAt(now)
                            .build();
                })
                .flatMap(orderRepository::save)
                .flatMap(this::enrichOrderResponse);
    }

    public Mono<OrderResponseDTO> update(String id, OrderRequestDTO requestDTO) {
        return orderRepository.findById(id)
                .switchIfEmpty(Mono.error(new ResourceNotFoundException("Pedido não encontrado: " + id)))
                .flatMap(existing -> settingsService.getSettings()
                        .map(settings -> {
                            final BigDecimal commissionAmount = calculateCommission(
                                    requestDTO.amount(),
                                    settings.defaultMotoboyCommissionPercentage()
                            );
                            return existing.toBuilder()
                                    .clientId(requestDTO.clientId())
                                    .motoboyId(requestDTO.motoboyId())
                                    .requesterName(requestDTO.requesterName())
                                    .createdByUserId(existing.getCreatedByUserId()) // Strictly immutable
                                    .originAddress(orderMapper.toAddress(requestDTO.originAddress()))
                                    .destinationAddress(orderMapper.toAddress(requestDTO.destinationAddress()))
                                    .routeDistanceKm(requestDTO.routeDistanceKm())
                                    .amount(requestDTO.amount())
                                    .motoboyCommissionAmount(commissionAmount)
                                    .updatedAt(Instant.now())
                                    .build();
                        }))
                .flatMap(orderRepository::save)
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
        final Mono<String> clientNameMono = order.getClientId() != null
                ? clientRepository.findById(order.getClientId()).map(Client::getName).defaultIfEmpty("Cliente Removido")
                : Mono.just("-");
        final Mono<String> motoboyNameMono = order.getMotoboyId() != null
                ? motoboyRepository.findById(order.getMotoboyId()).map(Motoboy::getName).defaultIfEmpty("Motoboy Removido")
                : Mono.just("-");
        final Mono<String> userNameMono = order.getCreatedByUserId() != null
                ? userRepository.findById(order.getCreatedByUserId()).map(User::getName).defaultIfEmpty("Sistema")
                : Mono.just("Sistema");

        return Mono.zip(clientNameMono, motoboyNameMono, userNameMono)
                .map(tuple -> new OrderResponseDTO(
                        order.getId(),
                        order.getOrderNumber(),
                        order.getClientId(),
                        tuple.getT1(),
                        order.getMotoboyId(),
                        tuple.getT2(),
                        order.getRequesterName(), // Never falls back to createdByUserName
                        order.getCreatedByUserId(),
                        tuple.getT3(),            // Attendant name resolved from created_by_user_id
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
        final OrderStatus current = existing.getStatus();
        final Instant now = Instant.now();

        if (targetStatus == OrderStatus.CANCELADO) {
            if (existing.getPaymentOrderId() != null
                    || current == OrderStatus.AGUARDANDO_PAGAMENTO
                    || current == OrderStatus.PAGO) {
                return Mono.error(new BusinessRuleException(
                        "Não é permitido cancelar um pedido que já foi incluído em uma Ordem de Pagamento ou pago."
                ));
            }
            return Mono.just(existing.toBuilder().status(OrderStatus.CANCELADO).updatedAt(now).build());
        }

        if (current == OrderStatus.PENDENTE && targetStatus == OrderStatus.COLETADO) {
            return Mono.just(existing.toBuilder()
                    .status(OrderStatus.COLETADO)
                    .collectedAt(now)
                    .updatedAt(now)
                    .build());
        }

        if ((current == OrderStatus.COLETADO || current == OrderStatus.PENDENTE) && targetStatus == OrderStatus.ENTREGUE) {
            return Mono.just(existing.toBuilder()
                    .status(OrderStatus.ENTREGUE)
                    .collectedAt(existing.getCollectedAt() != null ? existing.getCollectedAt() : now)
                    .deliveredAt(now)
                    .updatedAt(now)
                    .build());
        }

        if ((current == OrderStatus.ENTREGUE || current == OrderStatus.AGUARDANDO_PAGAMENTO)
                && targetStatus == OrderStatus.PAGO) {
            return Mono.just(existing.toBuilder()
                    .status(OrderStatus.PAGO)
                    .paidAt(now)
                    .updatedAt(now)
                    .build());
        }

        if (targetStatus == OrderStatus.PAGO) {
            return Mono.error(new BusinessRuleException(
                    "O pedido só pode ser marcado como PAGO após estar marcado como ENTREGUE."
            ));
        }

        return Mono.error(new BusinessRuleException(
                "Transição de status inválida de " + current + " para " + targetStatus
        ));
    }

    private BigDecimal calculateCommission(BigDecimal amount, Double percentage) {
        if (amount == null || percentage == null) {
            return BigDecimal.ZERO;
        }
        return amount.multiply(BigDecimal.valueOf(percentage))
                .divide(BigDecimal.valueOf(100.0), 2, RoundingMode.HALF_UP);
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

        if (criteriaList.isEmpty()) {
            return new Query();
        }
        return new Query(new Criteria().andOperator(criteriaList.toArray(new Criteria[0])));
    }
}
