package br.com.verticelabs.pdfprocessor.interfaces.documents.dto;

import br.com.verticelabs.pdfprocessor.domain.model.DocumentStatus;
import br.com.verticelabs.pdfprocessor.domain.model.DocumentType;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class BulkUploadItemResponse {
    private String filename;
    private String documentId;
    private DocumentStatus status;
    private DocumentType tipoDetectado;
    private Boolean sucesso;
    private String erro; // Mensagem de erro se falhou
}

