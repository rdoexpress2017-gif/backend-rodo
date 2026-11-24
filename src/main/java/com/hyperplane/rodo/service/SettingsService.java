package com.hyperplane.rodo.service;

import com.hyperplane.rodo.dto.SystemSettingsRequestDTO;
import com.hyperplane.rodo.dto.SystemSettingsResponseDTO;
import com.hyperplane.rodo.mapper.SystemSettingsMapper;
import com.hyperplane.rodo.model.SystemSettings;
import com.hyperplane.rodo.repository.SystemSettingsRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

@Service
@Slf4j
@RequiredArgsConstructor
public class SettingsService {

    private static final ZoneId SAO_PAULO_ZONE = ZoneId.of("America/Sao_Paulo");

    private final SystemSettingsRepository settingsRepository;
    private final SystemSettingsMapper settingsMapper;

    public Mono<SystemSettingsResponseDTO> getSettings() {
        final LocalDate today = LocalDate.now(SAO_PAULO_ZONE);
        return getOrCreateSettingsEntity()
                .flatMap(entity -> {
                    if (Boolean.TRUE.equals(entity.getDailyJobExecutedToday())
                            && (entity.getLastDailyJobExecutionDate() == null
                            || !entity.getLastDailyJobExecutionDate().equals(today))) {
                        return settingsRepository.save(entity.toBuilder()
                                .dailyJobExecutedToday(false)
                                .build());
                    }
                    return Mono.just(entity);
                })
                .map(settingsMapper::toResponseDTO);
    }

    public Mono<SystemSettingsResponseDTO> updateSettings(SystemSettingsRequestDTO requestDTO, String userId) {
        return getOrCreateSettingsEntity()
                .map(existing -> {
                    final String effectiveTime = (requestDTO.dailyJobExecutionTime() != null
                            && !requestDTO.dailyJobExecutionTime().isBlank())
                            ? requestDTO.dailyJobExecutionTime().trim()
                            : (existing.getDailyJobExecutionTime() != null ? existing.getDailyJobExecutionTime() : "20:00");
                    return existing.toBuilder()
                            .id(SystemSettings.GLOBAL_CONFIG_ID)
                            .pricePerKm(requestDTO.pricePerKm())
                            .baseRate(requestDTO.baseRate())
                            .defaultMotoboyCommissionPercentage(requestDTO.defaultMotoboyCommissionPercentage())
                            .dailyJobExecutionTime(effectiveTime)
                            .updatedByUserId(userId)
                            .updatedAt(Instant.now())
                            .build();
                })
                .flatMap(settingsRepository::save)
                .map(settingsMapper::toResponseDTO);
    }

    public Mono<Boolean> hasDailyJobExecutedOnDate(LocalDate referenceDate) {
        return getOrCreateSettingsEntity()
                .flatMap(entity -> {
                    final boolean executedOnRefDate = Boolean.TRUE.equals(entity.getDailyJobExecutedToday())
                            && (entity.getLastDailyJobExecutionDate() == null
                            || referenceDate.equals(entity.getLastDailyJobExecutionDate()));
                    if (Boolean.TRUE.equals(entity.getDailyJobExecutedToday())
                            && entity.getLastDailyJobExecutionDate() != null
                            && !referenceDate.equals(entity.getLastDailyJobExecutionDate())) {
                        return settingsRepository.save(entity.toBuilder()
                                        .dailyJobExecutedToday(false)
                                        .build())
                                .thenReturn(false);
                    }
                    return Mono.just(executedOnRefDate);
                });
    }

    public Mono<SystemSettingsResponseDTO> markDailyJobExecutedOnDate(LocalDate referenceDate) {
        return getOrCreateSettingsEntity()
                .map(existing -> existing.toBuilder()
                        .dailyJobExecutedToday(true)
                        .lastDailyJobExecutionDate(referenceDate)
                        .updatedAt(Instant.now())
                        .build())
                .flatMap(settingsRepository::save)
                .map(settingsMapper::toResponseDTO);
    }

    public Mono<Void> resetDailyJobExecutedFlag() {
        return getOrCreateSettingsEntity()
                .flatMap(existing -> settingsRepository.save(existing.toBuilder()
                        .dailyJobExecutedToday(false)
                        .updatedAt(Instant.now())
                        .build()))
                .then();
    }

    private Mono<SystemSettings> getOrCreateSettingsEntity() {
        return settingsRepository.findById(SystemSettings.GLOBAL_CONFIG_ID)
                .switchIfEmpty(Mono.defer(() -> settingsRepository.save(
                        SystemSettings.builder()
                                .id(SystemSettings.GLOBAL_CONFIG_ID)
                                .pricePerKm(new BigDecimal("2.50"))
                                .baseRate(new BigDecimal("10.00"))
                                .defaultMotoboyCommissionPercentage(80.0)
                                .dailyJobExecutedToday(false)
                                .dailyJobExecutionTime("20:00")
                                .updatedByUserId("SYSTEM")
                                .updatedAt(Instant.now())
                                .build()
                )))
                .map(entity -> entity.getDailyJobExecutionTime() == null || entity.getDailyJobExecutionTime().isBlank()
                        ? entity.toBuilder().dailyJobExecutionTime("20:00").build()
                        : entity);
    }
}
