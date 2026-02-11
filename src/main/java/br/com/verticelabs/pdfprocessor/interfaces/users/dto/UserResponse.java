package br.com.verticelabs.pdfprocessor.interfaces.users.dto;

import com.fasterxml.jackson.annotation.JsonGetter;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserResponse {
    private String id;
    private String tenantId;
    private String tenantNome; // Nome do tenant (se houver)
    private String nome;
    private String email;
    private String telefone;

    @Builder.Default
    private Set<String> roles = new HashSet<>();

    private Boolean ativo;
    private Boolean twoFactorEnabled;
    private Instant createdAt;
    private Instant updatedAt;
    private Instant desativadoEm; // Se desativado

    /**
     * Getter customizado para garantir que roles seja sempre um HashSet mutável.
     * Retorna uma cópia defensiva para evitar que o Jackson modifique o Set original.
     * @JsonGetter garante que o Jackson sempre use este método durante a serialização.
     * Sobrescreve o getter gerado pelo Lombok.
     */
    @JsonGetter("roles")
    public Set<String> getRoles() {
        if (this.roles == null) {
            this.roles = new HashSet<>();
        }
        // Retornar uma cópia defensiva para evitar UnsupportedOperationException
        // O Jackson pode tentar modificar o Set durante a serialização, então sempre retornamos uma cópia
        return new HashSet<>(this.roles);
    }

    /**
     * Setter customizado para garantir que roles seja sempre um HashSet mutável.
     * Sobrescreve o setter gerado pelo Lombok.
     */
    public void setRoles(Set<String> roles) {
        this.roles = roles != null ? new HashSet<>(roles) : new HashSet<>();
    }
}
