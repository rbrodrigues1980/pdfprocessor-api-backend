package br.com.verticelabs.pdfprocessor.infrastructure.incometax;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Garante que a regex de prev. complementar distingue as duas linhas de previdência no RESUMO IRPF.
 */
class ITextIncomeTaxPrevComplPatternTest {

    private static final String DEDUCOES_ADRIANA_2019 = """
            DEDUÇÕES
            Contribuição à previdência oficial e à previdência complementar pública (até o limite do patrocinador) 7.707,96
            Contribuição à previdência complementar, pública (acima do limite do patrocinador) ou privada, e Fapi 34.964,17
            Dependentes 0,00
            Despesas com instrução 0,00
            Despesas médicas 6.465,68
            TOTAL 49.137,81
            """;

    @Test
    void extraiPrevComplementarDaLinhaAcimaDoLimite() throws Exception {
        Pattern acimaLimite = pattern("DEDUCOES_CONTRIB_PREV_COMPL_ACIMA_LIMITE_PATTERN");
        Pattern fallback = pattern("DEDUCOES_CONTRIB_PREV_COMPL_PATTERN");

        assertEquals(new BigDecimal("34964.17"), extract(DEDUCOES_ADRIANA_2019, acimaLimite));
        assertEquals(new BigDecimal("34964.17"), extract(DEDUCOES_ADRIANA_2019, fallback));
    }

    private static Pattern pattern(String fieldName) throws Exception {
        Field field = ITextIncomeTaxServiceImpl.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        return (Pattern) field.get(null);
    }

    private static BigDecimal extract(String text, Pattern pattern) throws Exception {
        Method method = ITextIncomeTaxServiceImpl.class.getDeclaredMethod("extractValorMonetario", String.class, Pattern.class);
        method.setAccessible(true);
        ITextIncomeTaxServiceImpl service = new ITextIncomeTaxServiceImpl();
        BigDecimal value = (BigDecimal) method.invoke(service, text, pattern);
        assertNotNull(value, "Valor não extraído para " + pattern);
        return value;
    }
}
