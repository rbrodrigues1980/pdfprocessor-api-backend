package br.com.verticelabs.pdfprocessor.application.tributacao.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Valores brutos de doações antes da aplicação dos limites legais no motor.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DoacoesBrutasDTO {

    /** Soma dos códigos 40–43 (ECA, Cultura, Desporto, Idoso). */
    private BigDecimal deducaoIncentivoBruta;
    /** Código 44 — PRONON. */
    private BigDecimal dedPrononBruta;
    /** Código 45 — PRONAS/PCD. */
    private BigDecimal dedPronasBruta;
}
