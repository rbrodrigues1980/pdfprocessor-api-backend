package br.com.verticelabs.pdfprocessor.interfaces.persons.dto;

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
public class PersonResponse {
    private String id;
    private String tenantId;
    private String cpf;
    private String nome;
    private String matricula;
    private List<String> documentos;
    private Boolean ativo;
    private Instant createdAt;
    private Instant updatedAt;
}

