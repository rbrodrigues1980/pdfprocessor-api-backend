package br.com.verticelabs.pdfprocessor.interfaces.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * DTO para faixa de tributação.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FaixaTributacaoDTO {
    private Integer faixa;
    private BigDecimal limiteInferior;
    private BigDecimal limiteSuperior;
    private BigDecimal aliquota;
    private BigDecimal deducao;
    private String descricao;
}
