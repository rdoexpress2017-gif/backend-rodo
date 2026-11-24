package com.hyperplane.rodo.service;

import com.hyperplane.rodo.dto.CompanyReportSummaryDTO;
import com.hyperplane.rodo.dto.MotoboyReportAggregateDTO;
import com.hyperplane.rodo.dto.OrderResponseDTO;
import com.hyperplane.rodo.dto.ReportDetailResponseDTO;
import com.hyperplane.rodo.dto.ReportResponseDTO;
import com.hyperplane.rodo.model.enums.ReportType;
import com.lowagie.text.Document;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import io.vavr.control.Try;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class PdfExportService {

    private static final ZoneId SAO_PAULO_ZONE = ZoneId.of("America/Sao_Paulo");
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy").withZone(SAO_PAULO_ZONE);
    private static final DateTimeFormatter DATE_TIME_FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy, HH:mm").withZone(SAO_PAULO_ZONE);
    private static final Locale PT_BR = Locale.of("pt", "BR");

    // Executive Color Palette
    private static final Color NAVY_BG = new Color(22, 29, 47);
    private static final Color BRAND_RED = new Color(217, 45, 58);
    private static final Color SLATE_900 = new Color(15, 23, 42);
    private static final Color SLATE_700 = new Color(51, 65, 85);
    private static final Color SLATE_500 = new Color(100, 116, 139);
    private static final Color SLATE_HEADER_BG = new Color(241, 245, 249);
    private static final Color ZEBRA_BG = new Color(248, 250, 252);
    private static final Color BORDER_LIGHT = new Color(203, 213, 225);

    private static final Color GREEN_TEXT = new Color(6, 95, 70);
    private static final Color GREEN_BOX_BG = new Color(236, 253, 245);
    private static final Color GREEN_BORDER = new Color(167, 243, 208);

    private static final Color AMBER_TEXT = new Color(146, 64, 14);
    private static final Color AMBER_BG = new Color(255, 251, 235);
    private static final Color AMBER_BORDER = new Color(253, 230, 138);

    private static final Color BLUE_TEXT = new Color(29, 78, 216);
    private static final Color BLUE_BOX_BG = new Color(239, 246, 255);
    private static final Color BLUE_BORDER = new Color(191, 219, 254);

    private static final Color PURPLE_TEXT = new Color(107, 33, 168);
    private static final Color PURPLE_BOX_BG = new Color(250, 245, 255);
    private static final Color PURPLE_BORDER = new Color(233, 213, 255);

    private static final Color ROSE_TEXT = new Color(159, 18, 57);
    private static final Color ROSE_BOX_BG = new Color(255, 241, 242);
    private static final Color ROSE_BORDER = new Color(254, 205, 211);

    private final ReportService reportService;
    private final SettingsService settingsService;

    public Mono<byte[]> generateReportPdf(String reportId) {
        return Mono.zip(
                        reportService.findReportDetail(reportId),
                        settingsService.getSettings()
                )
                .flatMap(tuple -> {
                    final ReportDetailResponseDTO detail = tuple.getT1();
                    final Double configuredPct = tuple.getT2().defaultMotoboyCommissionPercentage();
                    final double motoboyPct = configuredPct != null ? configuredPct : 70.0;
                    return Mono.fromCallable(() -> renderPdfBytes(detail, motoboyPct))
                            .subscribeOn(Schedulers.boundedElastic());
                });
    }

    private String formatPercentage(double pct) {
        if (pct == Math.floor(pct)) {
            return String.format("%d%%", Math.round(pct));
        }
        return String.format(PT_BR, "%.1f%%", pct);
    }

    private byte[] renderPdfBytes(ReportDetailResponseDTO detail, double motoboyPct) {
        return Try.of(() -> {
            final ByteArrayOutputStream out = new ByteArrayOutputStream();
            final Document document = new Document(PageSize.A4, 26, 26, 26, 26);
            PdfWriter.getInstance(document, out);
            document.open();

            final ReportResponseDTO report = detail.report();
            final List<OrderResponseDTO> orders = detail.orders() != null ? detail.orders() : List.of();
            final NumberFormat currencyFmt = NumberFormat.getCurrencyInstance(PT_BR);

            final String startStr = report.periodStart() != null ? DATE_FMT.format(report.periodStart()) : "-";
            final String endStr = report.periodEnd() != null ? DATE_FMT.format(report.periodEnd()) : "-";
            final String entityUpper = (report.targetEntityName() != null ? report.targetEntityName() : "RELATÓRIO").toUpperCase();

            // 1. Executive Dark Navy Header Banner with Brand Accent
            addTopBanner(document, report, entityUpper, startStr, endStr);

            // 2. Section "Informações do Relatório" (4 separated cards with gaps)
            addInformationsSection(document, report, startStr, endStr);

            if (report.type() == ReportType.EMPRESA && report.companySummary() != null) {
                // 3A. Company RDO Report: 8 separated KPI cards + Motoboy Aggregate Table + Orders
                addCompanyResumoGeralSection(document, report.companySummary(), currencyFmt);
                addOrdersGroupedByDateSection(document, orders, currencyFmt);
            } else {
                // 3B. Client or Motoboy Report: Entity Identification + Orders + Division Summary
                addEntityUsernameSection(document, report);
                addOrdersGroupedByDateSection(document, orders, currencyFmt);
                addSummarySection(document, report, orders, currencyFmt, motoboyPct);
            }

            document.close();
            return out.toByteArray();
        }).getOrElseThrow(ex -> new IllegalStateException("Falha ao gerar PDF do relatório: " + ex.getMessage(), ex));
    }

    private void addTopBanner(
            Document document,
            ReportResponseDTO report,
            String entityUpper,
            String startStr,
            String endStr
    ) throws Exception {
        final PdfPTable banner = new PdfPTable(new float[]{2.8f, 1.2f});
        banner.setWidthPercentage(100);
        banner.setSpacingAfter(16f);

        final Font brandTagFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 7.5f, new Color(248, 113, 113));
        final Font bannerTitleFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 13f, Color.WHITE);
        final Font bannerSubFont = FontFactory.getFont(FontFactory.HELVETICA, 8.5f, new Color(203, 213, 225));
        final Font codeFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 8.5f, Color.WHITE);

        final Paragraph leftP = new Paragraph();
        leftP.setLeading(14f);
        leftP.add(new Phrase("RDO EXPRESS • DEMONSTRATIVO OFICIAL\n", brandTagFont));
        leftP.add(new Phrase(String.format("%s\n", entityUpper), bannerTitleFont));
        leftP.add(new Phrase(String.format("Período de Apuração: %s a %s", startStr, endStr), bannerSubFont));

        final PdfPCell leftCell = new PdfPCell(leftP);
        leftCell.setBackgroundColor(NAVY_BG);
        leftCell.setBorder(Rectangle.BOTTOM);
        leftCell.setBorderColorBottom(BRAND_RED);
        leftCell.setBorderWidthBottom(3f);
        leftCell.setPadding(15f);
        banner.addCell(leftCell);

        final Paragraph rightP = new Paragraph();
        rightP.setLeading(13f);
        rightP.add(new Phrase("CÓDIGO DO RELATÓRIO\n", bannerSubFont));
        rightP.add(new Phrase((report.reportCode() != null ? report.reportCode() : "-") + "\n", codeFont));
        rightP.add(new Phrase(
                report.createdAt() != null ? "Emissão: " + DATE_TIME_FMT.format(report.createdAt()) : "",
                bannerSubFont
        ));

        final PdfPCell rightCell = new PdfPCell(rightP);
        rightCell.setBackgroundColor(NAVY_BG);
        rightCell.setHorizontalAlignment(Element.ALIGN_RIGHT);
        rightCell.setVerticalAlignment(Element.ALIGN_MIDDLE);
        rightCell.setBorder(Rectangle.BOTTOM);
        rightCell.setBorderColorBottom(BRAND_RED);
        rightCell.setBorderWidthBottom(3f);
        rightCell.setPadding(15f);
        banner.addCell(rightCell);

        document.add(banner);
    }

    private void addInformationsSection(
            Document document,
            ReportResponseDTO report,
            String startStr,
            String endStr
    ) throws Exception {
        final Font sectionFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9.5f, NAVY_BG);
        final Paragraph infoTitle = new Paragraph("INFORMAÇÕES DO RELATÓRIO", sectionFont);
        infoTitle.setSpacingAfter(6f);
        document.add(infoTitle);

        // 4 cards with 3 spacer columns between them
        final PdfPTable infoGrid = new PdfPTable(new float[]{1f, 0.03f, 1.15f, 0.03f, 1f, 0.03f, 0.95f});
        infoGrid.setWidthPercentage(100);
        infoGrid.setSpacingAfter(16f);

        final String typeLabel = report.type() == ReportType.CLIENTE ? "Cliente"
                : (report.type() == ReportType.MOTOBOY ? "Motoboy"
                : (report.type() == ReportType.EMPRESA ? "Empresa (RDO)" : "Master"));
        final String generatedBy = report.generatedByUserName() != null ? report.generatedByUserName() : "Sistema";
        final String createdTime = report.createdAt() != null ? DATE_TIME_FMT.format(report.createdAt()) : "-";

        addStackedMetadataCard(infoGrid, "CATEGORIA", typeLabel);
        addSpacerCell(infoGrid);
        addStackedMetadataCard(infoGrid, "PERÍODO APURADO", startStr + " a " + endStr);
        addSpacerCell(infoGrid);
        addStackedMetadataCard(infoGrid, "GERADO POR", generatedBy);
        addSpacerCell(infoGrid);
        addStackedMetadataCard(infoGrid, "DATA / HORA", createdTime);

        document.add(infoGrid);
    }

    private void addStackedMetadataCard(PdfPTable grid, String label, String value) {
        final Font lblFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 7f, SLATE_500);
        final Font valFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9f, SLATE_900);

        final Paragraph p = new Paragraph();
        p.setLeading(12f);
        p.add(new Phrase(label + "\n", lblFont));
        p.add(new Phrase(value, valFont));

        final PdfPCell cell = new PdfPCell(p);
        cell.setBackgroundColor(ZEBRA_BG);
        cell.setBorderColor(BORDER_LIGHT);
        cell.setBorderWidth(0.8f);
        cell.setPadding(9f);
        grid.addCell(cell);
    }

    private void addSpacerCell(PdfPTable grid) {
        final PdfPCell spacer = new PdfPCell(new Phrase(""));
        spacer.setBorder(Rectangle.NO_BORDER);
        grid.addCell(spacer);
    }

    private void addCompanyResumoGeralSection(
            Document document,
            CompanyReportSummaryDTO s,
            NumberFormat currencyFmt
    ) throws Exception {
        final Font sectionFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9.5f, NAVY_BG);
        final Paragraph title = new Paragraph("RESUMO GERAL (FINANCEIRO RDO)", sectionFont);
        title.setSpacingAfter(7f);
        document.add(title);

        final float[] fourCardsWidths = new float[]{1f, 0.03f, 1f, 0.03f, 1f, 0.03f, 1f};

        // Row 1 of KPI Cards: RECEITAS | JÁ RECEBIDO | A RECEBER | LUCRO
        final PdfPTable row1 = new PdfPTable(fourCardsWidths);
        row1.setWidthPercentage(100);
        row1.setSpacingAfter(7f);

        addKpiCard(
                row1,
                "RECEITAS",
                currencyFmt.format(s.totalRevenue()),
                String.format("Valor total de pedidos (%d)", s.totalOrders()),
                Color.WHITE,
                BORDER_LIGHT,
                SLATE_700,
                SLATE_900,
                SLATE_500
        );
        addSpacerCell(row1);
        addKpiCard(
                row1,
                "JÁ RECEBIDO",
                currencyFmt.format(s.paidRevenue()),
                String.format("%d pagos em caixa", s.paidOrdersCount()),
                GREEN_BOX_BG,
                GREEN_BORDER,
                GREEN_TEXT,
                GREEN_TEXT,
                GREEN_TEXT
        );
        addSpacerCell(row1);
        addKpiCard(
                row1,
                "A RECEBER",
                currencyFmt.format(s.pendingRevenue()),
                String.format("%d a receber (não pagos)", s.pendingOrdersCount()),
                AMBER_BG,
                AMBER_BORDER,
                AMBER_TEXT,
                AMBER_TEXT,
                AMBER_TEXT
        );
        addSpacerCell(row1);
        addKpiCard(
                row1,
                "LUCRO",
                currencyFmt.format(s.netProfit()),
                "Somente sobre pedidos pagos",
                ZEBRA_BG,
                BORDER_LIGHT,
                SLATE_700,
                SLATE_900,
                SLATE_500
        );
        document.add(row1);

        // Row 2 of KPI Cards: REPASSES | DESPESAS | COLABORADOR | REGISTROS
        final PdfPTable row2 = new PdfPTable(fourCardsWidths);
        row2.setWidthPercentage(100);
        row2.setSpacingAfter(18f);

        addKpiCard(
                row2,
                "REPASSES",
                currencyFmt.format(s.totalCommissions()),
                String.format("Pagos: %s", currencyFmt.format(s.paidCommissions())),
                ROSE_BOX_BG,
                ROSE_BORDER,
                ROSE_TEXT,
                SLATE_900,
                ROSE_TEXT
        );
        addSpacerCell(row2);
        addKpiCard(
                row2,
                "DESPESAS",
                currencyFmt.format(s.totalExpenses()),
                "Lançamentos do período",
                PURPLE_BOX_BG,
                PURPLE_BORDER,
                PURPLE_TEXT,
                PURPLE_TEXT,
                SLATE_500
        );
        addSpacerCell(row2);
        addKpiCard(
                row2,
                "COLABORADOR",
                currencyFmt.format(s.collaboratorShare()),
                "Comissão motoboys",
                BLUE_BOX_BG,
                BLUE_BORDER,
                BLUE_TEXT,
                BLUE_TEXT,
                SLATE_500
        );
        addSpacerCell(row2);
        addKpiCard(
                row2,
                "REGISTROS",
                String.valueOf(s.totalOrders()),
                String.format("%d pagos • %d pendentes", s.paidOrdersCount(), s.pendingOrdersCount()),
                ZEBRA_BG,
                BORDER_LIGHT,
                SLATE_700,
                AMBER_TEXT,
                SLATE_500
        );
        document.add(row2);

        // Motoboy Aggregate Table (Valores a Pagar no Dia)
        final Paragraph aggTitle = new Paragraph("AGREGADO POR MOTOBOY (VALORES A PAGAR NO DIA)", sectionFont);
        aggTitle.setSpacingAfter(7f);
        document.add(aggTitle);

        // Generous column widths so "TERCEIRIZADO" fits on a single line cleanly
        final PdfPTable aggTable = new PdfPTable(new float[]{2.3f, 1.45f, 1.05f, 1.1f, 1.4f, 1.5f});
        aggTable.setWidthPercentage(100);
        aggTable.setSpacingAfter(18f);

        final Font thFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 8f, Color.WHITE);
        final Font tdFont = FontFactory.getFont(FontFactory.HELVETICA, 8.5f, SLATE_900);
        final Font tdBoldFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 8.5f, SLATE_900);
        final Font greenValFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 8.5f, GREEN_TEXT);
        final Font totalLblFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 8.5f, GREEN_TEXT);
        final Font totalValFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9.5f, GREEN_TEXT);

        addNavyHeaderCell(aggTable, "MOTOBOY", thFont, Element.ALIGN_LEFT);
        addNavyHeaderCell(aggTable, "VÍNCULO", thFont, Element.ALIGN_CENTER);
        addNavyHeaderCell(aggTable, "QTD. PEDIDOS", thFont, Element.ALIGN_CENTER);
        addNavyHeaderCell(aggTable, "KM TOTAL", thFont, Element.ALIGN_RIGHT);
        addNavyHeaderCell(aggTable, "VALOR BRUTO", thFont, Element.ALIGN_RIGHT);
        addNavyHeaderCell(aggTable, "VALOR A PAGAR", thFont, Element.ALIGN_RIGHT);

        if (s.motoboyAggregates() != null) {
            int idx = 0;
            for (final MotoboyReportAggregateDTO agg : s.motoboyAggregates()) {
                final Color rowBg = (idx % 2 == 0) ? Color.WHITE : ZEBRA_BG;
                addStyledBodyCell(aggTable, agg.motoboyName(), tdBoldFont, rowBg, Element.ALIGN_LEFT);
                addStyledBodyCell(aggTable, agg.motoboyType(), tdFont, rowBg, Element.ALIGN_CENTER);
                addStyledBodyCell(aggTable, String.valueOf(agg.totalOrders()), tdBoldFont, rowBg, Element.ALIGN_CENTER);
                addStyledBodyCell(aggTable, String.format(PT_BR, "%.1f km", agg.totalDistanceKm()), tdFont, rowBg, Element.ALIGN_RIGHT);
                addStyledBodyCell(aggTable, currencyFmt.format(agg.grossAmount()), tdFont, rowBg, Element.ALIGN_RIGHT);
                addStyledBodyCell(aggTable, currencyFmt.format(agg.amountToPay()), greenValFont, rowBg, Element.ALIGN_RIGHT);
                idx++;
            }
        }

        // Footer Total Row with colspan = 2 for "VALOR FINAL DE TODOS OS MOTOBOYS JUNTOS NO DIA"
        final PdfPCell totalTitleCell = new PdfPCell(new Phrase("VALOR FINAL DE TODOS OS MOTOBOYS JUNTOS NO DIA", totalLblFont));
        totalTitleCell.setColspan(2);
        totalTitleCell.setBackgroundColor(GREEN_BOX_BG);
        totalTitleCell.setBorderColor(GREEN_BORDER);
        totalTitleCell.setPadding(8f);
        aggTable.addCell(totalTitleCell);

        addStyledBodyCell(aggTable, String.valueOf(s.totalOrders()), totalLblFont, GREEN_BOX_BG, Element.ALIGN_CENTER);
        addStyledBodyCell(aggTable, "-", totalLblFont, GREEN_BOX_BG, Element.ALIGN_RIGHT);
        addStyledBodyCell(aggTable, currencyFmt.format(s.totalRevenue()), totalLblFont, GREEN_BOX_BG, Element.ALIGN_RIGHT);
        addStyledBodyCell(aggTable, currencyFmt.format(s.totalMotoboysAmountToPay()), totalValFont, GREEN_BOX_BG, Element.ALIGN_RIGHT);

        document.add(aggTable);
    }

    private void addKpiCard(
            PdfPTable grid,
            String title,
            String mainValue,
            String subtitle,
            Color bgColor,
            Color borderColor,
            Color titleColor,
            Color valueColor,
            Color subtitleColor
    ) {
        final Font titleFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 7.5f, titleColor);
        final Font valueFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 11.5f, valueColor);
        final Font subFont = FontFactory.getFont(FontFactory.HELVETICA, 7.2f, subtitleColor);

        final Paragraph p = new Paragraph();
        p.setLeading(13.5f);
        p.add(new Phrase(title + "\n", titleFont));
        p.add(new Phrase(mainValue + "\n", valueFont));
        p.add(new Phrase(subtitle, subFont));

        final PdfPCell cell = new PdfPCell(p);
        cell.setBackgroundColor(bgColor);
        cell.setBorderColor(borderColor);
        cell.setBorderWidth(1f);
        cell.setPadding(9.5f);
        grid.addCell(cell);
    }

    private void addEntityUsernameSection(Document document, ReportResponseDTO report) throws Exception {
        final Font sectionFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9.5f, NAVY_BG);
        final String headerLabel = report.type() == ReportType.MOTOBOY ? "IDENTIFICAÇÃO DO ENTREGADOR" : "IDENTIFICAÇÃO DO CLIENTE";
        final Paragraph title = new Paragraph(headerLabel, sectionFont);
        title.setSpacingAfter(6f);
        document.add(title);

        final PdfPTable table = new PdfPTable(new float[]{2.5f, 1f});
        table.setWidthPercentage(100);
        table.setSpacingAfter(16f);

        final Font thFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 8f, Color.WHITE);
        final Font tdFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9f, SLATE_900);

        addNavyHeaderCell(table, "NOME / ESTABELECIMENTO", thFont, Element.ALIGN_LEFT);
        addNavyHeaderCell(table, "MODALIDADE / VÍNCULO", thFont, Element.ALIGN_CENTER);

        addStyledBodyCell(table, report.targetEntityName() != null ? report.targetEntityName() : "-", tdFont, Color.WHITE, Element.ALIGN_LEFT);
        addStyledBodyCell(table, report.targetEntitySubtype() != null ? report.targetEntitySubtype() : "-", tdFont, Color.WHITE, Element.ALIGN_CENTER);

        document.add(table);
    }

    private void addOrdersGroupedByDateSection(
            Document document,
            List<OrderResponseDTO> orders,
            NumberFormat currencyFmt
    ) throws Exception {
        final Font sectionFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9.5f, NAVY_BG);
        final Paragraph title = new Paragraph(String.format("DETALHAMENTO DE PEDIDOS (%d)", orders.size()), sectionFont);
        title.setSpacingAfter(7f);
        document.add(title);

        final Map<String, List<OrderResponseDTO>> groupedByDate = orders.stream()
                .collect(Collectors.groupingBy(
                        o -> o.createdAt() != null ? DATE_FMT.format(o.createdAt()) : "Sem Data",
                        LinkedHashMap::new,
                        Collectors.toList()
                ));

        final Font dateFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 8.5f, NAVY_BG);
        final Font subtotalLblFont = FontFactory.getFont(FontFactory.HELVETICA, 8f, SLATE_700);
        final Font subtotalValFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 8.5f, GREEN_TEXT);
        final Font thFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 8f, NAVY_BG);
        final Font tdFont = FontFactory.getFont(FontFactory.HELVETICA, 8f, SLATE_700);
        final Font tdBoldFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 8f, SLATE_900);

        groupedByDate.forEach((dateKey, dayOrders) -> Try.run(() -> {
            final BigDecimal daySubtotal = dayOrders.stream()
                    .map(OrderResponseDTO::amount)
                    .filter(Objects::nonNull)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            // Date group bar
            final PdfPTable groupBar = new PdfPTable(new float[]{1f, 1f});
            groupBar.setWidthPercentage(100);

            final PdfPCell leftDateCell = new PdfPCell(new Phrase("■  " + dateKey, dateFont));
            leftDateCell.setBackgroundColor(SLATE_HEADER_BG);
            leftDateCell.setBorderColor(BORDER_LIGHT);
            leftDateCell.setPadding(6.5f);
            groupBar.addCell(leftDateCell);

            final Phrase rightSubtotalPhrase = new Phrase();
            rightSubtotalPhrase.add(new Phrase(
                    String.format("%d %s • Subtotal: ", dayOrders.size(), dayOrders.size() == 1 ? "registro" : "registros"),
                    subtotalLblFont
            ));
            rightSubtotalPhrase.add(new Phrase(currencyFmt.format(daySubtotal), subtotalValFont));

            final PdfPCell rightSubtotalCell = new PdfPCell(rightSubtotalPhrase);
            rightSubtotalCell.setBackgroundColor(SLATE_HEADER_BG);
            rightSubtotalCell.setBorderColor(BORDER_LIGHT);
            rightSubtotalCell.setHorizontalAlignment(Element.ALIGN_RIGHT);
            rightSubtotalCell.setPadding(6.5f);
            groupBar.addCell(rightSubtotalCell);

            document.add(groupBar);

            // Orders table for that day
            final PdfPTable dayTable = new PdfPTable(new float[]{1.6f, 1.3f, 3.4f, 1.2f});
            dayTable.setWidthPercentage(100);
            dayTable.setSpacingAfter(10f);

            addTableHeaderCell(dayTable, "CLIENTE", thFont, Element.ALIGN_LEFT);
            addTableHeaderCell(dayTable, "SOLICITANTE", thFont, Element.ALIGN_LEFT);
            addTableHeaderCell(dayTable, "ENDEREÇO DE ENTREGA", thFont, Element.ALIGN_LEFT);
            addTableHeaderCell(dayTable, "VALOR CORRIDA", thFont, Element.ALIGN_RIGHT);

            int rowIdx = 0;
            for (final OrderResponseDTO order : dayOrders) {
                final Color rowBg = (rowIdx % 2 == 0) ? Color.WHITE : ZEBRA_BG;
                final String addr = order.destinationAddress() != null
                        ? String.format("%s, %s %s",
                        order.destinationAddress().street(),
                        order.destinationAddress().number(),
                        order.destinationAddress().neighborhood() != null ? "- " + order.destinationAddress().neighborhood() : "")
                        : "-";
                final String requester = (order.requesterName() != null && !order.requesterName().isBlank())
                        ? order.requesterName()
                        : (order.createdByUserName() != null ? order.createdByUserName() : "-");

                addStyledBodyCell(dayTable, order.clientName() != null ? order.clientName() : "-", tdBoldFont, rowBg, Element.ALIGN_LEFT);
                addStyledBodyCell(dayTable, requester, tdFont, rowBg, Element.ALIGN_LEFT);
                addStyledBodyCell(dayTable, addr.trim(), tdFont, rowBg, Element.ALIGN_LEFT);
                addStyledBodyCell(dayTable, currencyFmt.format(order.amount() != null ? order.amount() : BigDecimal.ZERO), tdBoldFont, rowBg, Element.ALIGN_RIGHT);
                rowIdx++;
            }

            document.add(dayTable);
        }));

        final BigDecimal grandTotal = orders.stream()
                .map(OrderResponseDTO::amount)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // Yellow Highlight Box: TOTAL GERAL POR PERÍODO
        final PdfPTable totalBar = new PdfPTable(new float[]{2f, 1f});
        totalBar.setWidthPercentage(100);
        totalBar.setSpacingAfter(16f);

        final Font totalLblFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9f, NAVY_BG);
        final Font totalValFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 11f, GREEN_TEXT);

        final PdfPCell lblCell = new PdfPCell(new Phrase("TOTAL GERAL POR PERÍODO", totalLblFont));
        lblCell.setBackgroundColor(AMBER_BG);
        lblCell.setBorderColor(AMBER_BORDER);
        lblCell.setPadding(9f);
        totalBar.addCell(lblCell);

        final PdfPCell valCell = new PdfPCell(new Phrase(currencyFmt.format(grandTotal), totalValFont));
        valCell.setBackgroundColor(AMBER_BG);
        valCell.setBorderColor(AMBER_BORDER);
        valCell.setHorizontalAlignment(Element.ALIGN_RIGHT);
        valCell.setPadding(9f);
        totalBar.addCell(valCell);

        document.add(totalBar);
    }

    private void addSummarySection(
            Document document,
            ReportResponseDTO report,
            List<OrderResponseDTO> orders,
            NumberFormat currencyFmt,
            double motoboyPct
    ) throws Exception {
        final double rdoPct = Math.max(0.0, 100.0 - motoboyPct);
        final String motoboyPctLabel = formatPercentage(motoboyPct);
        final String rdoPctLabel = formatPercentage(rdoPct);
        final boolean isMotoboyReport = report.type() == ReportType.MOTOBOY;

        final Font sectionFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9.5f, NAVY_BG);
        final Paragraph title = new Paragraph(
                isMotoboyReport ? "RESUMO FINANCEIRO POR MOTOBOY" : "RESUMO FINANCEIRO POR CLIENTE",
                sectionFont
        );
        title.setSpacingAfter(7f);
        document.add(title);

        // Blue Rule Explanation Box
        final PdfPTable ruleBox = new PdfPTable(1);
        ruleBox.setWidthPercentage(100);
        ruleBox.setSpacingAfter(12f);

        final Font ruleFont = FontFactory.getFont(FontFactory.HELVETICA, 8f, new Color(30, 58, 138));
        final PdfPCell ruleCell = new PdfPCell(new Phrase(
                String.format(
                        "Regra de divisão da corrida: do valor total de cada corrida, %s é destinado ao motoboy e %s é retido pela RDO (empresa). O \"Valor Total Receita\" representa a soma bruta das corridas; \"Valor Motoboy\" e \"Valor RDO\" são os valores já calculados conforme essa divisão.",
                        motoboyPctLabel,
                        rdoPctLabel
                ),
                ruleFont
        ));
        ruleCell.setBackgroundColor(BLUE_BOX_BG);
        ruleCell.setBorderColor(BLUE_BORDER);
        ruleCell.setPadding(9f);
        ruleBox.addCell(ruleCell);
        document.add(ruleBox);

        final BigDecimal totalRevenue = orders.stream()
                .map(OrderResponseDTO::amount)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        final BigDecimal totalMotoboy = orders.stream()
                .map(OrderResponseDTO::motoboyCommissionAmount)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        final BigDecimal totalRdo = totalRevenue.subtract(totalMotoboy);

        // Summary Table (6 columns)
        final PdfPTable summaryTable = new PdfPTable(new float[]{1.9f, 1.1f, 1.5f, 1.6f, 1.5f, 1.5f});
        summaryTable.setWidthPercentage(100);
        summaryTable.setSpacingAfter(14f);

        final Font thFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 8f, Color.WHITE);
        final Font tdFont = FontFactory.getFont(FontFactory.HELVETICA, 8.5f, NAVY_BG);
        final Font tdBoldFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 8.5f, NAVY_BG);
        final Font greenFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 8.5f, GREEN_TEXT);
        final Font blueFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 8.5f, BLUE_TEXT);

        addNavyHeaderCell(summaryTable, isMotoboyReport ? "MOTOBOY" : "CLIENTE", thFont, Element.ALIGN_LEFT);
        addNavyHeaderCell(summaryTable, "QTD. CORRIDAS", thFont, Element.ALIGN_CENTER);
        addNavyHeaderCell(summaryTable, "RECEITA BRUTA", thFont, Element.ALIGN_RIGHT);
        addNavyHeaderCell(summaryTable, "MOTOBOY (" + motoboyPctLabel + ")", thFont, Element.ALIGN_RIGHT);
        addNavyHeaderCell(summaryTable, "RDO (" + rdoPctLabel + ")", thFont, Element.ALIGN_RIGHT);
        addNavyHeaderCell(summaryTable, "TOTAL CALCULADO", thFont, Element.ALIGN_RIGHT);

        final String entityName = report.targetEntityName() != null ? report.targetEntityName() : "-";
        addStyledBodyCell(summaryTable, entityName, tdBoldFont, Color.WHITE, Element.ALIGN_LEFT);
        addStyledBodyCell(summaryTable, String.valueOf(orders.size()), tdFont, Color.WHITE, Element.ALIGN_CENTER);
        addStyledBodyCell(summaryTable, currencyFmt.format(totalRevenue), tdFont, Color.WHITE, Element.ALIGN_RIGHT);
        addStyledBodyCell(summaryTable, currencyFmt.format(totalMotoboy), greenFont, Color.WHITE, Element.ALIGN_RIGHT);
        addStyledBodyCell(summaryTable, currencyFmt.format(totalRdo), blueFont, Color.WHITE, Element.ALIGN_RIGHT);
        addStyledBodyCell(summaryTable, currencyFmt.format(totalRevenue), tdFont, Color.WHITE, Element.ALIGN_RIGHT);

        addStyledBodyCell(summaryTable, "TOTAL GERAL", tdBoldFont, SLATE_HEADER_BG, Element.ALIGN_LEFT);
        addStyledBodyCell(summaryTable, String.valueOf(orders.size()), tdBoldFont, SLATE_HEADER_BG, Element.ALIGN_CENTER);
        addStyledBodyCell(summaryTable, currencyFmt.format(totalRevenue), tdBoldFont, SLATE_HEADER_BG, Element.ALIGN_RIGHT);
        addStyledBodyCell(summaryTable, currencyFmt.format(totalMotoboy), greenFont, SLATE_HEADER_BG, Element.ALIGN_RIGHT);
        addStyledBodyCell(summaryTable, currencyFmt.format(totalRdo), blueFont, SLATE_HEADER_BG, Element.ALIGN_RIGHT);
        addStyledBodyCell(summaryTable, currencyFmt.format(totalRevenue), tdBoldFont, SLATE_HEADER_BG, Element.ALIGN_RIGHT);

        document.add(summaryTable);

        // Two Green Boxes: VALOR MOTOBOY (X%) | VALOR RDO (Y%)
        final PdfPTable greenSplitGrid = new PdfPTable(new float[]{1f, 0.04f, 1f});
        greenSplitGrid.setWidthPercentage(100);
        greenSplitGrid.setSpacingAfter(10f);

        addHighlightSplitBox(greenSplitGrid, "VALOR MOTOBOY (" + motoboyPctLabel + ")", currencyFmt.format(totalMotoboy), GREEN_BOX_BG, GREEN_TEXT, GREEN_BORDER);
        addSpacerCell(greenSplitGrid);
        addHighlightSplitBox(greenSplitGrid, "VALOR RDO (" + rdoPctLabel + ")", currencyFmt.format(totalRdo), BLUE_BOX_BG, BLUE_TEXT, BLUE_BORDER);

        document.add(greenSplitGrid);

        // Bottom Two Boxes: TOTAL DE REGISTROS | SOMA VALOR SERVIÇO
        final PdfPTable bottomGrid = new PdfPTable(new float[]{1f, 0.04f, 1f});
        bottomGrid.setWidthPercentage(100);

        addHighlightSplitBox(bottomGrid, "TOTAL DE REGISTROS", String.valueOf(orders.size()), SLATE_HEADER_BG, NAVY_BG, BORDER_LIGHT);
        addSpacerCell(bottomGrid);
        addHighlightSplitBox(bottomGrid, "SOMA VALOR SERVIÇO", currencyFmt.format(totalRevenue), ROSE_BOX_BG, ROSE_TEXT, ROSE_BORDER);

        document.add(bottomGrid);
    }

    private void addHighlightSplitBox(
            PdfPTable parent,
            String label,
            String value,
            Color bg,
            Color textColor,
            Color borderColor
    ) {
        final PdfPTable inner = new PdfPTable(new float[]{1.3f, 1f});
        final Font lblFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 8f, textColor);
        final Font valFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10.5f, textColor);

        final PdfPCell left = new PdfPCell(new Phrase(label, lblFont));
        left.setBorder(Rectangle.NO_BORDER);
        left.setBackgroundColor(bg);
        left.setVerticalAlignment(Element.ALIGN_MIDDLE);
        left.setPadding(10f);
        inner.addCell(left);

        final PdfPCell right = new PdfPCell(new Phrase(value, valFont));
        right.setBorder(Rectangle.NO_BORDER);
        right.setBackgroundColor(bg);
        right.setHorizontalAlignment(Element.ALIGN_RIGHT);
        right.setVerticalAlignment(Element.ALIGN_MIDDLE);
        right.setPadding(10f);
        inner.addCell(right);

        final PdfPCell wrapper = new PdfPCell(inner);
        wrapper.setBorderColor(borderColor);
        wrapper.setBorderWidth(1f);
        wrapper.setBackgroundColor(bg);
        parent.addCell(wrapper);
    }

    private void addNavyHeaderCell(PdfPTable table, String text, Font font, int alignment) {
        final PdfPCell cell = new PdfPCell(new Phrase(text, font));
        cell.setBackgroundColor(NAVY_BG);
        cell.setBorderColor(NAVY_BG);
        cell.setHorizontalAlignment(alignment);
        cell.setVerticalAlignment(Element.ALIGN_MIDDLE);
        cell.setPadding(7.5f);
        table.addCell(cell);
    }

    private void addTableHeaderCell(PdfPTable table, String text, Font font, int alignment) {
        final PdfPCell cell = new PdfPCell(new Phrase(text, font));
        cell.setBackgroundColor(new Color(241, 245, 249));
        cell.setBorderColor(BORDER_LIGHT);
        cell.setHorizontalAlignment(alignment);
        cell.setVerticalAlignment(Element.ALIGN_MIDDLE);
        cell.setPadding(6.5f);
        table.addCell(cell);
    }

    private void addStyledBodyCell(PdfPTable table, String text, Font font, Color bgColor, int alignment) {
        final PdfPCell cell = new PdfPCell(new Phrase(text, font));
        cell.setBackgroundColor(bgColor);
        cell.setBorderColor(BORDER_LIGHT);
        cell.setHorizontalAlignment(alignment);
        cell.setVerticalAlignment(Element.ALIGN_MIDDLE);
        cell.setPadding(7f);
        table.addCell(cell);
    }
}
