package com.hyperplane.rodo.controller;

import com.hyperplane.rodo.dto.PaymentOrderResponseDTO;
import com.hyperplane.rodo.model.enums.PaymentOrderStatus;
import com.hyperplane.rodo.service.PaymentOrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/v1/payment-orders")
@RequiredArgsConstructor
public class PaymentOrderController {

    private final PaymentOrderService paymentOrderService;

    @GetMapping
    public Flux<PaymentOrderResponseDTO> getAll(
            @RequestParam(required = false) String clientId,
            @RequestParam(required = false) PaymentOrderStatus status
    ) {
        return paymentOrderService.findAll(clientId, status);
    }

    @GetMapping("/{id}")
    public Mono<PaymentOrderResponseDTO> getById(@PathVariable String id) {
        return paymentOrderService.findById(id);
    }

    @PatchMapping("/{id}/settle")
    public Mono<PaymentOrderResponseDTO> settle(@PathVariable String id) {
        return paymentOrderService.settle(id);
    }
}
