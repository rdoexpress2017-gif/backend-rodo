package com.hyperplane.rodo.service;

import com.hyperplane.rodo.model.DatabaseSequence;
import com.hyperplane.rodo.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
@Slf4j
@RequiredArgsConstructor
public class SequenceGeneratorService {

    private static final long INITIAL_BASE_ORDER_NUMBER = 1000L;

    private final ReactiveMongoTemplate mongoTemplate;
    private final OrderRepository orderRepository;

    public Mono<Long> generateNextOrderNumber() {
        final Query query = Query.query(Criteria.where("_id").is(DatabaseSequence.ORDERS_SEQUENCE_KEY));
        final Update update = new Update().inc("seq", 1L);
        final FindAndModifyOptions options = FindAndModifyOptions.options().returnNew(true).upsert(true);

        return mongoTemplate.findAndModify(query, update, options, DatabaseSequence.class)
                .flatMap(seqDoc -> {
                    if (seqDoc.getSeq() != null && seqDoc.getSeq() == 1L) {
                        // Primeira inicialização da sequence: sincroniza com o maior orderNumber existente (ou 1000L)
                        return orderRepository.findTopByOrderByOrderNumberDesc()
                                .map(last -> last.getOrderNumber() != null
                                        ? Math.max(INITIAL_BASE_ORDER_NUMBER, last.getOrderNumber())
                                        : INITIAL_BASE_ORDER_NUMBER)
                                .defaultIfEmpty(INITIAL_BASE_ORDER_NUMBER)
                                .flatMap(baseOffset -> mongoTemplate.findAndModify(
                                        query,
                                        new Update().inc("seq", baseOffset),
                                        FindAndModifyOptions.options().returnNew(true),
                                        DatabaseSequence.class
                                ))
                                .map(DatabaseSequence::getSeq);
                    }
                    return Mono.just(seqDoc.getSeq());
                });
    }
}
