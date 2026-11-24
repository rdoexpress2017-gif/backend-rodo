package com.hyperplane.rodo.controller;

import com.hyperplane.rodo.dto.ExpenseRequestDTO;
import com.hyperplane.rodo.dto.ExpenseResponseDTO;
import com.hyperplane.rodo.service.ExpenseService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;

@RestController
@RequestMapping("/api/v1/expenses")
@RequiredArgsConstructor
public class ExpenseController {

    private final ExpenseService expenseService;

    @GetMapping
    public Flux<ExpenseResponseDTO> getAll(
            @RequestParam(required = false) Instant startDate,
            @RequestParam(required = false) Instant endDate
    ) {
        return expenseService.findAll(startDate, endDate);
    }

    @GetMapping("/{id}")
    public Mono<ExpenseResponseDTO> getById(@PathVariable String id) {
        return expenseService.findById(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Mono<ExpenseResponseDTO> create(@RequestBody @Valid ExpenseRequestDTO request) {
        return expenseService.create(request);
    }

    @PutMapping("/{id}")
    public Mono<ExpenseResponseDTO> update(
            @PathVariable String id,
            @RequestBody @Valid ExpenseRequestDTO request
    ) {
        return expenseService.update(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public Mono<Void> delete(@PathVariable String id) {
        return expenseService.delete(id);
    }
}
