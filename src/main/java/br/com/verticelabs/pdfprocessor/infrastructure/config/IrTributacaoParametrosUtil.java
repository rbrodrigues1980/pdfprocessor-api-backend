package br.com.verticelabs.pdfprocessor.infrastructure.config;

import java.math.BigDecimal;

/** Valores legais de parâmetros IRPF por ano-calendário. */
public final class IrTributacaoParametrosUtil {

    private IrTributacaoParametrosUtil() {
    }

    /**
     * Limite máximo anual da dedução de INSS patronal de empregador doméstico.
     * Ativo AC 2016–2018; extinto a partir de 2019.
     */
    public static BigDecimal limiteInssDomestico(int anoCalendario) {
        return switch (anoCalendario) {
            case 2016 -> new BigDecimal("1092.00");
            case 2017 -> new BigDecimal("1171.84");
            case 2018 -> new BigDecimal("1200.32");
            default -> BigDecimal.ZERO;
        };
    }
}
