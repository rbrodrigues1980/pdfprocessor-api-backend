package br.com.verticelabs.pdfprocessor.interfaces.rubricas.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class UpdateRubricaRequest {
    @NotBlank(message = "Descrição é obrigatória")
    private String descricao;

    private String categoria;
}

