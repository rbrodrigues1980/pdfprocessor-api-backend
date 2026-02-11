package br.com.verticelabs.pdfprocessor.interfaces.rubricas.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class CreateRubricaRequest {
    @NotBlank(message = "Código é obrigatório")
    private String codigo;

    @NotBlank(message = "Descrição é obrigatória")
    private String descricao;

    private String categoria;
}

