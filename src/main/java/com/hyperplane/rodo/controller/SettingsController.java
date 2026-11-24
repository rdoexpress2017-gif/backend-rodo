package com.hyperplane.rodo.controller;

import com.hyperplane.rodo.dto.SystemSettingsRequestDTO;
import com.hyperplane.rodo.dto.SystemSettingsResponseDTO;
import com.hyperplane.rodo.service.SettingsService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/v1/settings")
@RequiredArgsConstructor
public class SettingsController {

    private final SettingsService settingsService;

    @GetMapping
    public Mono<SystemSettingsResponseDTO> getSettings() {
        return settingsService.getSettings();
    }

    @PutMapping
    public Mono<SystemSettingsResponseDTO> updateSettings(@RequestBody @Valid SystemSettingsRequestDTO request) {
        return ReactiveSecurityContextHolder.getContext()
                .map(SecurityContext::getAuthentication)
                .map(Authentication::getName)
                .defaultIfEmpty("MASTER")
                .flatMap(userId -> settingsService.updateSettings(request, userId));
    }
}
