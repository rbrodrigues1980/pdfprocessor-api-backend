package br.com.verticelabs.pdfprocessor.interfaces.empresas.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class CreateEmpresaRequest {
    @NotBlank(message = "Nome é obrigatório")
    private String nome;

    @NotBlank(message = "Sigla é obrigatória")
    private String sigla;

    private String cnpj;

    @Valid
    private List<EmpresaPercentualDTO> percentuais = new ArrayList<>();
}
