package br.com.verticelabs.pdfprocessor.interfaces.auth.dto;

import lombok.Data;

@Data
public class Verify2FARequest {
    private String email;
    private String code; // Código de 6 dígitos
}

