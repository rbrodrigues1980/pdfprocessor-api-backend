package br.com.verticelabs.pdfprocessor.application.tributacao.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ModeloTributacaoResultDTO {

    private String tipo;
    private BigDecimal rendimentosTributaveis;
    private BigDecimal totalDeducoes;
    private BigDecimal descontoSimplificado;
    private BigDecimal baseCalculo;
    private List<ResultadoFaixaCalculoDTO> faixas;
    private BigDecimal impostoDevidoInicial;
    private BigDecimal reducaoAnual;
    private BigDecimal impostoDevidoAposReducao;
    private BigDecimal deducoesEspeciais;
    private BigDecimal impostoDevidoFinal;
    /** Imposto devido I − crédito INSS empregador doméstico (quando aplicável). */
    private BigDecimal impostoDevidoII;
    /** Crédito de INSS patronal empregador doméstico abatido do imposto devido I. */
    private BigDecimal creditoInssDomestico;
    private BigDecimal aliquotaEfetiva;
    private BigDecimal impostoPagoTotal;
    private BigDecimal saldo;
    private BigDecimal impostoRestituir;
    private BigDecimal saldoImpostoPagar;
    private ResumoDeclaracaoDTO resumo;
}
