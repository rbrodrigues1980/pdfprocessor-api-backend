package br.com.verticelabs.pdfprocessor.infrastructure.excel;

import br.com.verticelabs.pdfprocessor.interfaces.consolidation.dto.ConsolidationRow;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Totais da Ficha Financeira SABESPREV na matriz Excel:
 * CONTRIBUIÇÃO (lista fechada soma) − DEVOLUÇÃO (lista fechada subtrai) = TOTAL líquido.
 * <p>
 * Códigos fora das listas (ex. 1090, 9001, 9111) podem aparecer na matriz, mas não entram no rodapé.
 */
public final class SabesprevFichaTotaisHelper {

    private static final RoundingMode RM = RoundingMode.HALF_UP;

    /** Rubricas que somam no rodapé (CONTRIBUIÇÃO). */
    public static final Set<String> CODIGOS_SOMA = Set.of(
            "7400", "7401", "7402", "9105", "9106");

    /** Rubricas que subtraem no rodapé (DEVOLUÇÃO). */
    public static final Set<String> CODIGOS_SUBTRAI = Set.of(
            "7404", "9100", "9102", "9112", "9115");

    private static final Set<String> CODIGOS_RODAPE;

    static {
        java.util.HashSet<String> all = new java.util.HashSet<>(CODIGOS_SOMA);
        all.addAll(CODIGOS_SUBTRAI);
        CODIGOS_RODAPE = Set.copyOf(all);
    }

    /** Holerite SABESP ativa — se presente no mesmo ano, não usa rodapé de ficha. */
    private static final Set<String> CODIGOS_HOLERITE_SABESP = Set.of("3347", "3349");

    private SabesprevFichaTotaisHelper() {
    }

    /**
     * Ano com ficha (origem global pode ser mista SABESP+SABESPREV → null):
     * há pelo menos um código da lista fechada do rodapé e nenhum holerite SABESP (3347/3349).
     */
    public static boolean isAnoFichaFinanceira(List<ConsolidationRow> rubricas, String ano) {
        if (rubricas == null || ano == null || ano.isBlank()) {
            return false;
        }
        boolean temCodigoRodape = false;
        for (ConsolidationRow rubrica : rubricas) {
            if (rubrica == null || rubrica.getCodigo() == null || !temValorNoAno(rubrica, ano)) {
                continue;
            }
            String codigo = rubrica.getCodigo().trim();
            if (CODIGOS_HOLERITE_SABESP.contains(codigo)) {
                return false;
            }
            if (CODIGOS_RODAPE.contains(codigo)) {
                temCodigoRodape = true;
            }
        }
        return temCodigoRodape;
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
        TotaisLinha contrib = somarPorCodigos(rubricas, ano, CODIGOS_SOMA);
        TotaisLinha devol = somarPorCodigos(rubricas, ano, CODIGOS_SUBTRAI);
        BigDecimal[] liquidoMes = new BigDecimal[12];
        for (int i = 0; i < 12; i++) {
            liquidoMes[i] = scale(contrib.porMes()[i].subtract(devol.porMes()[i]));
        }
        BigDecimal liquidoAno = scale(contrib.totalAno().subtract(devol.totalAno()));
        return new ResumoFicha(contrib, devol, new TotaisLinha(liquidoMes, liquidoAno));
    }

    public static TotaisLinha somarPorCodigos(List<ConsolidationRow> rubricas, String ano, Set<String> codigos) {
        BigDecimal[] porMes = new BigDecimal[12];
        for (int i = 0; i < 12; i++) {
            porMes[i] = BigDecimal.ZERO;
        }
        if (rubricas == null || ano == null || codigos == null || codigos.isEmpty()) {
            return new TotaisLinha(porMes, BigDecimal.ZERO.setScale(2, RM));
        }

        for (ConsolidationRow rubrica : rubricas) {
            if (rubrica == null || rubrica.getCodigo() == null
                    || !codigos.contains(rubrica.getCodigo().trim())) {
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
