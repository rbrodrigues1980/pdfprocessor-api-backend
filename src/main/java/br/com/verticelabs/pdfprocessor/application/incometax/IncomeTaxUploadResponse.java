package br.com.verticelabs.pdfprocessor.application.incometax;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class IncomeTaxUploadResponse {
    private String anoCalendario;
    private Double impostoDevido;
    private byte[] excelBytes;
    private String excelFilename;
}

