package br.com.verticelabs.pdfprocessor.interfaces.users.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.Set;

@Data
public class CreateUserRequest {
    @NotBlank(message = "Nome é obrigatório")
    private String nome;
    
    @NotBlank(message = "Email é obrigatório")
    @Email(message = "Email inválido")
    private String email;
    
    @NotBlank(message = "Senha é obrigatória")
    @Size(min = 8, message = "Senha deve ter no mínimo 8 caracteres")
    private String senha;
    
    @NotEmpty(message = "Roles são obrigatórias")
    private Set<String> roles; // SUPER_ADMIN, TENANT_ADMIN, TENANT_USER
    
    private String tenantId; // Opcional para SUPER_ADMIN
    
    private String telefone; // Opcional
}

