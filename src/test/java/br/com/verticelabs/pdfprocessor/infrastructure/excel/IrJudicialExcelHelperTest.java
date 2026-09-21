package br.com.verticelabs.pdfprocessor.infrastructure.excel;

import br.com.verticelabs.pdfprocessor.interfaces.consolidation.dto.ConsolidationRow;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IrJudicialExcelHelperTest {

    @Test
    void naoGeraAbaSemRubricasJudiciais() {
        List<ConsolidationRow> rubricas = List.of(
                row("1090", null, map("2018-01", "100.00")));
        assertFalse(IrJudicialExcelHelper.deveGerarAba(rubricas));
        assertFalse(IrJudicialExcelHelper.deveGerarAba(List.of()));
        assertFalse(IrJudicialExcelHelper.deveGerarAba(null));
    }

    @Test
    void geraAbaSeSo4326TiverValor() {
        List<ConsolidationRow> rubricas = List.of(
                row("4326", "IMPOSTO RENDA ACAO JUDICIAL", map("2018-03", "150.10")),
                row("1090", null, map("2018-01", "100.00")));
        assertTrue(IrJudicialExcelHelper.deveGerarAba(rubricas));
    }

    @Test
    void geraAbaSeSo4426TiverValor() {
        List<ConsolidationRow> rubricas = List.of(
                row("4426", "IR AB. AN. FUNCEF AC. JUDIC", map("2018-11", "88.00")));
        assertTrue(IrJudicialExcelHelper.deveGerarAba(rubricas));
    }

    @Test
    void somaSimplesDoAnoIgnoraZerosETotaisPorAnoFuncef() {
        Map<String, BigDecimal> valores = map(
                "2018-01", "10.00",
                "2018-02", "20.50",
                "2018-12", "5.00",
                "2019-01", "99.00");
        ConsolidationRow judicial = ConsolidationRow.builder()
                .codigo("4326")
                .descricao("IMPOSTO RENDA ACAO JUDICIAL")
                .valores(valores)
                .totaisPorAno(Map.of("2018", new BigDecimal("20.50")))
                .build();

        assertEquals(new BigDecimal("35.50"),
                IrJudicialExcelHelper.totalAno(List.of(judicial), "4326", "2018"));
        assertEquals(new BigDecimal("99.00"),
                IrJudicialExcelHelper.totalAno(List.of(judicial), "4326", "2019"));
        assertNull(IrJudicialExcelHelper.totalAno(List.of(judicial), "4326", "2017"));
        assertNull(IrJudicialExcelHelper.totalAno(List.of(judicial), "4426", "2018"));
    }

    @Test
    void layoutExcelDuasLinhasFixasAnosNaOrdemECelulaVazia() throws IOException {
        List<ConsolidationRow> rubricas = List.of(
                row("4326", "IMPOSTO RENDA ACAO JUDICIAL", map(
                        "2018-01", "100.00",
                        "2018-06", "50.74")),
                row("1090", "OUTRA", map("2017-01", "1.00")));
        TreeSet<String> anos = new TreeSet<>(List.of("2017", "2018"));

        try (XSSFWorkbook wb = new XSSFWorkbook()) {
            Sheet sheet = wb.createSheet(IrJudicialExcelHelper.NOME_ABA);
            IrJudicialExcelHelper.preencher(
                    sheet, rubricas, anos,
                    wb.createCellStyle(), wb.createCellStyle(), wb.createCellStyle());

            assertEquals("IR Judicial", sheet.getSheetName());
            Row header = sheet.getRow(0);
            assertEquals("CÓDIGO", header.getCell(0).getStringCellValue());
            assertEquals("DESCRIÇÃO", header.getCell(1).getStringCellValue());
            assertEquals("2017", header.getCell(2).getStringCellValue());
            assertEquals("2018", header.getCell(3).getStringCellValue());

            Row r4326 = sheet.getRow(1);
            assertEquals("4326", r4326.getCell(0).getStringCellValue());
            assertEquals("IMPOSTO RENDA ACAO JUDICIAL", r4326.getCell(1).getStringCellValue());
            assertEquals(CellType.BLANK, r4326.getCell(2).getCellType());
            assertEquals(CellType.NUMERIC, r4326.getCell(3).getCellType());
            assertEquals(150.74, r4326.getCell(3).getNumericCellValue(), 0.001);

            Row r4426 = sheet.getRow(2);
            assertEquals("4426", r4426.getCell(0).getStringCellValue());
            assertEquals("IR AB. AN. FUNCEF AC. JUDIC", r4426.getCell(1).getStringCellValue());
            assertEquals(CellType.BLANK, r4426.getCell(2).getCellType());
            assertEquals(CellType.BLANK, r4426.getCell(3).getCellType());
            assertEquals(2, sheet.getLastRowNum());
        }
    }

    @Test
    void descricaoPadraoQuandoLinhaNaoVeioNaConsolidacao() {
        assertEquals("IR AB. AN. FUNCEF AC. JUDIC",
                IrJudicialExcelHelper.descricao(List.of(), IrJudicialExcelHelper.LINHAS.get(1)));
    }

    private static ConsolidationRow row(String codigo, String descricao, Map<String, BigDecimal> valores) {
        return ConsolidationRow.builder()
                .codigo(codigo)
                .descricao(descricao)
                .valores(valores)
                .build();
    }

    private static Map<String, BigDecimal> map(String... kv) {
        Map<String, BigDecimal> m = new HashMap<>();
        for (int i = 0; i < kv.length; i += 2) {
            m.put(kv[i], new BigDecimal(kv[i + 1]));
        }
        return m;
    }
}
