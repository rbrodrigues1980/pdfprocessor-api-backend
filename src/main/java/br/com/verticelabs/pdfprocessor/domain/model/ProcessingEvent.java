package br.com.verticelabs.pdfprocessor.domain.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Map;

/**
 * Evento de processamento de um documento PDF.
 * Armazenado como documento embarcado dentro do PayrollDocument (campo processingLog).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProcessingEvent {

    private Instant timestamp;

    private ProcessingEventType type;

    private ProcessingEventLevel level;

    /** Página do PDF (null se evento geral do documento) */
    private Integer page;

    /** Mensagem legível para o usuário */
    private String message;

    /** Dados extras estruturados (score, modelo IA, tempo, etc.) */
    private Map<String, Object> details;
}
