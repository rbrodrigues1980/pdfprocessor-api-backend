package br.com.verticelabs.pdfprocessor.interfaces.excel.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ResumoGeralLinhaResponse {
    private String anoCalendario;
    private BigDecimal valorDeclaracao;
    private String origemValorDeclaracao;
    private BigDecimal valorSimulacao;
    private String origemValorSimulacao;
    private BigDecimal principal;
    private BigDecimal selicAcumulada;
    private BigDecimal valorCorrecao;
    private BigDecimal principalMaisCorrecao;
    private String observacao;
    private LocalDate dataVencimento;
}
