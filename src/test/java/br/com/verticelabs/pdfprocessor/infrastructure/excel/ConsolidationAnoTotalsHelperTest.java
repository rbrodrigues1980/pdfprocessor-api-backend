package br.com.verticelabs.pdfprocessor.infrastructure.excel;

import br.com.verticelabs.pdfprocessor.interfaces.consolidation.dto.ConsolidationRow;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Map;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.assertEquals;

@DisplayName("ConsolidationAnoTotalsHelper - 13º Caixa vs Funcef")
class ConsolidationAnoTotalsHelperTest {

    @Test
    @DisplayName("CAIXA: FEV+NOV devem somar no Total (ex. 4369 em 2017)")
    void caixaSomaFevENov() {
        ConsolidationRow rubrica = row("4369", Map.of(
                "2017-02", new BigDecimal("259.38"),
                "2017-11", new BigDecimal("224.14")
        ));
        BigDecimal soma = new BigDecimal("483.52");

        BigDecimal total = ConsolidationAnoTotalsHelper.calcularTotalRubricaAno(
                rubrica, "2017", soma, "CAIXA");

        assertEquals(0, new BigDecimal("483.52").compareTo(total));
    }

    @Test
    @DisplayName("FUNCEF_DEMONSTRATIVO: FEV+NOV usam só NOV no Total")
    void funcefDemonstrativoUsaApenasNov() {
        ConsolidationRow rubrica = row("4364", Map.of(
                "2017-02", new BigDecimal("49.10"),
                "2017-11", new BigDecimal("98.20")
        ));
        BigDecimal soma = new BigDecimal("147.30");

        BigDecimal total = ConsolidationAnoTotalsHelper.calcularTotalRubricaAno(
                rubrica, "2017", soma, "FUNCEF_DEMONSTRATIVO");

        assertEquals(0, new BigDecimal("98.20").compareTo(total));
    }

    @Test
    @DisplayName("totaisPorAno preparado tem prioridade sobre heurística")
    void totaisPorAnoTemPrioridade() {
        ConsolidationRow rubrica = ConsolidationRow.builder()
                .codigo("4364")
                .descricao("TAXA ADM. AB. ANUAL")
                .valores(Map.of(
                        "2017-02", new BigDecimal("49.10"),
                        "2017-11", new BigDecimal("98.20")
                ))
                .totaisPorAno(Map.of("2017", new BigDecimal("98.20")))
                .build();
        BigDecimal soma = new BigDecimal("147.30");

        // Mesmo com origem CAIXA, respeita o total já calculado na consolidação
        BigDecimal total = ConsolidationAnoTotalsHelper.calcularTotalRubricaAno(
                rubrica, "2017", soma, "CAIXA");

        assertEquals(0, new BigDecimal("98.20").compareTo(total));
    }

    @Test
    @DisplayName("FUNCEF: FEV+NOV usam só NOV no Total")
    void funcefUsaApenasNov() {
        ConsolidationRow rubrica = row("4369", Map.of(
                "2017-02", new BigDecimal("259.38"),
                "2017-11", new BigDecimal("224.14")
        ));
        BigDecimal soma = new BigDecimal("483.52");

        BigDecimal total = ConsolidationAnoTotalsHelper.calcularTotalRubricaAno(
                rubrica, "2017", soma, "FUNCEF");

        assertEquals(0, new BigDecimal("224.14").compareTo(total));
    }

    @Test
    @DisplayName("Sem origem: soma (não aplica regra Funcef)")
    void semOrigemSoma() {
        ConsolidationRow rubrica = row("4416", Map.of(
                "2017-02", new BigDecimal("36.98"),
                "2017-11", new BigDecimal("37.86")
        ));
        BigDecimal soma = new BigDecimal("74.84");

        BigDecimal total = ConsolidationAnoTotalsHelper.calcularTotalRubricaAno(
                rubrica, "2017", soma, null);

        assertEquals(0, new BigDecimal("74.84").compareTo(total));
    }

    @Test
    @DisplayName("FUNCEF com outros meses: soma normal")
    void funcefComOutrosMesesSoma() {
        ConsolidationRow rubrica = row("4346", Map.of(
                "2017-01", new BigDecimal("458.28"),
                "2017-02", new BigDecimal("458.28"),
                "2017-11", new BigDecimal("483.21")
        ));
        BigDecimal soma = new BigDecimal("1399.77");

        BigDecimal total = ConsolidationAnoTotalsHelper.calcularTotalRubricaAno(
                rubrica, "2017", soma, "FUNCEF");

        assertEquals(0, soma.compareTo(total));
    }

    private static ConsolidationRow row(String codigo, Map<String, BigDecimal> valoresParciais) {
        Map<String, BigDecimal> valores = new TreeMap<>();
        for (int mes = 1; mes <= 12; mes++) {
            String ref = String.format("2017-%02d", mes);
            valores.put(ref, valoresParciais.getOrDefault(ref, BigDecimal.ZERO));
        }
        return ConsolidationRow.builder()
                .codigo(codigo)
                .descricao("teste")
                .valores(valores)
                .build();
    }
}
