package br.com.verticelabs.pdfprocessor.domain.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "tenants")
public class Tenant {
    @Id
    private String id;
    
    @Indexed(unique = true)
    private String nome; // Nome da empresa
    
    private String dominio; // Domínio opcional (ex: "xpto.com.br")
    
    @Builder.Default
    private Boolean ativo = true;
    
    private TenantConfig config;
    
    private Instant createdAt;
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TenantConfig {
        @Builder.Default
        private Boolean twoFactorRequired = false; // Força 2FA em todos usuários do tenant
        
        private Integer maxUsers; // Controle de licenças
    }
}

