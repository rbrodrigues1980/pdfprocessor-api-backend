package br.com.verticelabs.pdfprocessor.interfaces.documents.dto;

import br.com.verticelabs.pdfprocessor.domain.model.DocumentStatus;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.List;

@Data
@Builder
public class DocumentListItemResponse {
    private String id;
    private Integer ano;
    private DocumentStatus status;
    private String tipo; // "CAIXA", "FUNCEF", "CAIXA_FUNCEF" ou "IRPF" (para INCOME_TAX)
    private List<String> mesesDetectados; // Formato: ["2017-01", "2017-02"]
    private Instant dataUpload; // Data do upload
    private Instant dataProcessamento; // Data do processamento
    private Long totalEntries; // Número total de entries extraídas
}

