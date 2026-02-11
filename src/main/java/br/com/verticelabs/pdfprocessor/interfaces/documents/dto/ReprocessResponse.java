package br.com.verticelabs.pdfprocessor.interfaces.documents.dto;

import br.com.verticelabs.pdfprocessor.domain.model.DocumentStatus;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ReprocessResponse {
    private String documentId;
    private DocumentStatus status;
    private String message;
}

