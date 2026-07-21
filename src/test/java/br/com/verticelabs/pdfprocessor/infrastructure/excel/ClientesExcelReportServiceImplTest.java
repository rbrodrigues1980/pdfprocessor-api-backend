package br.com.verticelabs.pdfprocessor.infrastructure.excel;

import br.com.verticelabs.pdfprocessor.application.excel.ClienteExcelReportRow;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("ClientesExcelReportServiceImpl")
class ClientesExcelReportServiceImplTest {

    private final ClientesExcelReportServiceImpl service = new ClientesExcelReportServiceImpl();

    @Test
    @DisplayName("gera planilha com cabeçalhos e linha de cliente")
    void geraPlanilhaComCabecalhosELinha() throws Exception {
        ClienteExcelReportRow row = new ClienteExcelReportRow(
                "CLIENTE TESTE",
                "12345678901",
                "AEA/AL — Associação",
                "Aguardando documentação complementar",
                "faltam contracheques 2016",
                new BigDecimal("12.00"),
                new BigDecimal("1000.50"),
                new BigDecimal("1500.75"));

        byte[] bytes = service.generate(List.of(row), "teste.xlsx").block();
        assertTrue(bytes != null && bytes.length > 0);

        try (Workbook workbook = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            Sheet sheet = workbook.getSheet("Clientes");
            Row header = sheet.getRow(0);
            assertEquals("Nome do cliente", header.getCell(0).getStringCellValue());
            assertEquals("CPF", header.getCell(1).getStringCellValue());
            assertEquals("Entidade", header.getCell(2).getStringCellValue());
            assertEquals("Status", header.getCell(3).getStringCellValue());
            assertEquals("Observações", header.getCell(4).getStringCellValue());
            assertEquals("Percentual de honorários (%)", header.getCell(5).getStringCellValue());
            assertEquals("Principal PGFN", header.getCell(6).getStringCellValue());
            assertEquals("Principal + Correção", header.getCell(7).getStringCellValue());

            Row data = sheet.getRow(1);
            assertEquals("CLIENTE TESTE", data.getCell(0).getStringCellValue());
            assertEquals("123.456.789-01", data.getCell(1).getStringCellValue());
            assertEquals("AEA/AL — Associação", data.getCell(2).getStringCellValue());
            assertEquals("Aguardando documentação complementar", data.getCell(3).getStringCellValue());
            assertEquals("faltam contracheques 2016", data.getCell(4).getStringCellValue());
            assertEquals(12.00d, data.getCell(5).getNumericCellValue(), 0.001);
            assertEquals(1000.50d, data.getCell(6).getNumericCellValue(), 0.001);
            assertEquals(1500.75d, data.getCell(7).getNumericCellValue(), 0.001);
        }
    }

    @Test
    @DisplayName("formatCpf mascara 11 dígitos")
    void formatCpfMascara() {
        assertEquals("123.456.789-01", ClientesExcelReportServiceImpl.formatCpf("12345678901"));
        assertEquals("abc", ClientesExcelReportServiceImpl.formatCpf("abc"));
        assertEquals("", ClientesExcelReportServiceImpl.formatCpf(null));
    }
}
