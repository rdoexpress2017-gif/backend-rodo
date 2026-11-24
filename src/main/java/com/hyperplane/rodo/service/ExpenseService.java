package com.hyperplane.rodo.service;

import com.hyperplane.rodo.dto.ExpenseRequestDTO;
import com.hyperplane.rodo.dto.ExpenseResponseDTO;
import com.hyperplane.rodo.exception.ResourceNotFoundException;
import com.hyperplane.rodo.mapper.ExpenseMapper;
import com.hyperplane.rodo.model.Expense;
import com.hyperplane.rodo.repository.ExpenseRepository;
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
import java.time.Instant;
import java.util.Objects;

import static reactor.core.publisher.Mono.just;
import static reactor.core.publisher.Mono.justOrEmpty;

@Service
@Slf4j
@RequiredArgsConstructor
public class ExpenseService {

    private final ExpenseRepository expenseRepository;
    private final ExpenseMapper expenseMapper;
    private final ReactiveMongoTemplate mongoTemplate;

    public Flux<ExpenseResponseDTO> findAll(Instant startDate, Instant endDate) {
        final Query query = buildPeriodQuery(startDate, endDate)
                .with(Sort.by(Sort.Direction.DESC, "expenseDate", "createdAt"));
        return mongoTemplate.find(query, Expense.class)
                .map(expenseMapper::toResponseDTO);
    }

    public Mono<ExpenseResponseDTO> findById(String id) {
        return justOrEmpty(id)
                .flatMap(expenseRepository::findById)
                .switchIfEmpty(Mono.error(new ResourceNotFoundException("Lançamento não encontrado: " + id)))
                .map(expenseMapper::toResponseDTO);
    }

    public Mono<ExpenseResponseDTO> create(ExpenseRequestDTO requestDTO) {
        return just(requestDTO)
                .map(expenseMapper::toEntity)
                .flatMap(expenseRepository::save)
                .map(expenseMapper::toResponseDTO)
                .doOnSuccess(saved -> log.info("Lançamento criado com sucesso: {}", saved.id()));
    }

    public Mono<ExpenseResponseDTO> update(String id, ExpenseRequestDTO requestDTO) {
        return expenseRepository.findById(id)
                .switchIfEmpty(Mono.error(new ResourceNotFoundException("Lançamento não encontrado: " + id)))
                .map(existing -> existing.toBuilder()
                        .expenseDate(requestDTO.expenseDate())
                        .amount(requestDTO.amount())
                        .description(requestDTO.description())
                        .updatedAt(Instant.now())
                        .build())
                .flatMap(expenseRepository::save)
                .map(expenseMapper::toResponseDTO);
    }

    public Mono<Void> delete(String id) {
        return expenseRepository.findById(id)
                .switchIfEmpty(Mono.error(new ResourceNotFoundException("Lançamento não encontrado: " + id)))
                .flatMap(expenseRepository::delete);
    }

    public Mono<BigDecimal> sumExpensesInPeriod(Instant startDate, Instant endDate) {
        final Query query = buildPeriodQuery(startDate, endDate);
        return mongoTemplate.find(query, Expense.class)
                .map(Expense::getAmount)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .defaultIfEmpty(BigDecimal.ZERO);
    }

    private Query buildPeriodQuery(Instant startDate, Instant endDate) {
        final Query query = new Query();
        if (startDate != null && endDate != null) {
            query.addCriteria(Criteria.where("expenseDate").gte(startDate).lte(endDate));
        } else if (startDate != null) {
            query.addCriteria(Criteria.where("expenseDate").gte(startDate));
        } else if (endDate != null) {
            query.addCriteria(Criteria.where("expenseDate").lte(endDate));
        }
        return query;
    }
}
