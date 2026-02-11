package br.com.verticelabs.pdfprocessor.interfaces.documents.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class DocumentListResponse {
    private String cpf;
    private List<DocumentListItemResponse> documentos;
}

