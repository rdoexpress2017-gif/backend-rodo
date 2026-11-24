package com.hyperplane.rodo.repository;

import com.hyperplane.rodo.model.PaymentOrder;
import com.hyperplane.rodo.model.enums.PaymentOrderStatus;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Repository
public interface PaymentOrderRepository extends ReactiveMongoRepository<PaymentOrder, String> {
    Mono<PaymentOrder> findByReportId(String reportId);
    Flux<PaymentOrder> findByClientId(String clientId);
    Flux<PaymentOrder> findByStatus(PaymentOrderStatus status);
    Flux<PaymentOrder> findByClientIdAndStatus(String clientId, PaymentOrderStatus status);
}
