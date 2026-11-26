package com.hyperplane.rodo.service;

import com.hyperplane.rodo.dto.AddressDTO;
import com.hyperplane.rodo.dto.CompanyReportSummaryDTO;
import com.hyperplane.rodo.dto.MotoboyReportAggregateDTO;
import com.hyperplane.rodo.dto.OrderResponseDTO;
import com.hyperplane.rodo.dto.ReportDetailResponseDTO;
import com.hyperplane.rodo.dto.ReportResponseDTO;
import com.hyperplane.rodo.dto.SystemSettingsResponseDTO;
import com.hyperplane.rodo.model.enums.OrderStatus;
import com.hyperplane.rodo.model.enums.ReportType;
import com.lowagie.text.pdf.PdfReader;
import com.lowagie.text.pdf.parser.PdfTextExtractor;
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
import java.util.List;

import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PdfExportServiceTest {

    @Mock
    private ReportService reportService;

    @Mock
    private SettingsService settingsService;

    @InjectMocks
    private PdfExportService pdfExportService;

    @Test
    @DisplayName("Should generate EMPRESA (RDO) PDF containing LUCRO ESTIMADO and include Lucro Estimado in daily email body")
    void shouldGenerateEmpresaPdfAndEmailWithLucroEstimado() {
        final CompanyReportSummaryDTO companySummary = new CompanyReportSummaryDTO(
                new BigDecimal("4435.00"),
                new BigDecimal("4035.00"),
                new BigDecimal("400.00"),
                new BigDecimal("477.00"),
                new BigDecimal("557.00"),
                new BigDecimal("3548.00"),
                new BigDecimal("3228.00"),
                new BigDecimal("330.00"),
                new BigDecimal("3548.00"),
                136L,
                125L,
                11L,
                List.of(
                        new MotoboyReportAggregateDTO(
                                "m-1",
                                "Carlos Eduardo Silva",
                                "FIXO",
                                56,
                                450.0,
                                new BigDecimal("1935.00"),
                                new BigDecimal("1548.00")
                        ),
                        new MotoboyReportAggregateDTO(
                                "m-2",
                                "Marcos Vinícius Souza",
                                "TERCEIRIZADO",
                                80,
                                600.0,
                                new BigDecimal("2500.00"),
                                new BigDecimal("2000.00")
                        )
                ),
                new BigDecimal("3548.00"),
                true,
                "admin@rodo.com"
        );

        final ReportResponseDTO report = new ReportResponseDTO(
                "rep-emp-1",
                "REL-202611-RDO1",
                ReportType.EMPRESA,
                "RDO_EXPRESS",
                "RDO Express (Resumo Geral)",
                "EMPRESA",
                List.of("ord-1"),
                Instant.parse("2026-11-23T03:00:00Z"),
                Instant.parse("2026-11-24T02:59:59Z"),
                136,
                new BigDecimal("4435.00"),
                1050.0,
                null,
                null,
                "SYSTEM_JOB",
                "Robô Automático",
                Instant.parse("2026-11-23T23:00:00Z"),
                Instant.parse("2026-11-23T23:00:00Z"),
                companySummary
        );

        final OrderResponseDTO order1 = new OrderResponseDTO(
                "ord-1",
                1001L,
                "c-1",
                "INCLOSET",
                "m-1",
                "Carlos Eduardo Silva",
                "Juliana",
                "u-1",
                "Renata",
                new AddressDTO("Rua A", "10", "Centro", "Belo Horizonte", "MG", "30110-000", 0.0, 0.0),
                new AddressDTO("Rua B", "20", "Savassi", "Belo Horizonte", "MG", "30110-000", 0.0, 0.0),
                10.0,
                new BigDecimal("50.00"),
                new BigDecimal("35.00"),
                OrderStatus.ENTREGUE,
                null,
                Instant.parse("2026-11-23T14:00:00Z"),
                Instant.parse("2026-11-23T14:10:00Z"),
                Instant.parse("2026-11-23T14:30:00Z"),
                null,
                Instant.parse("2026-11-23T14:30:00Z")
        );

        when(reportService.findReportDetail("rep-emp-1"))
                .thenReturn(Mono.just(new ReportDetailResponseDTO(report, List.of(order1))));
        when(settingsService.getSettings())
                .thenReturn(Mono.just(new SystemSettingsResponseDTO(
                        "GLOBAL_CONFIG",
                        new BigDecimal("2.50"),
                        new BigDecimal("10.00"),
                        70.0,
                        "admin",
                        Instant.now(),
                        false,
                        null,
                        "20:00"
                )));

        StepVerifier.create(pdfExportService.generateReportPdf("rep-emp-1"))
                .expectNextMatches(pdfBytes -> {
                    try {
                        final PdfReader reader = new PdfReader(pdfBytes);
                        final PdfTextExtractor extractor = new PdfTextExtractor(reader);
                        final String pageText = extractor.getTextFromPage(1);
                        reader.close();

                        final EmailNotificationService emailSvc = new EmailNotificationService(null);
                        final String emailBody = emailSvc.buildCompanyReportEmailBody(report);

                        return pageText.contains("LUCRO ESTIMADO")
                                && pageText.contains("557,00")
                                && pageText.contains("477,00")
                                && emailBody.contains("Lucro Estimado")
                                && emailBody.contains("557,00");
                    } catch (Exception e) {
                        return false;
                    }
                })
                .verifyComplete();
    }
}
