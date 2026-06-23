package br.com.verticelabs.pdfprocessor.application.tributacao.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Valores efetivos de doações após travas legais (6% global, 1% PRONON/PRONAS).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DoacoesEfetivasDTO {

    private BigDecimal deducaoIncentivoEfetiva;
    private BigDecimal dedPrononEfetiva;
    private BigDecimal dedPronasEfetiva;

    public BigDecimal totalEfetivo() {
        return nvl(deducaoIncentivoEfetiva)
                .add(nvl(dedPrononEfetiva))
                .add(nvl(dedPronasEfetiva));
    }

    private static BigDecimal nvl(BigDecimal v) {
        return v != null ? v : BigDecimal.ZERO;
    }
}
