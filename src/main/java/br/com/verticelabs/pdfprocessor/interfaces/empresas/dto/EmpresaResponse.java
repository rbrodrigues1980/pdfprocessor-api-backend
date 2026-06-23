package br.com.verticelabs.pdfprocessor.interfaces.empresas.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EmpresaResponse {
    private String id;
    private String tenantId;
    private String nome;
    private String sigla;
    private String cnpj;
    private List<EmpresaPercentualDTO> percentuais;
    private Boolean ativo;
    private Instant createdAt;
    private Instant updatedAt;
}
