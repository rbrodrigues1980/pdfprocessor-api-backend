package br.com.verticelabs.pdfprocessor.interfaces.auth.dto;

import lombok.Data;

@Data
public class RegisterAdminRequest {
    private String tenantId;
    private String nome;
    private String email;
    private String senha;
}

