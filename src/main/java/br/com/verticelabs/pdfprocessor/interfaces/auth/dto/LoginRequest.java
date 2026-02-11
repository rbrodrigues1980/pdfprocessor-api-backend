package br.com.verticelabs.pdfprocessor.interfaces.auth.dto;

import lombok.Data;

@Data
public class LoginRequest {
    private String email; // Email único globalmente
    private String password;
}
