package br.com.verticelabs.pdfprocessor.interfaces.persons.dto;

import br.com.verticelabs.pdfprocessor.domain.model.PersonStatus;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class UpdatePersonStatusRequest {
    @NotNull(message = "Status é obrigatório")
    private PersonStatus status;
}
