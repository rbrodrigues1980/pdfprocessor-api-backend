package br.com.verticelabs.pdfprocessor.interfaces.empresas.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class EmpresaPercentualDTO {
    private String id;

    @NotBlank(message = "Descrição do percentual é obrigatória")
    private String descricao;

    @NotNull(message = "Percentual é obrigatório")
    @DecimalMin(value = "0.01", message = "Percentual deve ser maior que zero")
    @DecimalMax(value = "100", message = "Percentual não pode exceder 100")
    private BigDecimal percentual;

    @NotNull(message = "Data de início da vigência é obrigatória")
    private LocalDate vigenciaInicio;

    private LocalDate vigenciaFim;

    private Boolean ativo;
}
