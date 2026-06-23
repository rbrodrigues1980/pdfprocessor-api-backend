package br.com.verticelabs.pdfprocessor.domain.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EmpresaPercentual {
    private String id;
    private String descricao;
    private BigDecimal percentual;
    private LocalDate vigenciaInicio;
    private LocalDate vigenciaFim;
    @Builder.Default
    private Boolean ativo = true;
}
