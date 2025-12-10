package br.com.verticelabs.pdfprocessor.interfaces.persons.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PersonRubricasMatrixResponse {
    private String cpf;
    private String nome;
    private String matricula;
    
    // Matriz: rubricaCodigo -> referencia -> cell
    private Map<String, Map<String, RubricaMatrixCell>> matrix;
    
    // Totais por rubrica: rubricaCodigo -> total
    private Map<String, Double> rubricasTotais;
    
    // Total geral de todas as rubricas
    private Double totalGeral;
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RubricaMatrixCell {
        private String referencia; // Mês/ano no formato "2017-08"
        private Double valor;
        private Integer quantidade; // Quantidade de entries para essa rubrica/referência
    }
}

