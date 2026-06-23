package br.com.verticelabs.pdfprocessor.interfaces.persons.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PersonResponse {
    private String id;
    private String tenantId;
    private String cpf;
    private String nome;
    private String matricula;
    private String empresaId;
    private String empresaNome;
    private String empresaSigla;
    private String percentualHonorarioId;
    private BigDecimal percentualHonorarios;
    private String percentualDescricao;
    private List<String> documentos;
    private Boolean ativo;
    private Boolean validado;
    private Instant validadoEm;
    private Instant createdAt;
    private Instant updatedAt;
}
