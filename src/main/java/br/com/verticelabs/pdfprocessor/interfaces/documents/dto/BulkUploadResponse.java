package br.com.verticelabs.pdfprocessor.interfaces.documents.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class BulkUploadResponse {
    private String cpf;
    private Integer totalArquivos;
    private Integer sucessos;
    private Integer falhas;
    private List<BulkUploadItemResponse> resultados;
}

