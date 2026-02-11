package br.com.verticelabs.pdfprocessor.interfaces.auth.dto;

import lombok.Data;

@Data
public class RefreshRequest {
    private String refreshToken;
}

