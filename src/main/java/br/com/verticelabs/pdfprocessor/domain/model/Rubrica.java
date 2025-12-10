package br.com.verticelabs.pdfprocessor.domain.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "rubricas")
public class Rubrica {
    @Id
    private String id;
    
    private String tenantId; // "GLOBAL" para rubricas globais ou tenantId específico para customizadas
    
    @Indexed
    private String codigo; // Código da rubrica (único por tenant ou global)
    
    private String descricao; // Nome da rubrica exatamente como aparecerá no PDF
    
    private String categoria; // Classificação opcional (ex.: Administrativa, Extraordinária)
    
    @Builder.Default
    private Boolean ativo = true; // Indica se a rubrica está ativa no sistema
    
    public boolean isGlobal() {
        return "GLOBAL".equals(tenantId);
    }
}
