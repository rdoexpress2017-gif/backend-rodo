package com.hyperplane.rodo.controller;

import com.hyperplane.rodo.dto.AuthLoginRequestDTO;
import com.hyperplane.rodo.dto.AuthLoginResponseDTO;
import com.hyperplane.rodo.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/login")
    public Mono<AuthLoginResponseDTO> login(@RequestBody @Valid AuthLoginRequestDTO request) {
        return authService.login(request);
    }
}
