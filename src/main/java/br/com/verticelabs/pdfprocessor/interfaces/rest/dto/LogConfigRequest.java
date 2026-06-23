package br.com.verticelabs.pdfprocessor.interfaces.rest.dto;

import java.time.Instant;

public record LogConfigRequest(
        String retention
) {
}
