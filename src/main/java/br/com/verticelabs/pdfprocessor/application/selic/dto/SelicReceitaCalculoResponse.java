package br.com.verticelabs.pdfprocessor.application.selic.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

public record SelicReceitaCalculoResponse(
        YearMonth mesAnoInicio,
        YearMonth mesAnoFim,
        LocalDate dataVencimento,
        LocalDate dataPagamento,
        BigDecimal valorOriginal,
        BigDecimal taxaTotalAcumulada,
        BigDecimal fatorMultiplicacao,
        BigDecimal valorCorrigido,
        int totalMeses,
        List<DetalhamentoMes> detalhamento
) {
    public SelicReceitaCalculoResponse enriquecer(LocalDate vencimento, LocalDate pagamento) {
        return new SelicReceitaCalculoResponse(
                mesAnoInicio,
                mesAnoFim,
                vencimento,
                pagamento,
                valorOriginal,
                taxaTotalAcumulada,
                fatorMultiplicacao,
                valorCorrigido,
                totalMeses,
                detalhamento);
    }
}
