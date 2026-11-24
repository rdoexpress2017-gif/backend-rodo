package com.hyperplane.rodo.controller;

import com.hyperplane.rodo.dto.UserRequestDTO;
import com.hyperplane.rodo.dto.UserResponseDTO;
import com.hyperplane.rodo.service.UserService;
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
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @GetMapping
    public Flux<UserResponseDTO> getAll() {
        return userService.findAll();
    }

    @GetMapping("/{id}")
    public Mono<UserResponseDTO> getById(@PathVariable String id) {
        return userService.findById(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Mono<UserResponseDTO> create(@RequestBody @Valid UserRequestDTO request) {
        return userService.create(request);
    }

    @PutMapping("/{id}")
    public Mono<UserResponseDTO> update(@PathVariable String id, @RequestBody @Valid UserRequestDTO request) {
        return userService.update(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public Mono<Void> delete(@PathVariable String id) {
        return userService.delete(id);
    }
}
