package br.com.verticelabs.pdfprocessor.infrastructure.excel;

import br.com.verticelabs.pdfprocessor.application.empresas.EmpresaHonorariosResolver;
import br.com.verticelabs.pdfprocessor.application.excel.ResumoGeralMontagemResult;
import br.com.verticelabs.pdfprocessor.domain.model.Person;
import com.itextpdf.io.image.ImageDataFactory;
import com.itextpdf.kernel.colors.DeviceRgb;
import com.itextpdf.kernel.geom.PageSize;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.borders.Border;
import com.itextpdf.layout.borders.DoubleBorder;
import com.itextpdf.layout.borders.SolidBorder;
import com.itextpdf.layout.element.Cell;
import com.itextpdf.layout.element.Image;
import com.itextpdf.layout.element.Paragraph;
import com.itextpdf.layout.element.Table;
import com.itextpdf.layout.properties.HorizontalAlignment;
import com.itextpdf.layout.properties.TextAlignment;
import com.itextpdf.layout.properties.UnitValue;
import com.itextpdf.layout.properties.VerticalAlignment;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.text.Normalizer;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Gera PDF landscape (1 página) espelhando a aba Resumo Geral do Excel.
 */
@Slf4j
@Component
public class ResumoGeralPdfGenerator {

    private static final String LOGO_CLASSPATH = "excel/origium_logo.png";
    private static final DeviceRgb HEADER_BG = new DeviceRgb(191, 191, 191);
    private static final DeviceRgb TOTAL_BG = new DeviceRgb(255, 255, 0);
    private static final DeviceRgb HONOR_COLOR = new DeviceRgb(192, 0, 0);
    private static final DateTimeFormatter DATA_BR = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter DATA_HORA_BR = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private static final DateTimeFormatter FILENAME_TS = DateTimeFormatter.ofPattern("yyyyMMddHHmm");

    private static final float OUTER = 1.5f;
    private static final float INNER = 0.5f;
    private static final int COLS = 8;
    private static final int LOGO_ROWSPAN = 8;
    private static final float LOGO_MAX_HEIGHT = 24f;
    private static final float LOGO_MAX_WIDTH = 56f;
    private static final float[] COL_WIDTHS = {10.34f, 10.34f, 10.34f, 10.34f, 10.34f, 10.34f, 10.34f, 27.58f};

