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
import io.vavr.CheckedConsumer;
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
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@Service
@Slf4j
@RequiredArgsConstructor
public class PdfExportService {

    private static final ZoneId SAO_PAULO_ZONE = ZoneId.of("America/Sao_Paulo");
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy").withZone(SAO_PAULO_ZONE);
    private static final DateTimeFormatter DATE_TIME_FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy, HH:mm").withZone(SAO_PAULO_ZONE);
    private static final Locale PT_BR = Locale.of("pt", "BR");

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
                settingsService.getSettings())
        .flatMap(tuple -> Mono.fromCallable(() ->
                renderPdfBytes(tuple.getT1(), tuple.getT2().defaultMotoboyCommissionPercentage() != null ? tuple.getT2().defaultMotoboyCommissionPercentage() : 70.0))
                .subscribeOn(Schedulers.boundedElastic()));
    }

    private String formatPercentage(double pct) {
        return pct == Math.floor(pct)
                ? String.format("%d%%", Math.round(pct))
                : String.format(PT_BR, "%.1f%%", pct);
    }

    private String formatCurrency(BigDecimal amount) {
        return NumberFormat.getCurrencyInstance(PT_BR).format(amount != null ? amount : BigDecimal.ZERO);
    }

    private String formatStartDate(ReportResponseDTO report) {
        return report.periodStart() != null ? DATE_FMT.format(report.periodStart()) : "-";
    }

    private String formatEndDate(ReportResponseDTO report) {
        return report.periodEnd() != null ? DATE_FMT.format(report.periodEnd()) : "-";
    }

    private String resolveEntityUpper(ReportResponseDTO report) {
        return (report.targetEntityName() != null ? report.targetEntityName() : "RELATÓRIO").toUpperCase();
    }

    private List<OrderResponseDTO> safeOrders(ReportDetailResponseDTO detail) {
        return detail.orders() != null ? detail.orders() : List.of();
    }

    private byte[] renderPdfBytes(ReportDetailResponseDTO detail, double motoboyPct) {
        return withPdfDocument(document -> {
            addTopBanner(document, detail.report());
            addInformationsSection(document, detail.report());
            if (detail.report().type() == ReportType.EMPRESA && detail.report().companySummary() != null) {
                addCompanyResumoGeralSection(document, detail.report().companySummary(), safeOrders(detail));
                addOrdersGroupedByDateSection(document, safeOrders(detail));
            } else {
                addEntityUsernameSection(document, detail.report());
                addOrdersGroupedByDateSection(document, safeOrders(detail));
                addSummarySection(document, detail.report(), safeOrders(detail), motoboyPct);
            }
        });
    }

    private byte[] withPdfDocument(CheckedConsumer<Document> consumer) {
        return Try.of(() -> configureAndExport(new ByteArrayOutputStream(), new Document(PageSize.A4, 26, 26, 26, 26), consumer))
                .getOrElseThrow(ex -> new IllegalStateException("Falha ao gerar PDF do relatório: " + ex.getMessage(), ex));
    }

    private byte[] configureAndExport(ByteArrayOutputStream out, Document document, CheckedConsumer<Document> consumer) throws Throwable {
        PdfWriter.getInstance(document, out);
        document.open();
        consumer.accept(document);
        document.close();
        return out.toByteArray();
    }

    private PdfPTable buildTable(float[] widths, float spacingAfter, Consumer<PdfPTable> builder) {
        return configureElement(new PdfPTable(widths), table -> {
            table.setWidthPercentage(100);
            table.setSpacingAfter(spacingAfter);
            builder.accept(table);
        });
    }

    private PdfPCell buildCell(Phrase phrase, Consumer<PdfPCell> customizer) {
        return configureElement(new PdfPCell(phrase), customizer);
    }

    private PdfPCell buildCell(Paragraph paragraph, Consumer<PdfPCell> customizer) {
        return configureElement(new PdfPCell(paragraph), customizer);
    }

    private PdfPCell buildCell(PdfPTable innerTable, Consumer<PdfPCell> customizer) {
        return configureElement(new PdfPCell(innerTable), customizer);
    }

    private Paragraph buildParagraph(float leading, Consumer<Paragraph> builder) {
        return configureElement(new Paragraph(), p -> {
            p.setLeading(leading);
            builder.accept(p);
        });
    }

    private Paragraph buildSectionTitle(String text, float spacingAfter) {
        return configureElement(
                new Paragraph(text, FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9.5f, NAVY_BG)),
                p -> p.setSpacingAfter(spacingAfter)
        );
    }

    private <T> T configureElement(T element, Consumer<T> customizer) {
        customizer.accept(element);
        return element;
    }

    private void addTopBanner(Document document, ReportResponseDTO report) throws Exception {
        document.add(buildTable(new float[]{2.8f, 1.2f}, 16f, banner -> {
            banner.addCell(buildCell(
                    buildParagraph(14f, p -> {
                        p.add(new Phrase("RDO EXPRESS • DEMONSTRATIVO OFICIAL\n", FontFactory.getFont(FontFactory.HELVETICA_BOLD, 7.5f, new Color(248, 113, 113))));
                        p.add(new Phrase(String.format("%s\n", resolveEntityUpper(report)), FontFactory.getFont(FontFactory.HELVETICA_BOLD, 13f, Color.WHITE)));
                        p.add(new Phrase(String.format("Período de Apuração: %s a %s", formatStartDate(report), formatEndDate(report)), FontFactory.getFont(FontFactory.HELVETICA, 8.5f, new Color(203, 213, 225))));
                    }),
                    cell -> {
                        cell.setBackgroundColor(NAVY_BG);
                        cell.setBorder(Rectangle.BOTTOM);
                        cell.setBorderColorBottom(BRAND_RED);
                        cell.setBorderWidthBottom(3f);
                        cell.setPadding(15f);
                    }
            ));
            banner.addCell(buildCell(
                    buildParagraph(13f, p -> {
                        p.add(new Phrase("CÓDIGO DO RELATÓRIO\n", FontFactory.getFont(FontFactory.HELVETICA, 8.5f, new Color(203, 213, 225))));
                        p.add(new Phrase((report.reportCode() != null ? report.reportCode() : "-") + "\n", FontFactory.getFont(FontFactory.HELVETICA_BOLD, 8.5f, Color.WHITE)));
                        p.add(new Phrase(report.createdAt() != null ? "Emissão: " + DATE_TIME_FMT.format(report.createdAt()) : "", FontFactory.getFont(FontFactory.HELVETICA, 8.5f, new Color(203, 213, 225))));
                    }),
                    cell -> {
                        cell.setBackgroundColor(NAVY_BG);
                        cell.setHorizontalAlignment(Element.ALIGN_RIGHT);
                        cell.setVerticalAlignment(Element.ALIGN_MIDDLE);
                        cell.setBorder(Rectangle.BOTTOM);
                        cell.setBorderColorBottom(BRAND_RED);
                        cell.setBorderWidthBottom(3f);
                        cell.setPadding(15f);
                    }
            ));
        }));
    }

    private void addInformationsSection(Document document, ReportResponseDTO report) throws Exception {
        document.add(buildSectionTitle("INFORMAÇÕES DO RELATÓRIO", 6f));
        document.add(buildTable(new float[]{1f, 0.03f, 1.15f, 0.03f, 1f, 0.03f, 0.95f}, 16f, infoGrid -> {
            addStackedMetadataCard(infoGrid, "CATEGORIA", resolveTypeLabel(report));
            addSpacerCell(infoGrid);
            addStackedMetadataCard(infoGrid, "PERÍODO APURADO", formatStartDate(report) + " a " + formatEndDate(report));
            addSpacerCell(infoGrid);
            addStackedMetadataCard(infoGrid, "GERADO POR", report.generatedByUserName() != null ? report.generatedByUserName() : "Sistema");
            addSpacerCell(infoGrid);
            addStackedMetadataCard(infoGrid, "DATA / HORA", report.createdAt() != null ? DATE_TIME_FMT.format(report.createdAt()) : "-");
        }));
    }

    private String resolveTypeLabel(ReportResponseDTO report) {
        return report.type() == ReportType.CLIENTE ? "Cliente"
                : (report.type() == ReportType.MOTOBOY ? "Motoboy"
                : (report.type() == ReportType.EMPRESA ? "Empresa (RDO)" : "Master"));
    }

    private void addStackedMetadataCard(PdfPTable grid, String label, String value) {
        grid.addCell(buildCell(
                buildParagraph(12f, p -> {
                    p.add(new Phrase(label + "\n", FontFactory.getFont(FontFactory.HELVETICA_BOLD, 7f, SLATE_500)));
                    p.add(new Phrase(value, FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9f, SLATE_900)));
                }),
                cell -> {
                    cell.setBackgroundColor(ZEBRA_BG);
                    cell.setBorderColor(BORDER_LIGHT);
                    cell.setBorderWidth(0.8f);
                    cell.setPadding(9f);
                }
        ));
    }

    private void addSpacerCell(PdfPTable grid) {
        grid.addCell(buildCell(new Phrase(""), cell -> cell.setBorder(Rectangle.NO_BORDER)));
    }

    private BigDecimal resolveEstimatedProfit(CompanyReportSummaryDTO s, List<OrderResponseDTO> orders) {
        return s.estimatedProfit() != null
                ? s.estimatedProfit()
                : (orders != null && !orders.isEmpty()
                        ? sumEligibleOrders(orders, OrderResponseDTO::amount)
                                .subtract(sumEligibleOrders(orders, OrderResponseDTO::motoboyCommissionAmount))
                                .subtract(s.totalExpenses() != null ? s.totalExpenses() : BigDecimal.ZERO)
                        : (s.totalRevenue() != null ? s.totalRevenue() : BigDecimal.ZERO)
                                .subtract(s.totalCommissions() != null ? s.totalCommissions() : BigDecimal.ZERO)
                                .subtract(s.totalExpenses() != null ? s.totalExpenses() : BigDecimal.ZERO));
    }

    private BigDecimal sumEligibleOrders(List<OrderResponseDTO> orders, Function<OrderResponseDTO, BigDecimal> extractor) {
        return orders.stream()
                .filter(o -> o != null && o.status() != null && switch (o.status()) {
                    case COLETADO, ENTREGUE, AGUARDANDO_PAGAMENTO, PAGO -> true;
                    case PENDENTE, CANCELADO -> false;
                })
                .map(extractor)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private void addCompanyResumoGeralSection(
            Document document,
            CompanyReportSummaryDTO s,
            List<OrderResponseDTO> orders
    ) throws Exception {
        document.add(buildSectionTitle("RESUMO GERAL (FINANCEIRO RDO)", 7f));

        document.add(buildTable(new float[]{1f, 0.03f, 1f, 0.03f, 1f, 0.03f, 1f}, 7f, row1 -> {
            addKpiCard(row1, "RECEITAS", formatCurrency(s.totalRevenue()), String.format("Valor total de pedidos (%d)", s.totalOrders()), Color.WHITE, BORDER_LIGHT, SLATE_700, SLATE_900, SLATE_500);
            addSpacerCell(row1);
            addKpiCard(row1, "JÁ RECEBIDO", formatCurrency(s.paidRevenue()), String.format("%d pagos em caixa", s.paidOrdersCount()), GREEN_BOX_BG, GREEN_BORDER, GREEN_TEXT, GREEN_TEXT, GREEN_TEXT);
            addSpacerCell(row1);
            addKpiCard(row1, "A RECEBER", formatCurrency(s.pendingRevenue()), String.format("%d a receber (não pagos)", s.pendingOrdersCount()), AMBER_BG, AMBER_BORDER, AMBER_TEXT, AMBER_TEXT, AMBER_TEXT);
            addSpacerCell(row1);
            addKpiCard(row1, "REGISTROS", String.valueOf(s.totalOrders()), String.format("%d pagos • %d pendentes", s.paidOrdersCount(), s.pendingOrdersCount()), ZEBRA_BG, BORDER_LIGHT, SLATE_700, AMBER_TEXT, SLATE_500);
        }));

        document.add(buildTable(new float[]{1f, 0.03f, 1f, 0.03f, 1f, 0.03f, 1f}, 7f, row2 -> {
            addKpiCard(row2, "LUCRO REAL", formatCurrency(s.netProfit()), "Somente sobre pedidos pagos", ZEBRA_BG, BORDER_LIGHT, SLATE_700, SLATE_900, SLATE_500);
            addSpacerCell(row2);
            addKpiCard(row2, "LUCRO ESTIMADO", formatCurrency(resolveEstimatedProfit(s, orders)), "Pedidos coletados em diante", BLUE_BOX_BG, BLUE_BORDER, BLUE_TEXT, BLUE_TEXT, BLUE_TEXT);
            addSpacerCell(row2);
            addKpiCard(row2, "REPASSES", formatCurrency(s.totalCommissions()), String.format("Pagos: %s", formatCurrency(s.paidCommissions())), ROSE_BOX_BG, ROSE_BORDER, ROSE_TEXT, SLATE_900, ROSE_TEXT);
            addSpacerCell(row2);
            addKpiCard(row2, "DESPESAS", formatCurrency(s.totalExpenses()), "Lançamentos do período", PURPLE_BOX_BG, PURPLE_BORDER, PURPLE_TEXT, PURPLE_TEXT, SLATE_500);
        }));

        document.add(buildTable(new float[]{1f, 0.03f, 3.09f}, 18f, row3 -> {
            addKpiCard(row3, "COLABORADOR", formatCurrency(s.collaboratorShare()), "Comissão motoboys no período", BLUE_BOX_BG, BLUE_BORDER, BLUE_TEXT, BLUE_TEXT, SLATE_500);
            addSpacerCell(row3);
            row3.addCell(buildCell(
                    new Phrase(
                            "■ Demonstrativo da Empresa: O Lucro Real reflete o valor recebido em caixa (pedidos pagos) descontando repasses e despesas. O Lucro Estimado projeta o resultado considerando todos os pedidos a partir do status Coletado (em rota/entregues/pagos).",
                            FontFactory.getFont(FontFactory.HELVETICA, 7.5f, SLATE_700)
                    ),
                    cell -> {
                        cell.setBackgroundColor(SLATE_HEADER_BG);
                        cell.setBorderColor(BORDER_LIGHT);
                        cell.setPadding(9.5f);
                        cell.setVerticalAlignment(Element.ALIGN_MIDDLE);
                    }
            ));
        }));

        document.add(buildSectionTitle("AGREGADO POR MOTOBOY (VALORES A PAGAR NO DIA)", 7f));

        document.add(buildTable(new float[]{2.3f, 1.45f, 1.05f, 1.1f, 1.4f, 1.5f}, 18f, aggTable -> {
            addNavyHeaderCell(aggTable, "MOTOBOY", FontFactory.getFont(FontFactory.HELVETICA_BOLD, 8f, Color.WHITE), Element.ALIGN_LEFT);
            addNavyHeaderCell(aggTable, "VÍNCULO", FontFactory.getFont(FontFactory.HELVETICA_BOLD, 8f, Color.WHITE), Element.ALIGN_CENTER);
            addNavyHeaderCell(aggTable, "QTD. PEDIDOS", FontFactory.getFont(FontFactory.HELVETICA_BOLD, 8f, Color.WHITE), Element.ALIGN_CENTER);
            addNavyHeaderCell(aggTable, "KM TOTAL", FontFactory.getFont(FontFactory.HELVETICA_BOLD, 8f, Color.WHITE), Element.ALIGN_RIGHT);
            addNavyHeaderCell(aggTable, "VALOR BRUTO", FontFactory.getFont(FontFactory.HELVETICA_BOLD, 8f, Color.WHITE), Element.ALIGN_RIGHT);
            addNavyHeaderCell(aggTable, "VALOR A PAGAR", FontFactory.getFont(FontFactory.HELVETICA_BOLD, 8f, Color.WHITE), Element.ALIGN_RIGHT);

            Optional.ofNullable(s.motoboyAggregates())
                    .ifPresent(list -> IntStream.range(0, list.size())
                            .forEach(idx -> addMotoboyAggregateRow(aggTable, list.get(idx), idx % 2 == 0 ? Color.WHITE : ZEBRA_BG)));

            aggTable.addCell(buildCell(
                    new Phrase("VALOR FINAL DE TODOS OS MOTOBOYS JUNTOS NO DIA", FontFactory.getFont(FontFactory.HELVETICA_BOLD, 8.5f, GREEN_TEXT)),
                    cell -> {
                        cell.setColspan(2);
                        cell.setBackgroundColor(GREEN_BOX_BG);
                        cell.setBorderColor(GREEN_BORDER);
                        cell.setPadding(8f);
                    }
            ));

            addStyledBodyCell(aggTable, String.valueOf(s.totalOrders()), FontFactory.getFont(FontFactory.HELVETICA_BOLD, 8.5f, GREEN_TEXT), GREEN_BOX_BG, Element.ALIGN_CENTER);
            addStyledBodyCell(aggTable, "-", FontFactory.getFont(FontFactory.HELVETICA_BOLD, 8.5f, GREEN_TEXT), GREEN_BOX_BG, Element.ALIGN_RIGHT);
            addStyledBodyCell(aggTable, formatCurrency(s.totalRevenue()), FontFactory.getFont(FontFactory.HELVETICA_BOLD, 8.5f, GREEN_TEXT), GREEN_BOX_BG, Element.ALIGN_RIGHT);
            addStyledBodyCell(aggTable, formatCurrency(s.totalMotoboysAmountToPay()), FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9.5f, GREEN_TEXT), GREEN_BOX_BG, Element.ALIGN_RIGHT);
        }));
    }

    private void addMotoboyAggregateRow(PdfPTable aggTable, MotoboyReportAggregateDTO agg, Color rowBg) {
        addStyledBodyCell(aggTable, agg.motoboyName(), FontFactory.getFont(FontFactory.HELVETICA_BOLD, 8.5f, SLATE_900), rowBg, Element.ALIGN_LEFT);
        addStyledBodyCell(aggTable, agg.motoboyType(), FontFactory.getFont(FontFactory.HELVETICA, 8.5f, SLATE_900), rowBg, Element.ALIGN_CENTER);
        addStyledBodyCell(aggTable, String.valueOf(agg.totalOrders()), FontFactory.getFont(FontFactory.HELVETICA_BOLD, 8.5f, SLATE_900), rowBg, Element.ALIGN_CENTER);
        addStyledBodyCell(aggTable, String.format(PT_BR, "%.1f km", agg.totalDistanceKm()), FontFactory.getFont(FontFactory.HELVETICA, 8.5f, SLATE_900), rowBg, Element.ALIGN_RIGHT);
        addStyledBodyCell(aggTable, formatCurrency(agg.grossAmount()), FontFactory.getFont(FontFactory.HELVETICA, 8.5f, SLATE_900), rowBg, Element.ALIGN_RIGHT);
        addStyledBodyCell(aggTable, formatCurrency(agg.amountToPay()), FontFactory.getFont(FontFactory.HELVETICA_BOLD, 8.5f, GREEN_TEXT), rowBg, Element.ALIGN_RIGHT);
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
        grid.addCell(buildCell(
                buildParagraph(13.5f, p -> {
                    p.add(new Phrase(title + "\n", FontFactory.getFont(FontFactory.HELVETICA_BOLD, 7.5f, titleColor)));
                    p.add(new Phrase(mainValue + "\n", FontFactory.getFont(FontFactory.HELVETICA_BOLD, 11.5f, valueColor)));
                    p.add(new Phrase(subtitle, FontFactory.getFont(FontFactory.HELVETICA, 7.2f, subtitleColor)));
                }),
                cell -> {
                    cell.setBackgroundColor(bgColor);
                    cell.setBorderColor(borderColor);
                    cell.setBorderWidth(1f);
                    cell.setPadding(9.5f);
                }
        ));
    }

    private void addEntityUsernameSection(Document document, ReportResponseDTO report) throws Exception {
        document.add(buildSectionTitle(
                report.type() == ReportType.MOTOBOY ? "IDENTIFICAÇÃO DO ENTREGADOR" : "IDENTIFICAÇÃO DO CLIENTE",
                6f
        ));
        document.add(buildTable(new float[]{2.5f, 1f}, 16f, table -> {
            addNavyHeaderCell(table, "NOME / ESTABELECIMENTO", FontFactory.getFont(FontFactory.HELVETICA_BOLD, 8f, Color.WHITE), Element.ALIGN_LEFT);
            addNavyHeaderCell(table, "MODALIDADE / VÍNCULO", FontFactory.getFont(FontFactory.HELVETICA_BOLD, 8f, Color.WHITE), Element.ALIGN_CENTER);
            addStyledBodyCell(table, report.targetEntityName() != null ? report.targetEntityName() : "-", FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9f, SLATE_900), Color.WHITE, Element.ALIGN_LEFT);
            addStyledBodyCell(table, report.targetEntitySubtype() != null ? report.targetEntitySubtype() : "-", FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9f, SLATE_900), Color.WHITE, Element.ALIGN_CENTER);
        }));
    }

    private void addOrdersGroupedByDateSection(Document document, List<OrderResponseDTO> orders) throws Exception {
        document.add(buildSectionTitle(String.format("DETALHAMENTO DE PEDIDOS (%d)", orders.size()), 7f));

        groupOrdersByDate(orders).forEach((dateKey, dayOrders) -> Try.run(() -> {
            document.add(buildTable(new float[]{1f, 1f}, 0f, groupBar -> {
                groupBar.addCell(buildCell(
                        new Phrase("■  " + dateKey, FontFactory.getFont(FontFactory.HELVETICA_BOLD, 8.5f, NAVY_BG)),
                        cell -> {
                            cell.setBackgroundColor(SLATE_HEADER_BG);
                            cell.setBorderColor(BORDER_LIGHT);
                            cell.setPadding(6.5f);
                        }
                ));
                groupBar.addCell(buildCell(
                        configureElement(new Phrase(), phrase -> {
                            phrase.add(new Phrase(
                                    String.format("%d %s • Subtotal: ", dayOrders.size(), dayOrders.size() == 1 ? "registro" : "registros"),
                                    FontFactory.getFont(FontFactory.HELVETICA, 8f, SLATE_700)
                            ));
                            phrase.add(new Phrase(
                                    formatCurrency(sumOrdersRevenue(dayOrders)),
                                    FontFactory.getFont(FontFactory.HELVETICA_BOLD, 8.5f, GREEN_TEXT)
                            ));
                        }),
                        cell -> {
                            cell.setBackgroundColor(SLATE_HEADER_BG);
                            cell.setBorderColor(BORDER_LIGHT);
                            cell.setHorizontalAlignment(Element.ALIGN_RIGHT);
                            cell.setPadding(6.5f);
                        }
                ));
            }));

            document.add(buildTable(new float[]{1.6f, 1.3f, 3.4f, 1.2f}, 10f, dayTable -> {
                addTableHeaderCell(dayTable, "CLIENTE", FontFactory.getFont(FontFactory.HELVETICA_BOLD, 8f, NAVY_BG), Element.ALIGN_LEFT);
                addTableHeaderCell(dayTable, "SOLICITANTE", FontFactory.getFont(FontFactory.HELVETICA_BOLD, 8f, NAVY_BG), Element.ALIGN_LEFT);
                addTableHeaderCell(dayTable, "ENDEREÇO DE ENTREGA", FontFactory.getFont(FontFactory.HELVETICA_BOLD, 8f, NAVY_BG), Element.ALIGN_LEFT);
                addTableHeaderCell(dayTable, "VALOR CORRIDA", FontFactory.getFont(FontFactory.HELVETICA_BOLD, 8f, NAVY_BG), Element.ALIGN_RIGHT);

                IntStream.range(0, dayOrders.size())
                        .forEach(rowIdx -> addOrderRow(dayTable, dayOrders.get(rowIdx), rowIdx % 2 == 0 ? Color.WHITE : ZEBRA_BG));
            }));
        }));

        document.add(buildTable(new float[]{2f, 1f}, 16f, totalBar -> {
            totalBar.addCell(buildCell(
                    new Phrase("TOTAL GERAL POR PERÍODO", FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9f, NAVY_BG)),
                    cell -> {
                        cell.setBackgroundColor(AMBER_BG);
                        cell.setBorderColor(AMBER_BORDER);
                        cell.setPadding(9f);
                    }
            ));
            totalBar.addCell(buildCell(
                    new Phrase(formatCurrency(sumOrdersRevenue(orders)), FontFactory.getFont(FontFactory.HELVETICA_BOLD, 11f, GREEN_TEXT)),
                    cell -> {
                        cell.setBackgroundColor(AMBER_BG);
                        cell.setBorderColor(AMBER_BORDER);
                        cell.setHorizontalAlignment(Element.ALIGN_RIGHT);
                        cell.setPadding(9f);
                    }
            ));
        }));
    }

    private Map<String, List<OrderResponseDTO>> groupOrdersByDate(List<OrderResponseDTO> orders) {
        return orders.stream()
                .collect(Collectors.groupingBy(
                        o -> o.createdAt() != null ? DATE_FMT.format(o.createdAt()) : "Sem Data",
                        LinkedHashMap::new,
                        Collectors.toList()
                ));
    }

    private void addOrderRow(PdfPTable dayTable, OrderResponseDTO order, Color rowBg) {
        addStyledBodyCell(dayTable, order.clientName() != null ? order.clientName() : "-", FontFactory.getFont(FontFactory.HELVETICA_BOLD, 8f, SLATE_900), rowBg, Element.ALIGN_LEFT);
        addStyledBodyCell(dayTable, resolveRequesterLabel(order), FontFactory.getFont(FontFactory.HELVETICA, 8f, SLATE_700), rowBg, Element.ALIGN_LEFT);
        addStyledBodyCell(dayTable, resolveDestinationLabel(order), FontFactory.getFont(FontFactory.HELVETICA, 8f, SLATE_700), rowBg, Element.ALIGN_LEFT);
        addStyledBodyCell(dayTable, formatCurrency(order.amount()), FontFactory.getFont(FontFactory.HELVETICA_BOLD, 8f, SLATE_900), rowBg, Element.ALIGN_RIGHT);
    }

    private String resolveRequesterLabel(OrderResponseDTO order) {
        return (order.requesterName() != null && !order.requesterName().isBlank())
                ? order.requesterName()
                : (order.createdByUserName() != null ? order.createdByUserName() : "-");
    }

    private String resolveDestinationLabel(OrderResponseDTO order) {
        return order.destinationAddress() != null
                ? String.format(
                        "%s, %s %s",
                        order.destinationAddress().street(),
                        order.destinationAddress().number(),
                        order.destinationAddress().neighborhood() != null ? "- " + order.destinationAddress().neighborhood() : ""
                ).trim()
                : "-";
    }

    private BigDecimal sumOrdersRevenue(List<OrderResponseDTO> orders) {
        return orders.stream()
                .map(OrderResponseDTO::amount)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private BigDecimal sumOrdersMotoboyShare(List<OrderResponseDTO> orders) {
        return orders.stream()
                .map(OrderResponseDTO::motoboyCommissionAmount)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private void addSummarySection(
            Document document,
            ReportResponseDTO report,
            List<OrderResponseDTO> orders,
            double motoboyPct
    ) throws Exception {
        document.add(buildSectionTitle(
                report.type() == ReportType.MOTOBOY ? "RESUMO FINANCEIRO POR MOTOBOY" : "RESUMO FINANCEIRO POR CLIENTE",
                7f
        ));

        document.add(buildTable(new float[]{1f}, 12f, ruleBox ->
                ruleBox.addCell(buildCell(
                        new Phrase(
                                String.format(
                                        "Regra de divisão da corrida: do valor total de cada corrida, %s é destinado ao motoboy e %s é retido pela RDO (empresa). O \"Valor Total Receita\" representa a soma bruta das corridas; \"Valor Motoboy\" e \"Valor RDO\" são os valores já calculados conforme essa divisão.",
                                        formatPercentage(motoboyPct),
                                        formatPercentage(Math.max(0.0, 100.0 - motoboyPct))
                                ),
                                FontFactory.getFont(FontFactory.HELVETICA, 8f, new Color(30, 58, 138))
                        ),
                        cell -> {
                            cell.setBackgroundColor(BLUE_BOX_BG);
                            cell.setBorderColor(BLUE_BORDER);
                            cell.setPadding(9f);
                        }
                ))
        ));

        document.add(buildTable(new float[]{1.9f, 1.1f, 1.5f, 1.6f, 1.5f, 1.5f}, 14f, summaryTable -> {
            addNavyHeaderCell(summaryTable, report.type() == ReportType.MOTOBOY ? "MOTOBOY" : "CLIENTE", FontFactory.getFont(FontFactory.HELVETICA_BOLD, 8f, Color.WHITE), Element.ALIGN_LEFT);
            addNavyHeaderCell(summaryTable, "QTD. CORRIDAS", FontFactory.getFont(FontFactory.HELVETICA_BOLD, 8f, Color.WHITE), Element.ALIGN_CENTER);
            addNavyHeaderCell(summaryTable, "RECEITA BRUTA", FontFactory.getFont(FontFactory.HELVETICA_BOLD, 8f, Color.WHITE), Element.ALIGN_RIGHT);
            addNavyHeaderCell(summaryTable, "MOTOBOY (" + formatPercentage(motoboyPct) + ")", FontFactory.getFont(FontFactory.HELVETICA_BOLD, 8f, Color.WHITE), Element.ALIGN_RIGHT);
            addNavyHeaderCell(summaryTable, "RDO (" + formatPercentage(Math.max(0.0, 100.0 - motoboyPct)) + ")", FontFactory.getFont(FontFactory.HELVETICA_BOLD, 8f, Color.WHITE), Element.ALIGN_RIGHT);
            addNavyHeaderCell(summaryTable, "TOTAL CALCULADO", FontFactory.getFont(FontFactory.HELVETICA_BOLD, 8f, Color.WHITE), Element.ALIGN_RIGHT);

            addStyledBodyCell(summaryTable, report.targetEntityName() != null ? report.targetEntityName() : "-", FontFactory.getFont(FontFactory.HELVETICA_BOLD, 8.5f, NAVY_BG), Color.WHITE, Element.ALIGN_LEFT);
            addStyledBodyCell(summaryTable, String.valueOf(orders.size()), FontFactory.getFont(FontFactory.HELVETICA, 8.5f, NAVY_BG), Color.WHITE, Element.ALIGN_CENTER);
            addStyledBodyCell(summaryTable, formatCurrency(sumOrdersRevenue(orders)), FontFactory.getFont(FontFactory.HELVETICA, 8.5f, NAVY_BG), Color.WHITE, Element.ALIGN_RIGHT);
            addStyledBodyCell(summaryTable, formatCurrency(sumOrdersMotoboyShare(orders)), FontFactory.getFont(FontFactory.HELVETICA_BOLD, 8.5f, GREEN_TEXT), Color.WHITE, Element.ALIGN_RIGHT);
            addStyledBodyCell(summaryTable, formatCurrency(sumOrdersRevenue(orders).subtract(sumOrdersMotoboyShare(orders))), FontFactory.getFont(FontFactory.HELVETICA_BOLD, 8.5f, BLUE_TEXT), Color.WHITE, Element.ALIGN_RIGHT);
            addStyledBodyCell(summaryTable, formatCurrency(sumOrdersRevenue(orders)), FontFactory.getFont(FontFactory.HELVETICA, 8.5f, NAVY_BG), Color.WHITE, Element.ALIGN_RIGHT);

            addStyledBodyCell(summaryTable, "TOTAL GERAL", FontFactory.getFont(FontFactory.HELVETICA_BOLD, 8.5f, NAVY_BG), SLATE_HEADER_BG, Element.ALIGN_LEFT);
            addStyledBodyCell(summaryTable, String.valueOf(orders.size()), FontFactory.getFont(FontFactory.HELVETICA_BOLD, 8.5f, NAVY_BG), SLATE_HEADER_BG, Element.ALIGN_CENTER);
            addStyledBodyCell(summaryTable, formatCurrency(sumOrdersRevenue(orders)), FontFactory.getFont(FontFactory.HELVETICA_BOLD, 8.5f, NAVY_BG), SLATE_HEADER_BG, Element.ALIGN_RIGHT);
            addStyledBodyCell(summaryTable, formatCurrency(sumOrdersMotoboyShare(orders)), FontFactory.getFont(FontFactory.HELVETICA_BOLD, 8.5f, GREEN_TEXT), SLATE_HEADER_BG, Element.ALIGN_RIGHT);
            addStyledBodyCell(summaryTable, formatCurrency(sumOrdersRevenue(orders).subtract(sumOrdersMotoboyShare(orders))), FontFactory.getFont(FontFactory.HELVETICA_BOLD, 8.5f, BLUE_TEXT), SLATE_HEADER_BG, Element.ALIGN_RIGHT);
            addStyledBodyCell(summaryTable, formatCurrency(sumOrdersRevenue(orders)), FontFactory.getFont(FontFactory.HELVETICA_BOLD, 8.5f, NAVY_BG), SLATE_HEADER_BG, Element.ALIGN_RIGHT);
        }));

        document.add(buildTable(new float[]{1f, 0.04f, 1f}, 10f, greenSplitGrid -> {
            addHighlightSplitBox(greenSplitGrid, "VALOR MOTOBOY (" + formatPercentage(motoboyPct) + ")", formatCurrency(sumOrdersMotoboyShare(orders)), GREEN_BOX_BG, GREEN_TEXT, GREEN_BORDER);
            addSpacerCell(greenSplitGrid);
            addHighlightSplitBox(greenSplitGrid, "VALOR RDO (" + formatPercentage(Math.max(0.0, 100.0 - motoboyPct)) + ")", formatCurrency(sumOrdersRevenue(orders).subtract(sumOrdersMotoboyShare(orders))), BLUE_BOX_BG, BLUE_TEXT, BLUE_BORDER);
        }));

        document.add(buildTable(new float[]{1f, 0.04f, 1f}, 0f, bottomGrid -> {
            addHighlightSplitBox(bottomGrid, "TOTAL DE REGISTROS", String.valueOf(orders.size()), SLATE_HEADER_BG, NAVY_BG, BORDER_LIGHT);
            addSpacerCell(bottomGrid);
            addHighlightSplitBox(bottomGrid, "SOMA VALOR SERVIÇO", formatCurrency(sumOrdersRevenue(orders)), ROSE_BOX_BG, ROSE_TEXT, ROSE_BORDER);
        }));
    }

    private void addHighlightSplitBox(
            PdfPTable parent,
            String label,
            String value,
            Color bg,
            Color textColor,
            Color borderColor
    ) {
        parent.addCell(buildCell(
                buildTable(new float[]{1.3f, 1f}, 0f, inner -> {
                    inner.addCell(buildCell(
                            new Phrase(label, FontFactory.getFont(FontFactory.HELVETICA_BOLD, 8f, textColor)),
                            left -> {
                                left.setBorder(Rectangle.NO_BORDER);
                                left.setBackgroundColor(bg);
                                left.setVerticalAlignment(Element.ALIGN_MIDDLE);
                                left.setPadding(10f);
                            }
                    ));
                    inner.addCell(buildCell(
                            new Phrase(value, FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10.5f, textColor)),
                            right -> {
                                right.setBorder(Rectangle.NO_BORDER);
                                right.setBackgroundColor(bg);
                                right.setHorizontalAlignment(Element.ALIGN_RIGHT);
                                right.setVerticalAlignment(Element.ALIGN_MIDDLE);
                                right.setPadding(10f);
                            }
                    ));
                }),
                wrapper -> {
                    wrapper.setBorderColor(borderColor);
                    wrapper.setBorderWidth(1f);
                    wrapper.setBackgroundColor(bg);
                }
        ));
    }

    private void addNavyHeaderCell(PdfPTable table, String text, Font font, int alignment) {
        table.addCell(buildCell(new Phrase(text, font), cell -> {
            cell.setBackgroundColor(NAVY_BG);
            cell.setBorderColor(NAVY_BG);
            cell.setHorizontalAlignment(alignment);
            cell.setVerticalAlignment(Element.ALIGN_MIDDLE);
            cell.setPadding(7.5f);
        }));
    }

    private void addTableHeaderCell(PdfPTable table, String text, Font font, int alignment) {
        table.addCell(buildCell(new Phrase(text, font), cell -> {
            cell.setBackgroundColor(SLATE_HEADER_BG);
            cell.setBorderColor(BORDER_LIGHT);
            cell.setHorizontalAlignment(alignment);
            cell.setVerticalAlignment(Element.ALIGN_MIDDLE);
            cell.setPadding(6.5f);
        }));
    }

    private void addStyledBodyCell(PdfPTable table, String text, Font font, Color bgColor, int alignment) {
        table.addCell(buildCell(new Phrase(text, font), cell -> {
            cell.setBackgroundColor(bgColor);
            cell.setBorderColor(BORDER_LIGHT);
            cell.setHorizontalAlignment(alignment);
            cell.setVerticalAlignment(Element.ALIGN_MIDDLE);
            cell.setPadding(7f);
        }));
    }
}
