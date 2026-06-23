package br.com.verticelabs.pdfprocessor.interfaces.repasse.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Builder
public class MarkRepasseBulkPaidResponse {
    private int pagos;
    private BigDecimal valorTotal;
}
