package br.com.verticelabs.pdfprocessor.infrastructure.excel;

import br.com.verticelabs.pdfprocessor.interfaces.consolidation.dto.ConsolidationRow;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Totais da Ficha Financeira SABESPREV na matriz Excel:
 * CONTRIBUIÇÃO (códigos 7*) − DEVOLUÇÃO (códigos 9*) = TOTAL líquido.
 */
public final class SabesprevFichaTotaisHelper {

    private static final RoundingMode RM = RoundingMode.HALF_UP;

    private SabesprevFichaTotaisHelper() {
    }

    /**
     * Ano da ficha: todas as rubricas com valor no ano começam com 7 ou 9
     * (permite rodapé certo mesmo com origem global mista SABESP+SABESPREV).
     */
    public static boolean isAnoFichaFinanceira(List<ConsolidationRow> rubricas, String ano) {
        if (rubricas == null || ano == null || ano.isBlank()) {
            return false;
        }
        boolean alguma = false;
        for (ConsolidationRow rubrica : rubricas) {
            if (rubrica == null || rubrica.getCodigo() == null || !temValorNoAno(rubrica, ano)) {
                continue;
            }
            alguma = true;
            String codigo = rubrica.getCodigo().trim();
            if (!codigo.startsWith("7") && !codigo.startsWith("9")) {
                return false;
            }
        }
        return alguma;
    }

    private static boolean temValorNoAno(ConsolidationRow rubrica, String ano) {
        Map<String, BigDecimal> valores = rubrica.getValores();
        if (valores == null || valores.isEmpty()) {
            return false;
        }
        for (int mes = 1; mes <= 12; mes++) {
            String ref = ano + "-" + String.format("%02d", mes);
            BigDecimal v = valores.get(ref);
            if (v != null && v.compareTo(BigDecimal.ZERO) != 0) {
                return true;
            }
        }
        return false;
    }

    public record TotaisLinha(BigDecimal[] porMes, BigDecimal totalAno) {
        public TotaisLinha {
            if (porMes == null || porMes.length != 12) {
                throw new IllegalArgumentException("porMes deve ter 12 elementos");
            }
        }
    }

    public record ResumoFicha(TotaisLinha contribuicao, TotaisLinha devolucao, TotaisLinha totalLiquido) {
    }

    public static ResumoFicha calcular(List<ConsolidationRow> rubricas, String ano) {
        TotaisLinha contrib = somarPorPrefixo(rubricas, ano, "7");
        TotaisLinha devol = somarPorPrefixo(rubricas, ano, "9");
        BigDecimal[] liquidoMes = new BigDecimal[12];
        for (int i = 0; i < 12; i++) {
            liquidoMes[i] = scale(contrib.porMes()[i].subtract(devol.porMes()[i]));
        }
        BigDecimal liquidoAno = scale(contrib.totalAno().subtract(devol.totalAno()));
        return new ResumoFicha(contrib, devol, new TotaisLinha(liquidoMes, liquidoAno));
    }

    public static TotaisLinha somarPorPrefixo(List<ConsolidationRow> rubricas, String ano, String prefixo) {
        BigDecimal[] porMes = new BigDecimal[12];
        for (int i = 0; i < 12; i++) {
            porMes[i] = BigDecimal.ZERO;
        }
        if (rubricas == null || ano == null || prefixo == null) {
            return new TotaisLinha(porMes, BigDecimal.ZERO.setScale(2, RM));
        }

        for (ConsolidationRow rubrica : rubricas) {
            if (rubrica == null || rubrica.getCodigo() == null
                    || !rubrica.getCodigo().startsWith(prefixo)) {
                continue;
            }
            Map<String, BigDecimal> valores = rubrica.getValores() != null
                    ? rubrica.getValores()
                    : Collections.emptyMap();
            for (int mes = 1; mes <= 12; mes++) {
                String ref = ano + "-" + String.format("%02d", mes);
                BigDecimal v = valores.getOrDefault(ref, BigDecimal.ZERO);
                if (v != null) {
                    porMes[mes - 1] = porMes[mes - 1].add(v);
                }
            }
        }

        BigDecimal totalAno = BigDecimal.ZERO;
        for (int i = 0; i < 12; i++) {
            porMes[i] = scale(porMes[i]);
            totalAno = totalAno.add(porMes[i]);
        }
        return new TotaisLinha(porMes, scale(totalAno));
    }

    private static BigDecimal scale(BigDecimal v) {
        return (v != null ? v : BigDecimal.ZERO).setScale(2, RM);
    }
}
