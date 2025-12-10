package br.com.verticelabs.pdfprocessor.interfaces.documents.dto;

import br.com.verticelabs.pdfprocessor.domain.model.DocumentStatus;
import br.com.verticelabs.pdfprocessor.domain.model.DocumentType;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;

@Data
@Builder
public class DocumentResponse {
    private String id;
    private String cpf;
    private DocumentStatus status;
    private DocumentType tipo;
    private Integer ano;
    private Long entriesCount;
    private Instant dataUpload;
    private Instant dataProcessamento;
    private String erro;
}

