package br.com.verticelabs.pdfprocessor.application.excel;

import lombok.Value;

@Value
public class ResumoGeralPdfResult {
    byte[] bytes;
    String filename;
}
