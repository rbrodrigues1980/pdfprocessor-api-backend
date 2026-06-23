package br.com.verticelabs.pdfprocessor.interfaces.rest.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record RepasseConfigRequest(
        BigDecimal valorUnitario,
        Integer anoBase,
        Instant vigenciaDe
) {}
