package com.hyperplane.rodo.service;

import com.hyperplane.rodo.dto.ExpenseRequestDTO;
import com.hyperplane.rodo.dto.ExpenseResponseDTO;
import com.hyperplane.rodo.exception.ResourceNotFoundException;
import com.hyperplane.rodo.mapper.ExpenseMapper;
import com.hyperplane.rodo.model.Expense;
import com.hyperplane.rodo.repository.ExpenseRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.math.BigDecimal;
import java.time.Instant;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ExpenseServiceTest {

    @Mock
    private ExpenseRepository expenseRepository;

    @Mock
    private ExpenseMapper expenseMapper;

    @Mock
    private ReactiveMongoTemplate mongoTemplate;

    @InjectMocks
    private ExpenseService expenseService;

    @Test
    @DisplayName("Should create expense with expenseDate, amount, and description")
    void shouldCreateExpenseWhenRequestIsValid() {
        // Arrange
        final Instant expenseDate = Instant.parse("2026-10-29T12:00:00Z");
        final ExpenseRequestDTO request = new ExpenseRequestDTO(
                expenseDate,
                new BigDecimal("120.00"),
                "Gasto com internet do mês"
        );
        final Expense entity = Expense.builder()
                .expenseDate(expenseDate)
                .amount(new BigDecimal("120.00"))
                .description("Gasto com internet do mês")
                .createdAt(expenseDate)
                .updatedAt(expenseDate)
                .build();
        final Expense savedEntity = entity.toBuilder().id("exp-1").build();
        final ExpenseResponseDTO responseDTO = new ExpenseResponseDTO(
                "exp-1",
                expenseDate,
                new BigDecimal("120.00"),
                "Gasto com internet do mês",
                expenseDate,
                expenseDate
        );

        when(expenseMapper.toEntity(request)).thenReturn(entity);
        when(expenseRepository.save(entity)).thenReturn(Mono.just(savedEntity));
        when(expenseMapper.toResponseDTO(savedEntity)).thenReturn(responseDTO);

        // Act
        final Mono<ExpenseResponseDTO> result = expenseService.create(request);

        // Assert
        StepVerifier.create(result)
                .expectNextMatches(dto ->
                        dto.id().equals("exp-1")
                                && dto.amount().compareTo(new BigDecimal("120.00")) == 0
                                && dto.description().equals("Gasto com internet do mês")
                                && dto.expenseDate().equals(expenseDate)
                )
                .verifyComplete();
    }

    @Test
    @DisplayName("Should update existing expense immutable fields")
    void shouldUpdateExpenseWhenIdExists() {
        // Arrange
        final Instant oldDate = Instant.parse("2026-10-20T12:00:00Z");
        final Instant newDate = Instant.parse("2026-10-29T15:00:00Z");
        final Expense existing = Expense.builder()
                .id("exp-1")
                .expenseDate(oldDate)
                .amount(new BigDecimal("100.00"))
                .description("Conserto antigo")
                .createdAt(oldDate)
                .updatedAt(oldDate)
                .build();
        final ExpenseRequestDTO updateReq = new ExpenseRequestDTO(
                newDate,
                new BigDecimal("180.50"),
                "Conserto do computador"
        );

        when(expenseRepository.findById("exp-1")).thenReturn(Mono.just(existing));
        when(expenseRepository.save(any(Expense.class))).thenAnswer(inv -> Mono.just(inv.getArgument(0)));
        when(expenseMapper.toResponseDTO(any(Expense.class))).thenAnswer(inv -> {
            final Expense e = inv.getArgument(0);
            return new ExpenseResponseDTO(e.getId(), e.getExpenseDate(), e.getAmount(), e.getDescription(), e.getCreatedAt(), e.getUpdatedAt());
        });

        // Act
        final Mono<ExpenseResponseDTO> result = expenseService.update("exp-1", updateReq);

        // Assert
        StepVerifier.create(result)
                .expectNextMatches(dto ->
                        dto.id().equals("exp-1")
                                && dto.expenseDate().equals(newDate)
                                && dto.amount().compareTo(new BigDecimal("180.50")) == 0
                                && dto.description().equals("Conserto do computador")
                )
                .verifyComplete();
    }

    @Test
    @DisplayName("Should emit ResourceNotFoundException when deleting non-existent expense")
    void shouldEmitResourceNotFoundWhenDeletingNonExistentExpense() {
        // Arrange
        when(expenseRepository.findById("exp-404")).thenReturn(Mono.empty());

        // Act
        final Mono<Void> result = expenseService.delete("exp-404");

        // Assert
        StepVerifier.create(result)
                .expectError(ResourceNotFoundException.class)
                .verify();
    }

    @Test
    @DisplayName("Should sum all expenses within selected period")
    void shouldSumExpensesInPeriod() {
        // Arrange
        final Instant start = Instant.parse("2026-10-01T00:00:00Z");
        final Instant end = Instant.parse("2026-10-31T23:59:59Z");
        final Expense e1 = Expense.builder().id("e-1").amount(new BigDecimal("50.00")).expenseDate(start).build();
        final Expense e2 = Expense.builder().id("e-2").amount(new BigDecimal("125.40")).expenseDate(end).build();

        when(mongoTemplate.find(any(Query.class), eq(Expense.class))).thenReturn(Flux.just(e1, e2));

        // Act
        final Mono<BigDecimal> result = expenseService.sumExpensesInPeriod(start, end);

        // Assert
        StepVerifier.create(result)
                .expectNextMatches(total -> total.compareTo(new BigDecimal("175.40")) == 0)
                .verifyComplete();
    }
}
