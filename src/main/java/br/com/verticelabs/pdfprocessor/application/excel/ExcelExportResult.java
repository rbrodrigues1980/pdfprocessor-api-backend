package br.com.verticelabs.pdfprocessor.application.excel;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class ExcelExportResult {
    private byte[] bytes;
    private String filename;
}

