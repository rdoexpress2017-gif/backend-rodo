package com.hyperplane.rodo.controller;

import com.hyperplane.rodo.dto.FinancialSummaryDTO;
import com.hyperplane.rodo.dto.OrderRequestDTO;
import com.hyperplane.rodo.dto.OrderResponseDTO;
import com.hyperplane.rodo.dto.OrderStatusUpdateDTO;
import com.hyperplane.rodo.dto.PagedResponseDTO;
import com.hyperplane.rodo.model.enums.OrderStatus;
import com.hyperplane.rodo.service.OrderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
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
import java.util.List;

@RestController
@RequestMapping("/api/v1/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;

    @GetMapping
    public Mono<PagedResponseDTO<OrderResponseDTO>> getPaginatedOrders(
            @RequestParam(required = false) Instant startDate,
            @RequestParam(required = false) Instant endDate,
            @RequestParam(required = false) String clientId,
            @RequestParam(required = false) String motoboyId,
            @RequestParam(required = false) OrderStatus status,
            @RequestParam(required = false) String createdByUserId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return orderService.findPaginated(startDate, endDate, clientId, motoboyId, status, createdByUserId, page, size);
    }

    @GetMapping("/by-ids")
    public Flux<OrderResponseDTO> getOrdersByIds(@RequestParam List<String> ids) {
        return orderService.findByIds(ids);
    }

    @GetMapping("/list")
    public Flux<OrderResponseDTO> getFilteredOrdersList(
            @RequestParam(required = false) Instant startDate,
            @RequestParam(required = false) Instant endDate,
            @RequestParam(required = false) String clientId,
            @RequestParam(required = false) String motoboyId,
            @RequestParam(required = false) OrderStatus status,
            @RequestParam(required = false) String createdByUserId
    ) {
        return orderService.findFilteredList(startDate, endDate, clientId, motoboyId, status, createdByUserId);
    }

    @GetMapping("/summary")
    public Mono<FinancialSummaryDTO> getFinancialSummary(
            @RequestParam(required = false) Instant startDate,
            @RequestParam(required = false) Instant endDate
    ) {
        return orderService.getFinancialSummary(startDate, endDate);
    }

    @GetMapping("/{id}")
    public Mono<OrderResponseDTO> getById(@PathVariable String id) {
        return orderService.findById(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Mono<OrderResponseDTO> create(@RequestBody @Valid OrderRequestDTO request) {
        return ReactiveSecurityContextHolder.getContext()
                .map(SecurityContext::getAuthentication)
                .map(Authentication::getName)
                .defaultIfEmpty("SYSTEM")
                .flatMap(userId -> orderService.create(request, userId));
    }

    @PutMapping("/{id}")
    public Mono<OrderResponseDTO> update(@PathVariable String id, @RequestBody @Valid OrderRequestDTO request) {
        return orderService.update(id, request);
    }

    @PatchMapping("/{id}/status")
    public Mono<OrderResponseDTO> updateStatus(@PathVariable String id, @RequestBody @Valid OrderStatusUpdateDTO request) {
        return orderService.updateStatus(id, request);
    }
}
