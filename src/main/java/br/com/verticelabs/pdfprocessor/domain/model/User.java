package br.com.verticelabs.pdfprocessor.domain.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "users")
public class User {
    @Id
    private String id;
    
    private String tenantId; // Tenant ao qual o usuário pertence (null para SUPER_ADMIN)
    
    private String nome; // Nome completo do usuário
    
    @Indexed(unique = true)
    private String email; // Email único globalmente
    
    private String senhaHash; // Argon2id hash
    
    @Builder.Default
    @Setter(lombok.AccessLevel.NONE) // Excluir do setter gerado pelo Lombok
    private Set<String> roles = new HashSet<>(Set.of("TENANT_USER")); // SUPER_ADMIN, TENANT_ADMIN, TENANT_USER
    
    /**
     * Setter customizado para garantir que roles seja sempre um HashSet mutável.
     * Isso evita UnsupportedOperationException quando o MongoDB deserializa Sets como coleções imutáveis.
     * Sobrescreve qualquer setter que possa ser gerado.
     */
    public void setRoles(Set<String> roles) {
        this.roles = roles != null ? new HashSet<>(roles) : new HashSet<>();
    }
    
    @Builder.Default
    private Boolean twoFactorEnabled = false;
    
    private String twoFactorTempCode; // Código temporário de 6 dígitos
    
    private Instant twoFactorTempCodeExpires; // Expiração do código 2FA
    
    @Builder.Default
    private List<RefreshToken> refreshTokens = new ArrayList<>(); // Múltiplos refresh tokens rotativos
    
    private String telefone; // Telefone opcional
    
    @Builder.Default
    private Boolean ativo = true;
    
    private Instant createdAt;
    
    private Instant updatedAt;
    
    private Instant desativadoEm; // Data de desativação (soft delete)
    
    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RefreshToken {
        private String token; // UUID v4
        private Instant expiresAt;
        private Instant createdAt;
        private Boolean used; // Para detectar reutilização (rotativo)
    }
    
    public boolean isSuperAdmin() {
        return roles != null && roles.contains("SUPER_ADMIN");
    }
}
