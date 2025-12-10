package br.com.verticelabs.pdfprocessor.infrastructure.excel;

import br.com.verticelabs.pdfprocessor.domain.model.Rubrica;
import br.com.verticelabs.pdfprocessor.domain.service.ExcelService;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.io.ByteArrayOutputStream;
import java.util.List;

@Service
public class ExcelServiceImpl implements ExcelService {

    @Override
    public Mono<byte[]> generateExcel(List<Rubrica> rubricas) {
        return Mono.fromCallable(() -> {
            try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                Sheet sheet = workbook.createSheet("Rubricas");

                // Header
                Row headerRow = sheet.createRow(0);
                headerRow.createCell(0).setCellValue("Código");
                headerRow.createCell(1).setCellValue("Descrição");
                headerRow.createCell(2).setCellValue("Categoria");
                headerRow.createCell(3).setCellValue("Ativo");

                // Data
                int rowIdx = 1;
                for (Rubrica rubrica : rubricas) {
                    Row row = sheet.createRow(rowIdx++);
                    row.createCell(0).setCellValue(rubrica.getCodigo() != null ? rubrica.getCodigo() : "");
                    row.createCell(1).setCellValue(rubrica.getDescricao() != null ? rubrica.getDescricao() : "");
                    row.createCell(2).setCellValue(rubrica.getCategoria() != null ? rubrica.getCategoria() : "");
                    row.createCell(3).setCellValue(rubrica.getAtivo() != null && rubrica.getAtivo() ? "Sim" : "Não");
                }

                workbook.write(out);
                return out.toByteArray();
            }
        }).subscribeOn(Schedulers.boundedElastic());
    }
}
