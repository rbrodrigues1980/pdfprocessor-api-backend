package br.com.verticelabs.pdfprocessor.interfaces.persons.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class UpdatePersonObservacoesRequest {
    /** Texto livre de observações. Nulo ou em branco limpa o campo. */
    @Size(max = 1000, message = "Observações não podem exceder 1000 caracteres")
    private String observacoes;
}
