package com.hyperplane.rodo.scheduler;

import com.hyperplane.rodo.service.ReportService;
import com.hyperplane.rodo.service.SettingsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

@Component
@Slf4j
@RequiredArgsConstructor
public class DailyBillingJob {

    private static final ZoneId SAO_PAULO_ZONE = ZoneId.of("America/Sao_Paulo");
    private static final DateTimeFormatter HH_MM_FMT = DateTimeFormatter.ofPattern("HH:mm");

    private final ReportService reportService;
    private final SettingsService settingsService;

    @Scheduled(cron = "0 0 0 * * *", zone = "America/Sao_Paulo")
    public void resetDailyExecutionFlagAtMidnight() {
        log.info("Resetando flag daily_job_executed_today à meia-noite (00:00).");
        settingsService.resetDailyJobExecutedFlag()
                .doOnError(err -> log.error("Erro ao resetar flag diária à meia-noite: {}", err.getMessage(), err))
                .subscribe();
    }

    @Scheduled(cron = "0 * * * * *", zone = "America/Sao_Paulo")
    public void checkAndExecuteAtConfiguredTime() {
        settingsService.getSettings()
                .filter(settings -> LocalTime.now(SAO_PAULO_ZONE).format(HH_MM_FMT).equals(
                        (settings.dailyJobExecutionTime() != null && !settings.dailyJobExecutionTime().isBlank())
                                ? settings.dailyJobExecutionTime().trim()
                                : "20:00"
                ))
                .doOnNext(settings -> executeDailyClosing())
                .subscribe();
    }

    public void executeDailyClosing() {
        reportService.processDailyBillingForDate(LocalDate.now(SAO_PAULO_ZONE))
                .doOnSubscribe(sub -> log.info("Iniciando robô de fechamento diário no horário configurado para clientes, motoboys e empresa na data: {}", LocalDate.now(SAO_PAULO_ZONE)))
                .doOnNext(report -> log.info("Fechamento gerado com sucesso: {} para entidade {}", report.reportCode(), report.targetEntityName()))
                .doOnError(err -> log.error("Erro durante fechamento diário: {}", err.getMessage(), err))
                .subscribe();
    }
}
