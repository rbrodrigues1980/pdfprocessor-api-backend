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
@Document(collection = "persons")
public class Person {
    @Id
    private String id;
    
    @Indexed
    private String tenantId; // Tenant ao qual a pessoa pertence
    
    @Indexed
    private String cpf; // CPF único por tenant (índice composto único com tenantId)
    
    private String nome; // Nome completo do titular
    
    private String matricula; // Matrícula (formato pode variar: "0437412" ou "043741-2")
    
    @Builder.Default
    private List<String> documentos = new ArrayList<>(); // IDs dos documentos associados
    
    @Builder.Default
    private Boolean ativo = true; // Status ativo/inativo da pessoa
    
    private Instant createdAt;
    
    private Instant updatedAt;
}

