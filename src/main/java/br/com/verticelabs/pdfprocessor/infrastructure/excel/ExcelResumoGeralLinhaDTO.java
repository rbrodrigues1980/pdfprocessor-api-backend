package br.com.verticelabs.pdfprocessor.infrastructure.excel;

import br.com.verticelabs.pdfprocessor.application.selic.dto.SelicReceitaCalculoResponse;
import lombok.Builder;
import lombok.Value;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;

/**
 * Uma linha da aba Resumo Geral (por ano-calendário com declaração IRPF).
 */
@Value
@Builder
public class ExcelResumoGeralLinhaDTO {

    private static final RoundingMode RM = RoundingMode.HALF_UP;

    String anoCalendario;
    BigDecimal valorDeclaracao;
    String origemValorDeclaracao;
    BigDecimal valorSimulacao;
    String origemValorSimulacao;
    BigDecimal principal;
    BigDecimal selicAcumulada;
    BigDecimal valorCorrecao;
    BigDecimal principalMaisCorrecao;
    String observacao;
    LocalDate dataVencimento;

    public ExcelResumoGeralLinhaDTO enriquecerComSelic(SelicReceitaCalculoResponse selic) {
        if (selic == null || principal == null || principal.compareTo(BigDecimal.ZERO) <= 0) {
            return this;
        }
        BigDecimal taxa = nvl(selic.taxaTotalAcumulada());
        BigDecimal corrigido = nvl(selic.valorCorrigido());
        BigDecimal correcao = corrigido.subtract(principal).max(BigDecimal.ZERO).setScale(2, RM);
        return ExcelResumoGeralLinhaDTO.builder()
                .anoCalendario(anoCalendario)
                .valorDeclaracao(valorDeclaracao)
                .origemValorDeclaracao(origemValorDeclaracao)
                .valorSimulacao(valorSimulacao)
                .origemValorSimulacao(origemValorSimulacao)
                .principal(principal)
                .selicAcumulada(taxa)
                .valorCorrecao(correcao)
                .principalMaisCorrecao(principal.add(correcao).setScale(2, RM))
                .observacao(observacao)
                .dataVencimento(dataVencimento)
                .build();
    }

    private static BigDecimal nvl(BigDecimal v) {
        return v != null ? v : BigDecimal.ZERO;
    }
}
