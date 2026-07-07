package br.com.verticelabs.pdfprocessor.infrastructure.incometax;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Garante que o TOTAL DE RENDIMENTOS TRIBUTÁVEIS é a soma de todas as linhas
 * (PJ + PF/Exterior + acumulados + rural), e não apenas a primeira linha.
 *
 * <p>Regressão do caso "CÉLIA DONOLA CARVALHO E CASTRO" (AC 2016, simplificada):
 * PJ Titular 67.963,80 + PF/Exterior Titular 6.120,00 = 74.083,80.</p>
 */
class ITextIncomeTaxRendimentosBreakdownTest {

    /** Layout linha-a-linha (declaração simplificada). */
    private static final String RESUMO_CELIA_2016 = """
            RESUMO
            TRIBUTAÇÃO UTILIZANDO O DESCONTO SIMPLIFICADO
            RENDIMENTOS TRIBUTÁVEIS E DESCONTO SIMPLIFICADO
            Recebidos de Pessoa Jurídica pelo Titular 67.963,80
            Recebidos de Pessoa Jurídica pelos Dependentes 0,00
            Recebidos de Pessoa Física/Exterior pelo Titular 6.120,00
            Recebidos de Pessoa Física/Exterior pelos Dependentes 0,00
            Recebidos acumuladamente pelo titular 0,00
            Recebidos acumuladamente pelos dependentes 0,00
            Resultado tributável da Atividade Rural 0,00
            TOTAL DE RENDIMENTOS TRIBUTÁVEIS 74.083,80
            Desconto Simplificado 14.816,76
            Base de cálculo do imposto 53.147,04
            Imposto devido 5.866,11
            """;

    /** Layout agrupado (rótulos juntos, valores em sequência). */
    private static final String RESUMO_AGRUPADO = """
            RENDIMENTOS TRIBUTÁVEIS Recebidos de Pessoa Jurídica pelo titular Recebidos de Pessoa Jurídica pelos dependentes Recebidos de Pessoa Física/Exterior pelo titular Recebidos de Pessoa Física/Exterior pelos dependentes Recebidos acumuladamente pelo titular Recebidos acumuladamente pelos dependentes Resultado tributável da Atividade Rural TOTAL DE RENDIMENTOS TRIBUTÁVEIS
            373.152,82 10.560,00 0,00 0,00 0,00 0,00 0,00
            383.712,82
            DEDUÇÕES
            """;

    @Test
    void totalSomaTodasAsLinhasNoLayoutLinhaALinha() throws Exception {
        Object breakdown = breakdown(RESUMO_CELIA_2016);
        assertNotNull(breakdown, "Breakdown não deveria ser nulo para layout linha-a-linha");

        assertEquals(new BigDecimal("74083.80"), field(breakdown, "total"));
        assertEquals(new BigDecimal("67963.80"), field(breakdown, "titularPJ"));
        assertEquals(new BigDecimal("6120.00"), field(breakdown, "titularPF"));
    }

    @Test
    void totalUsaUltimoValorNoLayoutAgrupado() throws Exception {
        Object breakdown = breakdown(RESUMO_AGRUPADO);
        assertNotNull(breakdown, "Breakdown não deveria ser nulo para layout agrupado");

        assertEquals(new BigDecimal("383712.82"), field(breakdown, "total"));
        assertEquals(new BigDecimal("373152.82"), field(breakdown, "titularPJ"));
        assertEquals(new BigDecimal("10560.00"), field(breakdown, "dependentesPJ"));
    }

    private static Object breakdown(String text) throws Exception {
        Method method = ITextIncomeTaxServiceImpl.class
                .getDeclaredMethod("extractRendimentosTributaveisBreakdown", String.class);
        method.setAccessible(true);
        return method.invoke(new ITextIncomeTaxServiceImpl(), text);
    }

    private static BigDecimal field(Object breakdown, String name) throws Exception {
        Field field = breakdown.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return (BigDecimal) field.get(breakdown);
    }
}
