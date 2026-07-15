package br.com.verticelabs.pdfprocessor.interfaces.consolidation.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConsolidationRow {
    private String codigo;
    private String descricao;
    private Map<String, BigDecimal> valores; // formato: "2017-01" -> 424.10
    /** Totais por ano já com regra de 13º Funcef aplicada (ex.: "2017" -> 98.20). */
    private Map<String, BigDecimal> totaisPorAno;
    private BigDecimal total;
}
