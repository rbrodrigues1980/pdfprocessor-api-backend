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
public class ResultadoCalculoIrpfDTO {
    private BigDecimal rendimentosTributaveis;
    private BigDecimal totalDeducoes;
    private BigDecimal baseCalculo;
    private List<ResultadoFaixaCalculoDTO> faixas;
    private BigDecimal impostoProgressivo;
    private BigDecimal reducaoAnual;
    private BigDecimal deducoesEspeciais;
    private BigDecimal impostoDevido;
    private BigDecimal aliquotaEfetiva;
}
