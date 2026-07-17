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

    /** Empresa/entidade associada ao cliente (sempre em maiúsculas). @deprecated substituído por empresaId */
    @Deprecated
    private String entidade;

    /** ID da empresa vinculada (opcional). */
    private String empresaId;

    /** ID do percentual de honorários selecionado na empresa (opcional). */
    private String percentualHonorarioId;
    
    @Builder.Default
    private List<String> documentos = new ArrayList<>(); // IDs dos documentos associados
    
    @Builder.Default
    private Boolean ativo = true; // Status ativo/inativo da pessoa

    @Builder.Default
    private Boolean validado = false; // Indica que todo o processamento do cliente foi validado (irreversível)

    private Instant validadoEm; // Data/hora em que foi marcado como validado

    /**
     * Status operacional do cliente. Nulo em documentos antigos é tratado como {@link PersonStatus#EM_PROCESSAMENTO}.
     */
    private PersonStatus status;
    
    private Instant createdAt;
    
    private Instant updatedAt;
}

