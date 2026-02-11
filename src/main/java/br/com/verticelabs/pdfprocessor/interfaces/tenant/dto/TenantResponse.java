package br.com.verticelabs.pdfprocessor.interfaces.tenant.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TenantResponse {
    private String id;
    private String nome;
    private String dominio;
    private Boolean ativo;
    private Instant createdAt;
}

