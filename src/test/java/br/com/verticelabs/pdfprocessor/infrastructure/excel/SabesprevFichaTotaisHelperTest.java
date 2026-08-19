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
    void isAnoFichaFinanceira_comCodigoRodapeSemHolerite() {
        List<ConsolidationRow> ficha = List.of(
                row("7400", map("2021-03", "120.45")),
                row("9100", map("2021-09", "605.67")),
                row("1090", map("2021-01", "1000.00")), // matriz, fora do rodapé
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
    void isAnoFichaFinanceira_falseSemCodigoDaListaFechada() {
        List<ConsolidationRow> soBeneficio = List.of(
                row("1090", map("2021-01", "11561.98")),
                row("9001", map("2021-01", "1721.44")));
        assertFalse(SabesprevFichaTotaisHelper.isAnoFichaFinanceira(soBeneficio, "2021"));
    }

    @Test
    void anselmo2021_listaFechadaSomaMenosSubtrai() {
        // 7404 vai para SUBTRAI; 9111 fora da lista (não entra no rodapé)
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

        // SOMA = 7400+7401+7402 = 1096.33; SUBTRAI = 7404+9100+9102 = 763.17; TOTAL = 333.16
        assertEquals(new BigDecimal("1096.33"), resumo.contribuicao().totalAno());
        assertEquals(new BigDecimal("763.17"), resumo.devolucao().totalAno());
        assertEquals(new BigDecimal("333.16"), resumo.totalLiquido().totalAno());

        // SET: soma 50.70+472.62 = 523.32; subtrai 153.41+605.67+4.09 = 763.17; líquido -239.85
        assertEquals(new BigDecimal("523.32"), resumo.contribuicao().porMes()[8]);
        assertEquals(new BigDecimal("763.17"), resumo.devolucao().porMes()[8]);
        assertEquals(new BigDecimal("-239.85"), resumo.totalLiquido().porMes()[8]);

        // DEZ contribuição: 50.70 + 50.70 = 101.40
        assertEquals(new BigDecimal("101.40"), resumo.contribuicao().porMes()[11]);
        assertEquals(new BigDecimal("101.40"), resumo.totalLiquido().porMes()[11]);
    }

    @Test
    void codigos9105e9106_entramEmContribuicao() {
        List<ConsolidationRow> rubricas = List.of(
                row("9105", map("2019-01", "47.31", "2019-02", "47.31")),
                row("9106", map("2019-12", "64.22")),
                row("1090", map("2019-01", "11561.98"))); // ignorado no rodapé

        SabesprevFichaTotaisHelper.ResumoFicha resumo = SabesprevFichaTotaisHelper.calcular(rubricas, "2019");

        assertEquals(new BigDecimal("158.84"), resumo.contribuicao().totalAno());
        assertEquals(new BigDecimal("0.00"), resumo.devolucao().totalAno());
        assertEquals(new BigDecimal("158.84"), resumo.totalLiquido().totalAno());
    }

    @Test
    void codigos9112e9115_entramEmDevolucao() {
        List<ConsolidationRow> rubricas = List.of(
                row("7400", map("2022-06", "100.00")),
                row("9112", map("2022-06", "10.00")),
                row("9115", map("2022-06", "5.50")),
                row("9111", map("2022-06", "99.00"))); // fora da lista

        SabesprevFichaTotaisHelper.ResumoFicha resumo = SabesprevFichaTotaisHelper.calcular(rubricas, "2022");

        assertEquals(new BigDecimal("100.00"), resumo.contribuicao().totalAno());
        assertEquals(new BigDecimal("15.50"), resumo.devolucao().totalAno());
        assertEquals(new BigDecimal("84.50"), resumo.totalLiquido().totalAno());
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
