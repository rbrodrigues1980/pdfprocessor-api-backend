package br.com.verticelabs.pdfprocessor.infrastructure.excel;

import br.com.verticelabs.pdfprocessor.interfaces.consolidation.dto.ConsolidationRow;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Aba Excel "IR Judicial": rubricas 4326 e 4426 somadas por ano-calendário.
 * Soma simples dos 12 meses — sem regra Funcef de 13º/FEV+NOV.
 */
public final class IrJudicialExcelHelper {

    public static final String NOME_ABA = "IR Judicial";
    public static final String CODIGO_4326 = "4326";
    public static final String CODIGO_4426 = "4426";
    public static final String DESCRICAO_4326 = "IMPOSTO RENDA ACAO JUDICIAL";
    public static final String DESCRICAO_4426 = "IR AB. AN. FUNCEF AC. JUDIC";

    public static final List<Linha> LINHAS = List.of(
            new Linha(CODIGO_4326, DESCRICAO_4326),
            new Linha(CODIGO_4426, DESCRICAO_4426));

    private static final RoundingMode RM = RoundingMode.HALF_UP;

    private IrJudicialExcelHelper() {
    }

    public record Linha(String codigo, String descricaoPadrao) {
    }

    public static boolean deveGerarAba(List<ConsolidationRow> rubricas) {
        if (rubricas == null || rubricas.isEmpty()) {
            return false;
        }
        for (Linha linha : LINHAS) {
            ConsolidationRow row = encontrar(rubricas, linha.codigo());
            if (row != null && temQualquerValor(row)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Soma Jan–Dez do ano. {@code null} quando não há movimento (célula vazia no Excel).
     */
    public static BigDecimal totalAno(List<ConsolidationRow> rubricas, String codigo, String ano) {
        ConsolidationRow row = encontrar(rubricas, codigo);
        if (row == null || ano == null || ano.isBlank()) {
            return null;
        }
        Map<String, BigDecimal> valores = row.getValores() != null ? row.getValores() : Collections.emptyMap();
        BigDecimal soma = BigDecimal.ZERO;
        boolean temValor = false;
        for (int mes = 1; mes <= 12; mes++) {
            String ref = ano + "-" + String.format("%02d", mes);
            BigDecimal v = valores.get(ref);
            if (v != null && v.compareTo(BigDecimal.ZERO) != 0) {
                soma = soma.add(v);
                temValor = true;
            }
        }
        if (!temValor) {
            return null;
        }
        return soma.setScale(2, RM);
    }

    public static String descricao(List<ConsolidationRow> rubricas, Linha linha) {
        ConsolidationRow row = encontrar(rubricas, linha.codigo());
        if (row != null && row.getDescricao() != null && !row.getDescricao().isBlank()) {
            return row.getDescricao().trim();
        }
        return linha.descricaoPadrao();
    }

    public static void preencher(
            Sheet sheet,
            List<ConsolidationRow> rubricas,
            Collection<String> anos,
            CellStyle headerStyle,
            CellStyle numberStyle,
            CellStyle defaultStyle) {

        int colCount = 2 + (anos != null ? anos.size() : 0);
        Row header = sheet.createRow(0);
        Cell codigoH = header.createCell(0);
        codigoH.setCellValue("CÓDIGO");
        codigoH.setCellStyle(headerStyle);
        Cell descH = header.createCell(1);
        descH.setCellValue("DESCRIÇÃO");
        descH.setCellStyle(headerStyle);
        int col = 2;
        if (anos != null) {
            for (String ano : anos) {
                Cell anoH = header.createCell(col++);
                anoH.setCellValue(ano);
                anoH.setCellStyle(headerStyle);
            }
        }

        int rowIdx = 1;
        for (Linha linha : LINHAS) {
            Row data = sheet.createRow(rowIdx++);
            Cell codigoC = data.createCell(0);
            codigoC.setCellValue(linha.codigo());
            codigoC.setCellStyle(defaultStyle);
            Cell descC = data.createCell(1);
            descC.setCellValue(descricao(rubricas, linha));
            descC.setCellStyle(defaultStyle);
            int anoCol = 2;
            if (anos != null) {
                for (String ano : anos) {
                    Cell valorC = data.createCell(anoCol++);
                    BigDecimal total = totalAno(rubricas, linha.codigo(), ano);
                    if (total != null) {
                        valorC.setCellValue(total.doubleValue());
                        valorC.setCellStyle(numberStyle);
                    } else {
                        valorC.setCellStyle(defaultStyle);
                    }
                }
            }
        }

        sheet.setColumnWidth(0, 4000);
        sheet.setColumnWidth(1, 14000);
        for (int i = 2; i < colCount; i++) {
            sheet.setColumnWidth(i, 3500);
        }
        sheet.createFreezePane(2, 1);
    }

    static ConsolidationRow encontrar(List<ConsolidationRow> rubricas, String codigo) {
        if (rubricas == null || codigo == null) {
            return null;
        }
        for (ConsolidationRow row : rubricas) {
            if (row != null && row.getCodigo() != null && codigo.equals(row.getCodigo().trim())) {
                return row;
            }
        }
        return null;
    }

    private static boolean temQualquerValor(ConsolidationRow row) {
        Map<String, BigDecimal> valores = row.getValores();
        if (valores == null || valores.isEmpty()) {
            return false;
        }
        for (BigDecimal v : valores.values()) {
            if (v != null && v.compareTo(BigDecimal.ZERO) != 0) {
                return true;
            }
        }
        return false;
    }
}
