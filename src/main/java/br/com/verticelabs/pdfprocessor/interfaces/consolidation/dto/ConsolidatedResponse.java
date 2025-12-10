package br.com.verticelabs.pdfprocessor.interfaces.consolidation.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;
import java.util.Set;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConsolidatedResponse {
    private String cpf;
    private String nome;
    private Set<String> anos; // ["2016", "2017", "2018"]
    private List<String> meses; // ["01", "02", ..., "12"]
    private List<ConsolidationRow> rubricas;
    private Map<String, Double> totaisMensais; // formato: "2017-01" -> 700.31
    private Double totalGeral;
}

