package com.hyperplane.rodo.repository;

import com.hyperplane.rodo.model.Motoboy;
import com.hyperplane.rodo.model.enums.MotoboyType;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Repository
public interface MotoboyRepository extends ReactiveMongoRepository<Motoboy, String> {
    Mono<Motoboy> findByCpf(String cpf);
    Flux<Motoboy> findByActiveTrue();
    Flux<Motoboy> findByTypeAndActiveTrue(MotoboyType type);
}
