package br.com.verticelabs.pdfprocessor.infrastructure.excel;

import br.com.verticelabs.pdfprocessor.interfaces.consolidation.dto.ConsolidatedResponse;
import br.com.verticelabs.pdfprocessor.interfaces.consolidation.dto.ConsolidationRow;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.util.Locale;
import java.util.Map;

/**
 * Cálculo de totais anuais de contracheques (previdência complementar / Resumo Geral).
 */
@Slf4j
public final class ConsolidationAnoTotalsHelper {

    private ConsolidationAnoTotalsHelper() {
    }

    public static BigDecimal calcularTotalContracheques(ConsolidatedResponse consolidatedResponse, String ano) {
        BigDecimal total = BigDecimal.ZERO;
        String origem = consolidatedResponse != null ? consolidatedResponse.getOrigem() : null;

        for (ConsolidationRow rubrica : consolidatedResponse.getRubricas()) {
            BigDecimal somaAno = BigDecimal.ZERO;
            for (int mes = 1; mes <= 12; mes++) {
                String mesStr = String.format("%02d", mes);
                String referencia = ano + "-" + mesStr;
                somaAno = somaAno.add(rubrica.getValores().getOrDefault(referencia, BigDecimal.ZERO));
            }
            total = total.add(calcularTotalRubricaAno(rubrica, ano, somaAno, origem));
        }

        log.debug("Total contracheques para ano {}: {}", ano, total);
        return total;
    }

    /**
     * @deprecated Preferir {@link #calcularTotalRubricaAno(ConsolidationRow, String, BigDecimal, String)}.
     */
    @Deprecated
    public static BigDecimal calcularTotalRubricaAno(ConsolidationRow rubrica, String ano, BigDecimal somaSimples) {
        return calcularTotalRubricaAno(rubrica, ano, somaSimples, null);
    }

    /**
     * Total anual da rubrica.
     * <p>
     * Prefere {@link ConsolidationRow#getTotaisPorAno()} (já com regra Funcef).
     * Fallback FEV+NOV → NOV apenas para origem Funcef / Funcef Demonstrativo.
     * Em {@code CAIXA} (ou origem mista/ausente no fallback), retorna a soma simples.
     */
    public static BigDecimal calcularTotalRubricaAno(
            ConsolidationRow rubrica, String ano, BigDecimal somaSimples, String origem) {

        Map<String, BigDecimal> totaisPorAno = rubrica.getTotaisPorAno();
        if (totaisPorAno != null && totaisPorAno.containsKey(ano)) {
            BigDecimal totalPreparado = totaisPorAno.get(ano);
            if (totalPreparado != null) {
                return totalPreparado;
            }
        }

        if (!isOrigemFuncef(origem)) {
            return somaSimples;
        }

        String refFev = ano + "-02";
        String refNov = ano + "-11";

        BigDecimal valorFev = rubrica.getValores().getOrDefault(refFev, BigDecimal.ZERO);
        BigDecimal valorNov = rubrica.getValores().getOrDefault(refNov, BigDecimal.ZERO);

        boolean temValorOutrosMeses = false;
        for (int mes = 1; mes <= 12; mes++) {
            if (mes == 2 || mes == 11) {
                continue;
            }
            String mesStr = String.format("%02d", mes);
            String referencia = ano + "-" + mesStr;
            BigDecimal valor = rubrica.getValores().getOrDefault(referencia, BigDecimal.ZERO);
            if (valor.compareTo(BigDecimal.ZERO) > 0) {
                temValorOutrosMeses = true;
                break;
            }
        }

        if (temValorOutrosMeses) {
            return somaSimples;
        }

        if (valorFev.compareTo(BigDecimal.ZERO) > 0 && valorNov.compareTo(BigDecimal.ZERO) > 0) {
            log.info("Rubrica {} ano {}: Total = NOV ({}) (regra YYYY-13 Funcef - último valor)",
                    rubrica.getCodigo(), ano, valorNov);
            return valorNov;
        }

        return somaSimples;
    }

    static boolean isOrigemFuncef(String origem) {
        if (origem == null || origem.isBlank()) {
            return false;
        }
        String o = origem.trim().toUpperCase(Locale.ROOT);
        return "FUNCEF".equals(o) || "FUNCEF_DEMONSTRATIVO".equals(o);
    }
}
