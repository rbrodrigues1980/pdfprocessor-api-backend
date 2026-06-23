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
public class ResultadoFaixaCalculoDTO {
    private Integer faixa;
    private String descricao;
    private BigDecimal baseNaFaixa;
    private BigDecimal aliquota;
    private BigDecimal impostoNaFaixa;
}
