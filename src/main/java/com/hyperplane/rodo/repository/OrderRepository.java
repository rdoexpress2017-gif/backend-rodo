package com.hyperplane.rodo.repository;

import com.hyperplane.rodo.model.Order;
import com.hyperplane.rodo.model.enums.OrderStatus;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.Collection;

@Repository
public interface OrderRepository extends ReactiveMongoRepository<Order, String> {
    Flux<Order> findByClientIdAndStatusAndCreatedAtBetween(String clientId, OrderStatus status, Instant start, Instant end);
    Flux<Order> findByClientIdAndCreatedAtBetween(String clientId, Instant start, Instant end);
    Flux<Order> findByMotoboyIdAndCreatedAtBetween(String motoboyId, Instant start, Instant end);
    Flux<Order> findByCreatedAtBetween(Instant start, Instant end);
    Flux<Order> findByPaymentOrderId(String paymentOrderId);
    Flux<Order> findByIdIn(Collection<String> ids);
    Mono<Order> findTopByOrderByOrderNumberDesc();
}
