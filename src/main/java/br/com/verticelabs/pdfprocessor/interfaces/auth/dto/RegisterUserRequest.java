package br.com.verticelabs.pdfprocessor.interfaces.auth.dto;

import lombok.Data;

import java.util.Set;

@Data
public class RegisterUserRequest {
    private String nome;
    private String email;
    private String senha;
    private Set<String> roles; // Opcional, padrão: TENANT_USER
}

