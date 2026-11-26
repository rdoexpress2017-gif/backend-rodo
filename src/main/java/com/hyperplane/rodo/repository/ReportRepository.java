package com.hyperplane.rodo.repository;

import com.hyperplane.rodo.model.Report;
import com.hyperplane.rodo.model.enums.ReportType;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;

@Repository
public interface ReportRepository extends ReactiveMongoRepository<Report, String> {
    Flux<Report> findByTypeOrderByCreatedAtDesc(ReportType type);
    Flux<Report> findByTypeAndTargetEntityIdOrderByCreatedAtDesc(ReportType type, String targetEntityId);
    Mono<Long> deleteByCreatedAtBefore(Instant cutoff);
}
