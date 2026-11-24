package com.hyperplane.rodo.service;

import com.hyperplane.rodo.model.DatabaseSequence;
import com.hyperplane.rodo.model.Order;
import com.hyperplane.rodo.repository.OrderRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SequenceGeneratorServiceTest {

    @Mock
    private ReactiveMongoTemplate mongoTemplate;

    @Mock
    private OrderRepository orderRepository;

    @InjectMocks
    private SequenceGeneratorService sequenceGeneratorService;

    @Test
    @DisplayName("Should return atomic incremented sequence directly when sequence is already initialized (> 1)")
    void shouldReturnIncrementedSequenceWhenAlreadyInitialized() {
        // Arrange
        final DatabaseSequence existingSeq = DatabaseSequence.builder()
                .id(DatabaseSequence.ORDERS_SEQUENCE_KEY)
                .seq(1027L)
                .build();

        when(mongoTemplate.findAndModify(
                any(Query.class),
                any(Update.class),
                any(FindAndModifyOptions.class),
                eq(DatabaseSequence.class)
        )).thenReturn(Mono.just(existingSeq));

        // Act & Assert
        StepVerifier.create(sequenceGeneratorService.generateNextOrderNumber())
                .expectNext(1027L)
                .verifyComplete();

        verify(mongoTemplate, times(1)).findAndModify(
                any(Query.class),
                any(Update.class),
                any(FindAndModifyOptions.class),
                eq(DatabaseSequence.class)
        );
    }

    @Test
    @DisplayName("Should initialize sequence starting above highest existing orderNumber when sequence is created for the first time (seq == 1)")
    void shouldBootstrapSequenceFromHighestExistingOrderWhenCreatedFirstTime() {
        // Arrange
        final DatabaseSequence firstCreated = DatabaseSequence.builder()
                .id(DatabaseSequence.ORDERS_SEQUENCE_KEY)
                .seq(1L)
                .build();
        final DatabaseSequence bumped = DatabaseSequence.builder()
                .id(DatabaseSequence.ORDERS_SEQUENCE_KEY)
                .seq(1026L)
                .build();

        when(mongoTemplate.findAndModify(
                any(Query.class),
                any(Update.class),
                any(FindAndModifyOptions.class),
                eq(DatabaseSequence.class)
        )).thenReturn(Mono.just(firstCreated), Mono.just(bumped));

        when(orderRepository.findTopByOrderByOrderNumberDesc())
                .thenReturn(Mono.just(Order.builder().orderNumber(1025L).build()));

        // Act & Assert
        StepVerifier.create(sequenceGeneratorService.generateNextOrderNumber())
                .expectNext(1026L)
                .verifyComplete();
    }
}
