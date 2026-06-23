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
public class ResumoDeclaracaoDTO {

    // Item 7 — Imposto devido
    private BigDecimal baseCalculoImposto;
    private BigDecimal impostoDevido;
    private BigDecimal deducaoIncentivo;
    private BigDecimal impostoDevidoI;
    private BigDecimal impostoDevidoRRA;
    private BigDecimal aliquotaEfetiva;
    private BigDecimal totalImpostoDevido;

    // Item 8 — Imposto pago
    private BigDecimal impostoRetidoFonteTitular;
    private BigDecimal impostoRetidoFonteDependentes;
    private BigDecimal carneLeaoTitular;
    private BigDecimal carneLeaoDependentes;
    private BigDecimal impostoComplementar;
    private BigDecimal impostoPagoExterior;
    private BigDecimal impostoRetidoFonteLei11033;
    private BigDecimal impostoRetidoRRA;
    private BigDecimal totalImpostoPago;

    // Itens 9 e 10
    private BigDecimal impostoRestituir;
    private BigDecimal saldoImpostoPagar;
}
