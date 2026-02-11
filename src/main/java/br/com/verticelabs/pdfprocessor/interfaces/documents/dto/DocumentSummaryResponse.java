package br.com.verticelabs.pdfprocessor.interfaces.documents.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class DocumentSummaryResponse {
    private String documentId;
    private Long entriesCount;
    private List<DocumentRubricaSummary> rubricasResumo;
}

