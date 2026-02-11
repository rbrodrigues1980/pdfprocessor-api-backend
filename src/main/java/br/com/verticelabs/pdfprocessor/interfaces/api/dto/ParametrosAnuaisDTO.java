package br.com.verticelabs.pdfprocessor.interfaces.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * DTO para parâmetros anuais.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ParametrosAnuaisDTO {
    private BigDecimal deducaoDependente;
    private BigDecimal limiteInstrucao;
    private BigDecimal limiteDescontoSimplificado;
    private BigDecimal isencao65Anos;
}
