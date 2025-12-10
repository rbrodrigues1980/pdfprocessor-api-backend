package br.com.verticelabs.pdfprocessor.interfaces.documents.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class DocumentRubricaSummary {
    private String codigo;
    private Long quantidade;
    private Double total;
}

