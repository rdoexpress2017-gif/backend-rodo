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
        return mongoTemplate.findAndModify(
                        sequenceQuery(),
                        new Update().inc("seq", 1L),
                        FindAndModifyOptions.options().returnNew(true).upsert(true),
                        DatabaseSequence.class
                )
                .flatMap(seqDoc -> seqDoc.getSeq() != null && seqDoc.getSeq() == 1L
                        ? orderRepository.findTopByOrderByOrderNumberDesc()
                                .map(last -> last.getOrderNumber() != null
                                        ? Math.max(INITIAL_BASE_ORDER_NUMBER, last.getOrderNumber())
                                        : INITIAL_BASE_ORDER_NUMBER)
                                .defaultIfEmpty(INITIAL_BASE_ORDER_NUMBER)
                                .flatMap(baseOffset -> mongoTemplate.findAndModify(
                                        sequenceQuery(),
                                        new Update().inc("seq", baseOffset),
                                        FindAndModifyOptions.options().returnNew(true),
                                        DatabaseSequence.class
                                ))
                                .map(DatabaseSequence::getSeq)
                        : Mono.just(seqDoc.getSeq()));
    }

    private Query sequenceQuery() {
        return Query.query(Criteria.where("_id").is(DatabaseSequence.ORDERS_SEQUENCE_KEY));
    }
}
