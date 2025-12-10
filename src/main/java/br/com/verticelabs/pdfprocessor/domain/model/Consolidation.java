package br.com.verticelabs.pdfprocessor.domain.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "consolidations")
public class Consolidation {
    @Id
    private String id;
    
    @Indexed
    private String tenantId; // Tenant ao qual a consolidação pertence
    
    @Indexed
    private String cpf; // CPF do titular
    
    @Indexed
    private Integer ano; // Ano da consolidação
    
    @Builder.Default
    private List<ConsolidationRow> matriz = new ArrayList<>(); // Matriz consolidada
    
    private Instant generatedAt; // Data de geração da consolidação
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ConsolidationRow {
        private String codigo; // Código da rubrica
        private String descricao; // Descrição da rubrica
        private Double janeiro;
        private Double fevereiro;
        private Double marco;
        private Double abril;
        private Double maio;
        private Double junho;
        private Double julho;
        private Double agosto;
        private Double setembro;
        private Double outubro;
        private Double novembro;
        private Double dezembro;
    }
}

