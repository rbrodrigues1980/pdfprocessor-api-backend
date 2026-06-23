package br.com.verticelabs.pdfprocessor.application.tributacao.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CalcularIrpfRequest {
    private Integer anoCalendario;
    private String tipoIncidencia;
    private BigDecimal rendimentosTributaveis;
    private BigDecimal totalDeducoes;
    private BigDecimal deducoesEspeciais;
}
