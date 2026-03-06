package br.com.verticelabs.pdfprocessor.interfaces.documents.dto;

import br.com.verticelabs.pdfprocessor.domain.model.DocumentStatus;
import br.com.verticelabs.pdfprocessor.domain.model.ProcessingEvent;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * DTO leve para polling do status de processamento.
 * Retorna apenas as informações necessárias para acompanhar o progresso em tempo real.
 */
@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ProcessingStatusResponse {
    private String documentId;
    private DocumentStatus status;
    private Integer totalPages;
    private String erro;
    private List<ProcessingEvent> processingLog;
}
