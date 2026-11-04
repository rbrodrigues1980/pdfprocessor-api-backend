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

    /** Soma dos códigos 40–43 (ECA, Cultura, Desporto, Idoso) feitos durante o ano. */
    private BigDecimal deducaoIncentivoBruta;
    /** ECA informado diretamente na declaração — teto de 3% do imposto, dentro do global de 6%. */
    private BigDecimal incentivoDiretoEcaBruto;
    /** Pessoa Idosa informado diretamente na declaração — teto de 3% do imposto, dentro do global de 6%. */
    private BigDecimal incentivoDiretoIdosoBruto;
    /** Código 44 — PRONON. */
    private BigDecimal dedPrononBruta;
    /** Código 45 — PRONAS/PCD. */
    private BigDecimal dedPronasBruta;
}
