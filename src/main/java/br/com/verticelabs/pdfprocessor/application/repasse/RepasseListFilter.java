package br.com.verticelabs.pdfprocessor.application.repasse;

import br.com.verticelabs.pdfprocessor.domain.model.RepasseStatus;

import java.time.Instant;

public record RepasseListFilter(
        RepasseStatus status,
        String mesReferencia,
        String tenantId,
        Instant validadoDe,
        Instant validadoAte,
        Instant pagoDe,
        Instant pagoAte) {
}
