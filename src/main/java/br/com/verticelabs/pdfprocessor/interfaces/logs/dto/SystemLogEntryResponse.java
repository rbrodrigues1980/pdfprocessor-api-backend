package br.com.verticelabs.pdfprocessor.interfaces.logs.dto;

import java.time.Instant;
import java.util.Map;

public record SystemLogEntryResponse(
        String id,
        Instant timestamp,
        String level,
        String logger,
        String thread,
        String message,
        String exception,
        Map<String, String> context
) {
}
