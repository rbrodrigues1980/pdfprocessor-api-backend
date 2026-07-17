package br.com.verticelabs.pdfprocessor.infrastructure.excel;

import br.com.verticelabs.pdfprocessor.application.excel.ClienteExcelReportRow;
import br.com.verticelabs.pdfprocessor.application.excel.ClientesExcelReportService;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.util.List;

@Slf4j
@Service
public class ClientesExcelReportServiceImpl implements ClientesExcelReportService {

    private static final String[] HEADERS = {
            "Nome do cliente",
            "CPF",
            "Entidade",
            "Status",
            "Percentual de honorários (%)",
            "Principal PGFN",
            "Principal + Correção"
    };

    @Override
    public Mono<byte[]> generate(List<ClienteExcelReportRow> rows, String filename) {
        return Mono.fromCallable(() -> buildWorkbook(rows != null ? rows : List.of()))
                .subscribeOn(Schedulers.boundedElastic())
                .doOnSuccess(bytes -> log.debug(
                        "Workbook relatório clientes gerado: {} bytes ({})",
                        bytes.length, filename));
    }

    private byte[] buildWorkbook(List<ClienteExcelReportRow> rows) throws Exception {
        try (Workbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {

            Sheet sheet = workbook.createSheet("Clientes");
            CellStyle headerStyle = createHeaderStyle(workbook);
            CellStyle textStyle = createTextStyle(workbook);
            CellStyle percentStyle = createPercentStyle(workbook);
            CellStyle moneyStyle = createMoneyStyle(workbook);

            Row headerRow = sheet.createRow(0);
            for (int i = 0; i < HEADERS.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(HEADERS[i]);
                cell.setCellStyle(headerStyle);
            }

            int rowIdx = 1;
            for (ClienteExcelReportRow rowData : rows) {
                Row row = sheet.createRow(rowIdx++);

                Cell nomeCell = row.createCell(0);
                nomeCell.setCellValue(nullToEmpty(rowData.nome()));
                nomeCell.setCellStyle(textStyle);

                Cell cpfCell = row.createCell(1);
                cpfCell.setCellValue(formatCpf(rowData.cpf()));
                cpfCell.setCellStyle(textStyle);

                Cell entidadeCell = row.createCell(2);
                entidadeCell.setCellValue(nullToEmpty(rowData.entidade()));
                entidadeCell.setCellStyle(textStyle);

                Cell statusCell = row.createCell(3);
                statusCell.setCellValue(nullToEmpty(rowData.status()));
                statusCell.setCellStyle(textStyle);

                Cell pctCell = row.createCell(4);
                pctCell.setCellValue(toDouble(rowData.percentualHonorarios()));
                pctCell.setCellStyle(percentStyle);

                Cell principalCell = row.createCell(5);
                principalCell.setCellValue(toDouble(rowData.totalPrincipalPgfn()));
                principalCell.setCellStyle(moneyStyle);

                Cell principalCorrecaoCell = row.createCell(6);
                principalCorrecaoCell.setCellValue(toDouble(rowData.totalPrincipalMaisCorrecao()));
                principalCorrecaoCell.setCellStyle(moneyStyle);
            }

            sheet.createFreezePane(0, 1);
            if (rows.size() > 0) {
                sheet.setAutoFilter(new CellRangeAddress(0, rows.size(), 0, HEADERS.length - 1));
            }

            sheet.setColumnWidth(0, 40 * 256);
            sheet.setColumnWidth(1, 18 * 256);
            sheet.setColumnWidth(2, 36 * 256);
            sheet.setColumnWidth(3, 52 * 256);
            sheet.setColumnWidth(4, 22 * 256);
            sheet.setColumnWidth(5, 18 * 256);
            sheet.setColumnWidth(6, 22 * 256);

            workbook.write(out);
            return out.toByteArray();
        }
    }

    private CellStyle createHeaderStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        font.setColor(IndexedColors.WHITE.getIndex());
        style.setFont(font);
        style.setFillForegroundColor(IndexedColors.DARK_BLUE.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setAlignment(HorizontalAlignment.CENTER);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        return style;
    }

    private CellStyle createTextStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        return style;
    }

    private CellStyle createPercentStyle(Workbook workbook) {
        CellStyle style = createTextStyle(workbook);
        // Valor já vem em exibição (ex.: 12.00). Evita formato % do Excel (que multiplica por 100).
        style.setDataFormat(workbook.createDataFormat().getFormat("0.00"));
        style.setAlignment(HorizontalAlignment.RIGHT);
        return style;
    }

    private CellStyle createMoneyStyle(Workbook workbook) {
        CellStyle style = createTextStyle(workbook);
        style.setDataFormat(workbook.createDataFormat().getFormat("#,##0.00"));
        style.setAlignment(HorizontalAlignment.RIGHT);
        return style;
    }

    static String formatCpf(String cpf) {
        if (cpf == null) {
            return "";
        }
        String digits = cpf.replaceAll("\\D", "");
        if (digits.length() != 11) {
            return cpf;
        }
        return digits.substring(0, 3) + "."
                + digits.substring(3, 6) + "."
                + digits.substring(6, 9) + "-"
                + digits.substring(9);
    }

    private static String nullToEmpty(String value) {
        return value != null ? value : "";
    }

    private static double toDouble(BigDecimal value) {
        return value != null ? value.doubleValue() : 0d;
    }
}
