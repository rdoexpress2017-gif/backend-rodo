package com.hyperplane.rodo.controller;

import com.hyperplane.rodo.dto.CustomReportRequestDTO;
import com.hyperplane.rodo.dto.ReportDetailResponseDTO;
import com.hyperplane.rodo.dto.ReportResponseDTO;
import com.hyperplane.rodo.model.enums.ReportType;
import com.hyperplane.rodo.service.PdfExportService;
import com.hyperplane.rodo.service.ReportService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDate;
import java.time.ZoneId;

@RestController
@RequestMapping("/api/v1/reports")
@RequiredArgsConstructor
public class ReportController {

    private final ReportService reportService;
    private final PdfExportService pdfExportService;

    @GetMapping
    public Flux<ReportResponseDTO> getAll(
            @RequestParam(required = false) ReportType type,
            @RequestParam(required = false) String targetEntityId
    ) {
        return reportService.findAll(type, targetEntityId);
    }

    @GetMapping("/{id}")
    public Mono<ReportDetailResponseDTO> getReportDetail(@PathVariable String id) {
        return reportService.findReportDetail(id);
    }

    @GetMapping(value = "/{id}/pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    public Mono<ResponseEntity<byte[]>> downloadReportPdf(@PathVariable String id) {
        return pdfExportService.generateReportPdf(id)
                .map(bytes -> ResponseEntity.ok()
                        .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=relatorio-" + id + ".pdf")
                        .contentType(MediaType.APPLICATION_PDF)
                        .body(bytes));
    }

    @PostMapping("/custom")
    @ResponseStatus(HttpStatus.CREATED)
    public Mono<ReportResponseDTO> generateCustomReport(@RequestBody @Valid CustomReportRequestDTO request) {
        return ReactiveSecurityContextHolder.getContext()
                .map(SecurityContext::getAuthentication)
                .map(Authentication::getName)
                .defaultIfEmpty("SYSTEM")
                .flatMap(userId -> reportService.generateCustomReport(request, userId));
    }

    @PostMapping("/run-daily-job")
    public Flux<ReportResponseDTO> triggerDailyBillingManually() {
        return reportService.triggerManualDailyBilling(LocalDate.now(ZoneId.of("America/Sao_Paulo")));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public Mono<Void> deleteReport(@PathVariable String id) {
        return reportService.deleteReport(id);
    }
}
