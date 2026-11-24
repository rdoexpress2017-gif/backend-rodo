package com.hyperplane.rodo.repository;

import com.hyperplane.rodo.model.Client;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;

@Repository
public interface ClientRepository extends ReactiveMongoRepository<Client, String> {
    Flux<Client> findByActiveTrue();
}
