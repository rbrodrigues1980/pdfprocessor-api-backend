package br.com.verticelabs.pdfprocessor.interfaces.consolidation.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConsolidationRow {
    private String codigo;
    private String descricao;
    private Map<String, Double> valores; // formato: "2017-01" -> 424.10
    private Double total;
}

