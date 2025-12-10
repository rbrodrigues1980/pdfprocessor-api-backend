package br.com.verticelabs.pdfprocessor.interfaces.documents.dto;

import br.com.verticelabs.pdfprocessor.domain.model.DocumentStatus;
import br.com.verticelabs.pdfprocessor.domain.model.DocumentType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProcessDocumentResponse {
    private String documentId;
    private DocumentStatus status;
    private String message;
    private Long entries; // Número de entries criadas (opcional, apenas quando status = PROCESSED)
    private DocumentType tipoDocumento; // Tipo do documento (opcional, apenas quando status = PROCESSED)
}

