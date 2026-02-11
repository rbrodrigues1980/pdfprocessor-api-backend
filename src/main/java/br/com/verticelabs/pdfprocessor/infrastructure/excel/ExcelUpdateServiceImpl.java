package br.com.verticelabs.pdfprocessor.infrastructure.excel;

import br.com.verticelabs.pdfprocessor.domain.service.ExcelUpdateService;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

@Slf4j
@Service
public class ExcelUpdateServiceImpl implements ExcelUpdateService {

    // Coluna onde fica o "Imposto Devido" (coluna C, índice 2)
    private static final int COL_IMPOSTO_DEVIDO = 2; // 0-based index (coluna C)
    // Coluna onde fica o label "Imposto Devido" (coluna B, índice 1)
    private static final int COL_LABEL_IMPOSTO_DEVIDO = 1; // 0-based index (coluna B)

    @Override
    public Mono<byte[]> updateImpostoDevido(byte[] excelBytes, Double impostoDevido) {
        log.info("Atualizando Imposto Devido em todas as abas de anos com valor: {}", impostoDevido);
        
        if (excelBytes == null || excelBytes.length == 0) {
            return Mono.error(new IllegalArgumentException("Excel não pode ser nulo ou vazio"));
        }
        
        return Mono.fromCallable(() -> {
            log.info("Carregando workbook existente ({} bytes)", excelBytes.length);
            
            Workbook workbook;
            try (ByteArrayInputStream bis = new ByteArrayInputStream(excelBytes)) {
                workbook = new XSSFWorkbook(bis);
            }
            
            try {
                // Iterar sobre todas as abas (exceto "Consolidação")
                int abasAtualizadas = 0;
                for (int i = 0; i < workbook.getNumberOfSheets(); i++) {
                    Sheet sheet = workbook.getSheetAt(i);
                    String sheetName = sheet.getSheetName();
                    
                    // Pular a aba "Consolidação"
                    if ("Consolidação".equalsIgnoreCase(sheetName)) {
                        log.debug("Pulando aba 'Consolidação'");
                        continue;
                    }
                    
                    // Verificar se é uma aba de ano (contém apenas números)
                    if (sheetName.matches("\\d{4}")) {
                        log.info("Processando aba '{}'", sheetName);
                        
                        // Encontrar a última linha preenchida (linha de totais)
                        int lastRowNum = findLastFilledRow(sheet);
                        int impostoRowNum = lastRowNum + 1;
                        
                        log.debug("Última linha preenchida na aba '{}': {} (índice {})", 
                                sheetName, lastRowNum + 1, lastRowNum);
                        log.debug("Inserindo Imposto Devido na linha {} (índice {})", 
                                impostoRowNum + 1, impostoRowNum);
                        
                        // Criar ou obter linha para Imposto Devido
                        Row impostoRow = sheet.getRow(impostoRowNum);
                        if (impostoRow == null) {
                            impostoRow = sheet.createRow(impostoRowNum);
                        }
                        
                        // Coluna B: Label "Imposto Devido"
                        Cell labelCell = impostoRow.getCell(COL_LABEL_IMPOSTO_DEVIDO);
                        if (labelCell == null) {
                            labelCell = impostoRow.createCell(COL_LABEL_IMPOSTO_DEVIDO);
                        }
                        labelCell.setCellValue("Imposto Devido");
                        
                        // Aplicar estilo ao label (mesmo estilo dos totais)
                        CellStyle totalStyle = createTotalStyle(workbook);
                        labelCell.setCellStyle(totalStyle);
                        
                        // Coluna C: Valor do Imposto Devido
                        Cell valueCell = impostoRow.getCell(COL_IMPOSTO_DEVIDO);
                        if (valueCell == null) {
                            valueCell = impostoRow.createCell(COL_IMPOSTO_DEVIDO);
                        }
                        
                        // Aplicar estilo de número formatado (mesmo dos totais)
                        valueCell.setCellStyle(totalStyle);
                        valueCell.setCellValue(impostoDevido);
                        
                        log.info("✓ Imposto Devido inserido na aba '{}' na linha {} (célula C{})", 
                                sheetName, impostoRowNum + 1, impostoRowNum + 1);
                        abasAtualizadas++;
                    } else {
                        log.debug("Aba '{}' não é uma aba de ano, pulando...", sheetName);
                    }
                }
                
                log.info("Total de abas atualizadas: {}", abasAtualizadas);
                
                // Salvar workbook em bytes
                try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                    workbook.write(out);
                    byte[] result = out.toByteArray();
                    log.info("Workbook atualizado salvo. Tamanho: {} bytes", result.length);
                    return result;
                }
            } finally {
                workbook.close();
            }
        }).subscribeOn(Schedulers.boundedElastic());
    }

    /**
     * Encontra a última linha preenchida na planilha (linha de totais).
     * Procura pela linha que contém "TOTAL" na primeira coluna.
     */
    private int findLastFilledRow(Sheet sheet) {
        int lastRowNum = sheet.getLastRowNum();
        
        // Procurar pela linha que contém "TOTAL" na primeira coluna
        for (int i = lastRowNum; i >= 0; i--) {
            Row row = sheet.getRow(i);
            if (row != null) {
                Cell firstCell = row.getCell(0);
                if (firstCell != null) {
                    String cellValue = getCellValueAsString(firstCell);
                    if ("TOTAL".equalsIgnoreCase(cellValue)) {
                        log.debug("Linha de totais encontrada na linha {} (índice {})", i + 1, i);
                        return i;
                    }
                }
            }
        }
        
        // Se não encontrou "TOTAL", retorna a última linha preenchida
        log.debug("Linha 'TOTAL' não encontrada, usando última linha preenchida: {}", lastRowNum);
        return lastRowNum;
    }

    /**
     * Obtém o valor de uma célula como String.
     */
    private String getCellValueAsString(Cell cell) {
        if (cell == null) {
            return "";
        }
        
        switch (cell.getCellType()) {
            case STRING:
                return cell.getStringCellValue();
            case NUMERIC:
                if (org.apache.poi.ss.usermodel.DateUtil.isCellDateFormatted(cell)) {
                    return cell.getDateCellValue().toString();
                } else {
                    return String.valueOf(cell.getNumericCellValue());
                }
            case BOOLEAN:
                return String.valueOf(cell.getBooleanCellValue());
            case FORMULA:
                return cell.getCellFormula();
            default:
                return "";
        }
    }

    /**
     * Cria estilo para totais (usado para Imposto Devido).
     */
    private CellStyle createTotalStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        font.setFontName("Arial");
        font.setFontHeightInPoints((short) 10);
        style.setFont(font);
        style.setFillForegroundColor(IndexedColors.YELLOW.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setAlignment(HorizontalAlignment.RIGHT);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        style.setBorderTop(BorderStyle.DOUBLE);
        style.setBorderBottom(BorderStyle.DOUBLE);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        DataFormat format = workbook.createDataFormat();
        style.setDataFormat(format.getFormat("#,##0.00"));
        return style;
    }
}

