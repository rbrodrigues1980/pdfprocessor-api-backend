package br.com.verticelabs.pdfprocessor.interfaces.rest.dto;

import java.time.Instant;

public record LogConfigResponse(
        String retention,
        String retentionLabel,
        int retentionDays,
        boolean mongoLoggingEnabled,
        Instant updatedAt,
        String updatedBy,
        boolean fromDb
) {
}
