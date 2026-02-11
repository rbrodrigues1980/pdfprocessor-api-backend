package br.com.verticelabs.pdfprocessor.interfaces.users.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.Set;

@Data
public class UpdateUserRequest {
    @NotBlank(message = "Nome é obrigatório")
    private String nome;
    
    @NotBlank(message = "Email é obrigatório")
    @Email(message = "Email inválido")
    private String email;
    
    @NotEmpty(message = "Roles são obrigatórias")
    private Set<String> roles;
    
    private String telefone;
    
    private Boolean ativo;
}

