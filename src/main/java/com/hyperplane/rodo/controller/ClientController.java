package com.hyperplane.rodo.controller;

import com.hyperplane.rodo.dto.ClientRequestDTO;
import com.hyperplane.rodo.dto.ClientResponseDTO;
import com.hyperplane.rodo.service.ClientService;
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
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/v1/clients")
@RequiredArgsConstructor
public class ClientController {

    private final ClientService clientService;

    @GetMapping
    public Flux<ClientResponseDTO> getAll() {
        return clientService.findAll();
    }

    @GetMapping("/{id}")
    public Mono<ClientResponseDTO> getById(@PathVariable String id) {
        return clientService.findById(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Mono<ClientResponseDTO> create(@RequestBody @Valid ClientRequestDTO request) {
        return clientService.create(request);
    }

    @PutMapping("/{id}")
    public Mono<ClientResponseDTO> update(@PathVariable String id, @RequestBody @Valid ClientRequestDTO request) {
        return clientService.update(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public Mono<Void> delete(@PathVariable String id) {
        return clientService.delete(id);
    }
}
