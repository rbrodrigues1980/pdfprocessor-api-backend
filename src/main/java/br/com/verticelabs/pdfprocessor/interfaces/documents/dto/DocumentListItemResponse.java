package br.com.verticelabs.pdfprocessor.interfaces.documents.dto;

import br.com.verticelabs.pdfprocessor.domain.model.DocumentStatus;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.List;

@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class DocumentListItemResponse {
    private String id;
    private Integer ano;
    private DocumentStatus status;
    private String tipo; // "CAIXA", "FUNCEF", "CAIXA_FUNCEF" ou "IRPF" (para INCOME_TAX)
    private List<String> mesesDetectados; // Formato: ["2017-01", "2017-02"]
    private Instant dataUpload; // Data do upload
    private Instant dataProcessamento; // Data do processamento
    private Long totalEntries; // Número total de entries extraídas

    // === Campos de progresso (presentes quando status === PROCESSING) ===

    /** Total de páginas do PDF (null se ainda não detectado) */
    private Integer totalPages;

    /** Número de páginas já processadas */
    private Integer pagesProcessed;

    /** Percentual de progresso (0-100) */
    private Integer progressPercent;

    /** Última mensagem de evento (ex: "Gemini [flash] extraiu 17 rubricas em 22s. competencia=01/2016") */
    private String lastEventMessage;

    /** Tipo do último evento (ex: "GEMINI_EXTRACTION_COMPLETED") */
    private String lastEventType;

    /** Total de eventos no processingLog */
    private Integer eventsCount;
}

