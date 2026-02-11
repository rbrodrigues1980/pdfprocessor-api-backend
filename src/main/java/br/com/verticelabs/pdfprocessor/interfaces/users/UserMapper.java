package br.com.verticelabs.pdfprocessor.interfaces.users;

import br.com.verticelabs.pdfprocessor.domain.model.Tenant;
import br.com.verticelabs.pdfprocessor.domain.model.User;
import br.com.verticelabs.pdfprocessor.interfaces.users.dto.UserResponse;
import org.springframework.stereotype.Component;

import java.util.HashSet;

@Component
public class UserMapper {
    
    public UserResponse toResponse(User user) {
        return toResponse(user, null);
    }
    
    public UserResponse toResponse(User user, Tenant tenant) {
        // Garantir que roles seja um HashSet mutável para evitar UnsupportedOperationException
        java.util.Set<String> roles = user.getRoles() != null 
                ? new HashSet<>(user.getRoles()) 
                : new HashSet<>();
        
        // Criar UserResponse e usar setter para garantir HashSet mutável
        UserResponse response = UserResponse.builder()
                .id(user.getId())
                .tenantId(user.getTenantId())
                .tenantNome(tenant != null ? tenant.getNome() : null)
                .nome(user.getNome())
                .email(user.getEmail())
                .telefone(user.getTelefone())
                .ativo(user.getAtivo())
                .twoFactorEnabled(user.getTwoFactorEnabled())
                .createdAt(user.getCreatedAt())
                .updatedAt(user.getUpdatedAt())
                .desativadoEm(user.getDesativadoEm())
                .build();
        
        // Usar setter para garantir HashSet mutável (o setter sempre cria novo HashSet)
        response.setRoles(roles);
        
        return response;
    }
}

