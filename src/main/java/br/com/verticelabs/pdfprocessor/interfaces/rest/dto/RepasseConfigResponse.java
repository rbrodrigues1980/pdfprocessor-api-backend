package br.com.verticelabs.pdfprocessor.interfaces.rest.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record RepasseConfigResponse(
        BigDecimal valorUnitario,
        int anoBase,
        int anoAtual,
        Instant vigenciaDe,
        Instant updatedAt,
        String updatedBy,
        boolean fromDb,
        Long pendentesAtualizados
) {}
