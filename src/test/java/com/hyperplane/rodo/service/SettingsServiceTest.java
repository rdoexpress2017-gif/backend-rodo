package com.hyperplane.rodo.service;

import com.hyperplane.rodo.dto.SystemSettingsRequestDTO;
import com.hyperplane.rodo.dto.SystemSettingsResponseDTO;
import com.hyperplane.rodo.mapper.SystemSettingsMapper;
import com.hyperplane.rodo.model.SystemSettings;
import com.hyperplane.rodo.repository.SystemSettingsRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SettingsServiceTest {

    @Mock
    private SystemSettingsRepository settingsRepository;

    @Mock
    private SystemSettingsMapper settingsMapper;

    @InjectMocks
    private SettingsService settingsService;

    @Test
    @DisplayName("Should return default global settings when database has none")
    void shouldReturnDefaultSettingsWhenEmpty() {
        // Arrange
        when(settingsRepository.findById(SystemSettings.GLOBAL_CONFIG_ID)).thenReturn(Mono.empty());
        when(settingsRepository.save(any(SystemSettings.class))).thenAnswer(inv -> Mono.just(inv.getArgument(0)));
        when(settingsMapper.toResponseDTO(any(SystemSettings.class))).thenAnswer(inv -> {
            final SystemSettings s = inv.getArgument(0);
            return new SystemSettingsResponseDTO(
                    s.getId(), s.getPricePerKm(), s.getBaseRate(), s.getDefaultMotoboyCommissionPercentage(),
                    s.getUpdatedByUserId(), s.getUpdatedAt(), s.getDailyJobExecutedToday(), s.getLastDailyJobExecutionDate(),
                    s.getDailyJobExecutionTime()
            );
        });

        // Act
        final Mono<SystemSettingsResponseDTO> result = settingsService.getSettings();

        // Assert
        StepVerifier.create(result)
                .expectNextMatches(dto ->
                        dto.id().equals("GLOBAL_CONFIG")
                                && dto.defaultMotoboyCommissionPercentage().equals(80.0)
                                && Boolean.FALSE.equals(dto.dailyJobExecutedToday())
                                && "20:00".equals(dto.dailyJobExecutionTime())
                )
                .verifyComplete();
    }

    @Test
    @DisplayName("Should update global settings including dailyJobExecutionTime and record updatedByUserId while preserving dailyJobExecutedToday flag")
    void shouldUpdateSettings() {
        // Arrange
        final LocalDate today = LocalDate.of(2026, 11, 23);
        final SystemSettings existing = SystemSettings.builder()
                .id(SystemSettings.GLOBAL_CONFIG_ID)
                .pricePerKm(new BigDecimal("2.50"))
                .baseRate(new BigDecimal("10.00"))
                .defaultMotoboyCommissionPercentage(80.0)
                .dailyJobExecutedToday(true)
                .lastDailyJobExecutionDate(today)
                .dailyJobExecutionTime("20:00")
                .build();
        final SystemSettingsRequestDTO request = new SystemSettingsRequestDTO(
                new BigDecimal("3.00"), new BigDecimal("12.00"), 85.0, "21:30"
        );
        when(settingsRepository.findById(SystemSettings.GLOBAL_CONFIG_ID)).thenReturn(Mono.just(existing));
        when(settingsRepository.save(any(SystemSettings.class))).thenAnswer(inv -> Mono.just(inv.getArgument(0)));
        when(settingsMapper.toResponseDTO(any(SystemSettings.class))).thenAnswer(inv -> {
            final SystemSettings s = inv.getArgument(0);
            return new SystemSettingsResponseDTO(
                    s.getId(), s.getPricePerKm(), s.getBaseRate(), s.getDefaultMotoboyCommissionPercentage(),
                    s.getUpdatedByUserId(), s.getUpdatedAt(), s.getDailyJobExecutedToday(), s.getLastDailyJobExecutionDate(),
                    s.getDailyJobExecutionTime()
            );
        });

        // Act
        final Mono<SystemSettingsResponseDTO> result = settingsService.updateSettings(request, "master-1");

        // Assert
        StepVerifier.create(result)
                .expectNextMatches(dto ->
                        dto.defaultMotoboyCommissionPercentage().equals(85.0)
                                && dto.updatedByUserId().equals("master-1")
                                && Boolean.TRUE.equals(dto.dailyJobExecutedToday())
                                && today.equals(dto.lastDailyJobExecutionDate())
                                && "21:30".equals(dto.dailyJobExecutionTime())
                )
                .verifyComplete();
    }

    @Test
    @DisplayName("Should mark dailyJobExecutedToday flag true on execution date and automatically reset to false on next day")
    void shouldMarkAndCheckDailyJobExecutedFlagByDate() {
        final LocalDate today = LocalDate.of(2026, 11, 23);
        final LocalDate tomorrow = today.plusDays(1);

        final SystemSettings markedEntity = SystemSettings.builder()
                .id(SystemSettings.GLOBAL_CONFIG_ID)
                .dailyJobExecutedToday(true)
                .lastDailyJobExecutionDate(today)
                .build();

        when(settingsRepository.findById(SystemSettings.GLOBAL_CONFIG_ID)).thenReturn(Mono.just(markedEntity));
        when(settingsRepository.save(any(SystemSettings.class))).thenAnswer(inv -> Mono.just(inv.getArgument(0)));

        // Same day -> returns true
        StepVerifier.create(settingsService.hasDailyJobExecutedOnDate(today))
                .expectNext(true)
                .verifyComplete();

        // Next day -> automatically resets flag to false and returns false
        StepVerifier.create(settingsService.hasDailyJobExecutedOnDate(tomorrow))
                .expectNext(false)
                .verifyComplete();
    }
}