    public byte[] generate(Person person, ResumoGeralMontagemResult montagem) {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            PdfWriter writer = new PdfWriter(baos);
            PdfDocument pdf = new PdfDocument(writer);
            pdf.setDefaultPageSize(PageSize.A4.rotate());

            int rowCount = montagem.linhas() != null ? montagem.linhas().size() : 0;
            float baseFont = rowCount > 14 ? 5.5f : rowCount > 10 ? 6f : rowCount > 7 ? 6.5f : 7f;
            float headerFont = baseFont + 0.5f;
            float labelFont = baseFont;
            float cellPad = rowCount > 10 ? 1.5f : 2f;

            Document doc = new Document(pdf, PageSize.A4.rotate());
            doc.setMargins(14, 16, 14, 16);

            doc.add(buildMainBlock(person, montagem, baseFont, headerFont, labelFont, cellPad));
            doc.add(buildFooterTable(montagem, labelFont, cellPad));

            doc.close();
            return baos.toByteArray();
        } catch (Exception e) {
            throw new IllegalStateException("Falha ao gerar PDF do Resumo Geral: " + e.getMessage(), e);
        }
    }

    public String buildFilename(Person person) {
        String ts = montagemNow().format(FILENAME_TS);
        String cpf = person.getCpf() != null ? person.getCpf().replaceAll("\\D", "") : "00000000000";
        return String.format("%s_%s_%s.pdf", ts, cpf, normalizeName(person.getNome()));
    }

    private Table buildMainBlock(
            Person person,
            ResumoGeralMontagemResult montagem,
            float baseFont,
            float headerFont,
            float labelFont,
            float cellPad) {

        Table table = new Table(UnitValue.createPercentArray(COL_WIDTHS)).useAllAvailableWidth();

        String nome = person.getNome() != null ? person.getNome().toUpperCase(Locale.ROOT) : "";
        Cell logoCell = buildLogoCell(cellPad);

        table.addCell(headerLabelCell("NOME: " + nome, labelFont, cellPad, true, false, true, false));
        table.addCell(logoCell);

        table.addCell(headerLabelCell("CPF  " + formatCpf(person.getCpf()), labelFont, cellPad, false, false, true, false));
        table.addCell(headerLabelCell("Atualização : SELIC RECEITA FEDERAL", labelFont, cellPad, false, false, true, false));
        table.addCell(headerLabelCell("Datas para atualização", labelFont, cellPad, false, false, true, false));

        addDateGridRows(table, labelFont, cellPad);

        addTableHeaderRow(table, headerFont, cellPad);

        SolidBorder inner = innerBorder();
        for (ExcelResumoGeralLinhaDTO linha : montagem.linhas()) {
            table.addCell(dataCell(linha.getAnoCalendario(), baseFont, inner, TextAlignment.CENTER, false, null, cellPad, null, true, false, false));
            table.addCell(dataCell(formatMoney(linha.getValorDeclaracao()), baseFont, inner, TextAlignment.RIGHT, false, null, cellPad, null, false, false, false));
            table.addCell(dataCell(formatMoney(linha.getValorSimulacao()), baseFont, inner, TextAlignment.RIGHT, false, null, cellPad, null, false, false, false));
            table.addCell(dataCell(formatPrincipal(linha.getPrincipal()), baseFont, inner, TextAlignment.RIGHT, false, null, cellPad, null, false, false, false));
            table.addCell(dataCell(formatPercent(linha.getPrincipal(), linha.getSelicAcumulada()), baseFont, inner, TextAlignment.RIGHT, false, null, cellPad, null, false, false, false));
            table.addCell(dataCell(formatCorrecao(linha.getPrincipal(), linha.getValorCorrecao()), baseFont, inner, TextAlignment.RIGHT, false, null, cellPad, null, false, false, false));
            table.addCell(dataCell(formatCorrecao(linha.getPrincipal(), linha.getPrincipalMaisCorrecao()), baseFont, inner, TextAlignment.RIGHT, false, null, cellPad, null, false, false, false));
            table.addCell(dataCell(nullSafe(linha.getObservacao()), baseFont, inner, TextAlignment.LEFT, false, null, cellPad, null, false, true, false));
        }

        ExcelResumoGeralHelper.TotaisResumoGeral totais = montagem.totais();
        EmpresaHonorariosResolver.HonorariosConfig honor = montagem.honorariosConfig();
        Border doubleSep = new DoubleBorder(OUTER);

        table.addCell(totalLabelCell("Total da diferença R$", baseFont, inner, cellPad, doubleSep, true, false, false));
        table.addCell(emptyDataCell(inner, cellPad, doubleSep, false, false, false));
        table.addCell(emptyDataCell(inner, cellPad, doubleSep, false, false, false));
        table.addCell(totalValueCell(formatMoney(totais.totalPrincipal()), baseFont, inner, cellPad, doubleSep, false, false, false));
        table.addCell(emptyDataCell(inner, cellPad, doubleSep, false, false, false));
        table.addCell(totalValueCell(formatMoney(totais.totalCorrecao()), baseFont, inner, cellPad, doubleSep, false, false, false));
        table.addCell(totalValueCell(formatMoney(totais.totalPrincipalMaisCorrecao()), baseFont, inner, cellPad, doubleSep, false, false, false));
        table.addCell(emptyDataCell(inner, cellPad, doubleSep, false, true, false));

        table.addCell(totalLabelCell(honor.formatLabelHonorarios(), baseFont, inner, cellPad, doubleSep, true, false, false));
        for (int i = 0; i < 5; i++) {
            table.addCell(emptyDataCell(inner, cellPad, doubleSep, false, false, false));
        }
        table.addCell(dataCell(formatMoney(totais.honorarios()), baseFont, inner, TextAlignment.RIGHT, false, HONOR_COLOR, cellPad, doubleSep, false, false, false));
        table.addCell(emptyDataCell(inner, cellPad, doubleSep, false, true, false));

        table.addCell(totalLabelCell("Valor a Receber", baseFont, inner, cellPad, doubleSep, true, false, true));
        for (int i = 0; i < 5; i++) {
            table.addCell(emptyDataCell(inner, cellPad, doubleSep, false, false, true));
        }
        table.addCell(dataCell(formatMoney(totais.valorReceber()), baseFont + 0.5f, inner, TextAlignment.RIGHT, true, null, cellPad, doubleSep, false, false, true));
        table.addCell(emptyDataCell(inner, cellPad, doubleSep, false, true, true));

        return table;
    }

    private Cell buildLogoCell(float cellPad) {
        Cell cell = new Cell(LOGO_ROWSPAN, 2)
                .setVerticalAlignment(VerticalAlignment.MIDDLE)
                .setTextAlignment(TextAlignment.CENTER)
                .setPadding(cellPad);
        cell.setBorderTop(outerBorder());
        cell.setBorderBottom(outerBorder());
        cell.setBorderRight(outerBorder());
        cell.setBorderLeft(innerBorder());

        Image logo = loadLogo();
        if (logo != null) {
            logo.setAutoScale(false);
            logo.scaleToFit(LOGO_MAX_WIDTH, LOGO_MAX_HEIGHT);
            logo.setHorizontalAlignment(HorizontalAlignment.CENTER);
            cell.add(logo);
        }
        return cell;
    }

    private Cell headerLabelCell(String text, float fontSize, float pad, boolean topOuter, boolean bottomOuter,
            boolean leftOuter, boolean rightOuter) {
        Cell cell = new Cell(1, 6)
                .add(new Paragraph(text).setFontSize(fontSize).setBold().setMargin(0))
                .setVerticalAlignment(VerticalAlignment.MIDDLE)
                .setPadding(pad);
        applyBlockBorder(cell, topOuter, bottomOuter, leftOuter, rightOuter);
        return cell;
    }

    private void addDateGridRows(Table table, float fontSize, float pad) {
        List<Map.Entry<String, LocalDate>> entries =
                new ArrayList<>(ExcelResumoGeralHelper.DATAS_VENCIMENTO.entrySet());
        int idx = 0;
        int rowIndex = 0;
        int totalDateRows = (int) Math.ceil(entries.size() / 3.0);

        while (idx < entries.size()) {
            boolean topOuter = rowIndex == 0;
            boolean bottomOuter = rowIndex == totalDateRows - 1;
            for (int pair = 0; pair < 3; pair++) {
                if (idx < entries.size()) {
                    Map.Entry<String, LocalDate> entry = entries.get(idx++);
                    table.addCell(dateCell(entry.getKey(), fontSize, pad, topOuter, bottomOuter, true, pair == 0));
                    table.addCell(dateCell(
                            entry.getValue() != null ? entry.getValue().format(DATA_BR) : "",
                            fontSize,
                            pad,
                            topOuter,
                            bottomOuter,
                            false,
                            false));
                } else {
                    table.addCell(dateCell("", fontSize, pad, topOuter, bottomOuter, false, pair == 0));
                    table.addCell(dateCell("", fontSize, pad, topOuter, bottomOuter, false, false));
                }
            }
            rowIndex++;
        }
    }

    private Cell dateCell(String text, float fontSize, float pad, boolean topOuter, boolean bottomOuter,
            boolean bold, boolean leftOuter) {
        Paragraph p = new Paragraph(text).setFontSize(fontSize).setMargin(0);
        if (bold) {
            p.setBold();
        }
        Cell cell = new Cell()
                .add(p)
                .setPadding(pad)
                .setTextAlignment(bold ? TextAlignment.LEFT : TextAlignment.CENTER)
                .setVerticalAlignment(VerticalAlignment.MIDDLE);
        applyBlockBorder(cell, topOuter, bottomOuter, leftOuter, false);
        return cell;
    }

    private void addTableHeaderRow(Table table, float fontSize, float pad) {
        String[] headers = {
                "Calendário",
                "Valores Restituidos / Pagos",
                "Valor Devido  e ou a Restituir",
                "Valor Principal a ser Restituido pela PGFN ao Contribuinte",
                "SELIC Acumulada - RFB",
                "Valor Correção R$",
                "Principal + Correção Valores a Receber",
                "Observações"
        };
        SolidBorder inner = innerBorder();
        for (int i = 0; i < headers.length; i++) {
            Cell cell = new Cell()
                    .add(new Paragraph(headers[i]).setFontSize(fontSize).setBold().setMargin(0))
                    .setBackgroundColor(HEADER_BG)
                    .setTextAlignment(TextAlignment.CENTER)
                    .setVerticalAlignment(VerticalAlignment.MIDDLE)
                    .setPadding(pad)
                    .setBorder(inner);
            cell.setBorderTop(outerBorder());
            cell.setBorderBottom(outerBorder());
            if (i == 0) {
                cell.setBorderLeft(outerBorder());
            }
            if (i == COLS - 1) {
                cell.setBorderRight(outerBorder());
            }
            table.addCell(cell);
        }
    }

    private Table buildFooterTable(ResumoGeralMontagemResult montagem, float fontSize, float pad) {
        Table footer = new Table(UnitValue.createPercentArray(COL_WIDTHS))
                .useAllAvailableWidth()
                .setMarginTop(6);

        Cell respLabel = new Cell(2, 1)
                .add(new Paragraph(ExcelResumoGeralHelper.RODAPE_LABEL).setFontSize(fontSize).setBold().setMargin(0))
                .setVerticalAlignment(VerticalAlignment.MIDDLE)
                .setPadding(pad);
        respLabel.setBorderTop(outerBorder());
        respLabel.setBorderBottom(outerBorder());
        respLabel.setBorderLeft(outerBorder());
        respLabel.setBorderRight(innerBorder());
        footer.addCell(respLabel);

        Cell nameCell = new Cell(1, 7)
                .add(new Paragraph(ExcelResumoGeralHelper.RODAPE_NOME).setFontSize(fontSize).setMargin(0))
                .setVerticalAlignment(VerticalAlignment.MIDDLE)
                .setPadding(pad);
        nameCell.setBorderTop(outerBorder());
        nameCell.setBorderBottom(innerBorder());
        nameCell.setBorderLeft(innerBorder());
        nameCell.setBorderRight(outerBorder());
        footer.addCell(nameCell);

        Cell econCell = new Cell(1, 6)
                .add(new Paragraph(ExcelResumoGeralHelper.RODAPE_ECONOMISTA).setFontSize(fontSize).setMargin(0))
                .setVerticalAlignment(VerticalAlignment.MIDDLE)
                .setPadding(pad);
        econCell.setBorderTop(innerBorder());
        econCell.setBorderBottom(outerBorder());
        econCell.setBorderLeft(innerBorder());
        econCell.setBorderRight(innerBorder());
        footer.addCell(econCell);

        var geracao = montagem.dataGeracao() != null ? montagem.dataGeracao().format(DATA_HORA_BR) : "";
        Cell dateCell = new Cell()
                .add(new Paragraph(geracao).setFontSize(fontSize).setMargin(0))
                .setTextAlignment(TextAlignment.RIGHT)
                .setVerticalAlignment(VerticalAlignment.BOTTOM)
                .setPadding(pad);
        dateCell.setBorderTop(innerBorder());
        dateCell.setBorderBottom(outerBorder());
        dateCell.setBorderLeft(innerBorder());
        dateCell.setBorderRight(outerBorder());
        footer.addCell(dateCell);

        return footer;
    }

    private Cell dataCell(String text, float fontSize, SolidBorder border, TextAlignment align,
            boolean bold, DeviceRgb color, float pad, Border rowSeparator,
            boolean leftOuter, boolean rightOuter, boolean bottomOuter) {
        Paragraph p = new Paragraph(text != null ? text : "").setFontSize(fontSize).setMargin(0);
        if (bold) {
            p.setBold();
        }
        if (color != null) {
            p.setFontColor(color);
        }
        Cell cell = new Cell().add(p)
                .setTextAlignment(align)
                .setVerticalAlignment(VerticalAlignment.MIDDLE)
                .setPadding(pad)
                .setBorder(border);
        if (rowSeparator != null) {
            cell.setBorderTop(rowSeparator);
            cell.setBorderBottom(rowSeparator);
        }
        if (leftOuter) {
            cell.setBorderLeft(outerBorder());
        }
        if (rightOuter) {
            cell.setBorderRight(outerBorder());
        }
        if (bottomOuter) {
            cell.setBorderBottom(outerBorder());
        }
        return cell;
    }

    private Cell totalLabelCell(String text, float fontSize, SolidBorder border, float pad, Border rowSeparator,
            boolean leftOuter, boolean rightOuter, boolean bottomOuter) {
        Cell cell = new Cell()
                .add(new Paragraph(text).setFontSize(fontSize).setBold().setMargin(0))
                .setPadding(pad)
                .setBorder(border);
        cell.setBorderTop(rowSeparator);
        cell.setBorderBottom(rowSeparator);
        if (leftOuter) {
            cell.setBorderLeft(outerBorder());
        }
        if (rightOuter) {
            cell.setBorderRight(outerBorder());
        }
        if (bottomOuter) {
            cell.setBorderBottom(outerBorder());
        }
        return cell;
    }

    private Cell totalValueCell(String text, float fontSize, SolidBorder border, float pad, Border rowSeparator,
            boolean leftOuter, boolean rightOuter, boolean bottomOuter) {
        Cell cell = dataCell(text, fontSize, border, TextAlignment.RIGHT, false, null, pad, rowSeparator,
                leftOuter, rightOuter, bottomOuter);
        cell.setBackgroundColor(TOTAL_BG);
        return cell;
    }

    private Cell emptyDataCell(SolidBorder border, float pad, Border rowSeparator,
            boolean leftOuter, boolean rightOuter, boolean bottomOuter) {
        Cell cell = new Cell().setPadding(pad).setBorder(border);
        cell.setBorderTop(rowSeparator);
        cell.setBorderBottom(rowSeparator);
        if (leftOuter) {
            cell.setBorderLeft(outerBorder());
        }
        if (rightOuter) {
            cell.setBorderRight(outerBorder());
        }
        if (bottomOuter) {
            cell.setBorderBottom(outerBorder());
        }
        return cell;
    }

    private void applyBlockBorder(Cell cell, boolean topOuter, boolean bottomOuter, boolean leftOuter, boolean rightOuter) {
        cell.setBorderTop(topOuter ? outerBorder() : innerBorder());
        cell.setBorderBottom(bottomOuter ? outerBorder() : innerBorder());
        cell.setBorderLeft(leftOuter ? outerBorder() : innerBorder());
        cell.setBorderRight(rightOuter ? outerBorder() : innerBorder());
    }

    private SolidBorder outerBorder() {
        return new SolidBorder(OUTER);
    }

    private SolidBorder innerBorder() {
        return new SolidBorder(INNER);
    }

    private Image loadLogo() {
        try {
            byte[] bytes = new ClassPathResource(LOGO_CLASSPATH).getInputStream().readAllBytes();
            return new Image(ImageDataFactory.create(bytes));
        } catch (Exception e) {
            log.warn("Logo Origium indisponível para PDF: {}", e.getMessage());
            return null;
        }
    }

    private static String formatMoney(BigDecimal value) {
        if (value == null || value.abs().compareTo(new BigDecimal("0.005")) < 0) {
            return "0,00";
        }
        DecimalFormatSymbols symbols = new DecimalFormatSymbols(new Locale("pt", "BR"));
        DecimalFormat df = new DecimalFormat("#,##0.00", symbols);
        return df.format(value.setScale(2, RoundingMode.HALF_UP));
    }

    private static String formatPrincipal(BigDecimal principal) {
        if (principal == null || principal.compareTo(BigDecimal.ZERO) <= 0) {
            return "R$ -";
        }
        return formatMoney(principal);
    }

    private static String formatCorrecao(BigDecimal principal, BigDecimal valor) {
        if (principal == null || principal.compareTo(BigDecimal.ZERO) <= 0) {
            return "R$ -";
        }
        return formatMoney(valor);
    }

    private static String formatPercent(BigDecimal principal, BigDecimal taxa) {
        if (principal == null || principal.compareTo(BigDecimal.ZERO) <= 0
                || taxa == null || taxa.abs().compareTo(new BigDecimal("0.0001")) < 0) {
            return "-";
        }
        return String.format(Locale.forLanguageTag("pt-BR"), "%,.2f%%", taxa.setScale(2, RoundingMode.HALF_UP))
                .replace('\u00a0', '.');
    }

    private static String formatCpf(String cpf) {
        if (cpf == null) {
            return "";
        }
        String digits = cpf.replaceAll("\\D", "");
        if (digits.length() != 11) {
            return cpf;
        }
        return String.format("%s.%s.%s-%s",
                digits.substring(0, 3),
                digits.substring(3, 6),
                digits.substring(6, 9),
                digits.substring(9));
    }

    private static String nullSafe(String value) {
        return value != null ? value : "";
    }

    private static String normalizeName(String nome) {
        if (nome == null || nome.isBlank()) {
            return "SEM_NOME";
        }
        String normalized = Normalizer.normalize(nome.trim().toUpperCase(Locale.ROOT), Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");
        normalized = normalized.replaceAll("\\s+", "_");
        normalized = normalized.replaceAll("[^A-Z0-9_]", "");
        normalized = normalized.replaceAll("_{2,}", "_");
        normalized = normalized.replaceAll("^_|_$", "");
        return normalized.isEmpty() ? "SEM_NOME" : normalized;
    }

    private java.time.LocalDateTime montagemNow() {
        return java.time.LocalDateTime.now(java.time.ZoneId.of("America/Sao_Paulo"));
    }
}
