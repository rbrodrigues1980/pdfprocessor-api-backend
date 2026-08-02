package br.com.verticelabs.pdfprocessor.infrastructure.excel;

import br.com.verticelabs.pdfprocessor.interfaces.consolidation.dto.ConsolidationRow;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SabesprevFichaTotaisHelperTest {

    @Test
    void isAnoFichaFinanceira_soCodigos7e9() {
        List<ConsolidationRow> ficha = List.of(
                row("7400", map("2021-03", "120.45")),
                row("9100", map("2021-09", "605.67")),
                row("3347", map("2020-01", "700.39"))); // outro ano — ignora
        assertTrue(SabesprevFichaTotaisHelper.isAnoFichaFinanceira(ficha, "2021"));
        assertFalse(SabesprevFichaTotaisHelper.isAnoFichaFinanceira(ficha, "2020"));
    }

    @Test
    void isAnoFichaFinanceira_rejeitaMisturaCom3347NoMesmoAno() {
        List<ConsolidationRow> misto = List.of(
                row("7400", map("2021-03", "120.45")),
                row("3347", map("2021-01", "700.39")));
        assertFalse(SabesprevFichaTotaisHelper.isAnoFichaFinanceira(misto, "2021"));
    }

    @Test
    void anselmo2021_contribuicaoMenosDevolucao() {
        List<ConsolidationRow> rubricas = List.of(
                row("7400", map(
                        "2021-03", "120.45",
                        "2021-04", "48.83",
                        "2021-05", "48.83",
                        "2021-06", "50.70",
                        "2021-07", "50.70",
                        "2021-08", "50.70",
                        "2021-09", "50.70",
                        "2021-10", "50.70",
                        "2021-11", "50.70",
                        "2021-12", "50.70")),
                row("7401", map("2021-12", "50.70")),
                row("7402", map("2021-09", "472.62")),
                row("7404", map("2021-09", "153.41")),
                row("9100", map("2021-09", "605.67")),
                row("9102", map("2021-09", "4.09")),
                row("9111", map("2021-09", "2.96")));

        SabesprevFichaTotaisHelper.ResumoFicha resumo = SabesprevFichaTotaisHelper.calcular(rubricas, "2021");

        assertEquals(new BigDecimal("1249.74"), resumo.contribuicao().totalAno());
        assertEquals(new BigDecimal("612.72"), resumo.devolucao().totalAno());
        assertEquals(new BigDecimal("637.02"), resumo.totalLiquido().totalAno());

        // SET: 50.70+472.62+153.41 = 676.73; devol 605.67+4.09+2.96 = 612.72; líquido 64.01
        assertEquals(new BigDecimal("676.73"), resumo.contribuicao().porMes()[8]);
        assertEquals(new BigDecimal("612.72"), resumo.devolucao().porMes()[8]);
        assertEquals(new BigDecimal("64.01"), resumo.totalLiquido().porMes()[8]);

        // DEZ contribuição: 50.70 + 50.70 = 101.40
        assertEquals(new BigDecimal("101.40"), resumo.contribuicao().porMes()[11]);
        assertEquals(new BigDecimal("101.40"), resumo.totalLiquido().porMes()[11]);
    }

    private static ConsolidationRow row(String codigo, Map<String, BigDecimal> valores) {
        return ConsolidationRow.builder().codigo(codigo).valores(valores).build();
    }

    private static Map<String, BigDecimal> map(String... kv) {
        Map<String, BigDecimal> m = new HashMap<>();
        for (int i = 0; i < kv.length; i += 2) {
            m.put(kv[i], new BigDecimal(kv[i + 1]));
        }
        return m;
    }
}
