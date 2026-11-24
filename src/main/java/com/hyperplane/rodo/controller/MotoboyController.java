package com.hyperplane.rodo.controller;

import com.hyperplane.rodo.dto.MotoboyRequestDTO;
import com.hyperplane.rodo.dto.MotoboyResponseDTO;
import com.hyperplane.rodo.service.MotoboyService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/v1/motoboys")
@RequiredArgsConstructor
public class MotoboyController {

    private final MotoboyService motoboyService;

    @GetMapping
    public Flux<MotoboyResponseDTO> getAll() {
        return motoboyService.findAll();
    }

    @GetMapping("/{id}")
    public Mono<MotoboyResponseDTO> getById(@PathVariable String id) {
        return motoboyService.findById(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Mono<MotoboyResponseDTO> create(@RequestBody @Valid MotoboyRequestDTO request) {
        return motoboyService.create(request);
    }

    @PutMapping("/{id}")
    public Mono<MotoboyResponseDTO> update(@PathVariable String id, @RequestBody @Valid MotoboyRequestDTO request) {
        return motoboyService.update(id, request);
    }
}
