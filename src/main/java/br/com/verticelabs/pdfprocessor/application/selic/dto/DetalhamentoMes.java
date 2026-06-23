package br.com.verticelabs.pdfprocessor.application.selic.dto;

import java.math.BigDecimal;

public record DetalhamentoMes(
        String mesAno,
        int ano,
        int mes,
        BigDecimal taxaMes,
        BigDecimal taxaAcumulada,
        BigDecimal valorAtualizado,
        String memoriaCalculo,
        boolean taxaFixaUltimoMes
) {
}
