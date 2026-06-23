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
    /** Limite anual da dedução INSS patronal empregador doméstico (AC 2016–2018). */
    private BigDecimal limiteInssDomestico;
    private BigDecimal limiteDescontoSimplificado;
    private BigDecimal isencao65Anos;
    private Boolean reducaoAnualAtiva;
    private BigDecimal reducaoRendimentoLimiteIsencao;
    private BigDecimal reducaoMaximaCompleta;
    private BigDecimal reducaoConstanteLinear;
    private BigDecimal reducaoCoeficienteLinear;
    private BigDecimal reducaoRendimentoLimiteSuperior;
}
