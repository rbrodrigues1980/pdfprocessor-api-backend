package br.com.verticelabs.pdfprocessor.domain.model;

/**
 * Tipos de eventos que ocorrem durante o processamento de um documento PDF.
 */
public enum ProcessingEventType {

    // === Início/Fim ===
    PROCESSING_STARTED,
    PROCESSING_COMPLETED,
    PROCESSING_FAILED,

    // === Extração de texto ===
    TEXT_EXTRACTED,
    TEXT_UNREADABLE,
    TEXT_TOO_SHORT,

    // === Gemini AI ===
    GEMINI_EXTRACTION_STARTED,
    GEMINI_EXTRACTION_COMPLETED,
    GEMINI_EXTRACTION_FAILED,

    // === Validação ===
    VALIDATION_PASSED,
    VALIDATION_FAILED,

    // === Cross-validation ===
    CROSS_VALIDATION_STARTED,
    CROSS_VALIDATION_COMPLETED,

    // === Escalação ===
    ESCALATION_TO_PRO,
    ESCALATION_COMPLETED,
    ESCALATION_FAILED,

    // === Multi-page retry ===
    MULTIPAGE_RETRY_STARTED,
    MULTIPAGE_RETRY_COMPLETED,
    MULTIPAGE_RETRY_FAILED,

    // === Resultado ===
    ENTRIES_EXTRACTED,
    ENTRIES_SAVED,

    // === Rubricas ===
    RUBRICA_NOT_FOUND
}
