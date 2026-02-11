package br.com.verticelabs.pdfprocessor.interfaces.tenant.dto;

import lombok.Data;

@Data
public class CreateTenantRequest {
    private String nome;
    private String dominio; // Opcional
}

