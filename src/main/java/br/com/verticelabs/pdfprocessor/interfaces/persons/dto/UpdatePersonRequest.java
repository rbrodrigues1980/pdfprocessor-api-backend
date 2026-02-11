package br.com.verticelabs.pdfprocessor.interfaces.persons.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class UpdatePersonRequest {
    @NotBlank(message = "Nome é obrigatório")
    private String nome;

    private String matricula;
}

