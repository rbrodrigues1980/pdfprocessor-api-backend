package br.com.verticelabs.pdfprocessor.infrastructure.pdf;

import br.com.verticelabs.pdfprocessor.domain.model.DocumentType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Layout vertical do portal FUNCEF aposentadoria (Carmen Lucia):
 * código, YYYY/MM, descrição e valor em linhas separadas — 4309/4310 sumiam.
 */
@DisplayName("Funcef portal Carmen — layout vertical")
class FuncefCarmenVerticalParsingTest {

    private final PdfLineParser parser = new PdfLineParser(new PdfNormalizer());

    /** Página maio/2017 — demonstrativo previdenciário vertical. */
    private static final String PAGINA_2017_05 = """
            DEMONSTRATIVO DE PROVENTOS PREVIDENCIÁRIOS
            Ano Pagamento /
            Mês
            2017/05
            Nome
            CARMEN LUCIA DOMINGUES CUNHA
            Tipo /
            Rubrica
            Referência
            Discriminação
            Prazo
            Valor
            1 409
            2017/04
            AC. BENEFICIO FUNCEF - IN1343
            7.159,37
            2 409
            2017/05
            BENEFICIO FUNCEF - IN1343
            7.159,37

            4 309
            2017/05
            FUNCEF CONTRIBUICAO
            447,88
            3 309
            2017/04
            REP. FUNCEF CONTRIBUICAO
            447,88

            OBS
            Dep IR
            0
            Renda Base
            7.159,37
            """;

    /** Fevereiro/2024 com 4309 mensal e 4310 abono (ref 2024/13). */
    private static final String PAGINA_2024_02 = """
            DEMONSTRATIVO DE PROVENTOS PREVIDENCIÁRIOS
            Ano Pagamento /
            Mês
            2024/02
            Nome
            CARMEN LUCIA DOMINGUES CUNHA
            Tipo /
            Rubrica
            Referência
            Discriminação
            Prazo
            Valor
            2 125
            2024/02
            SUPLEMENTACAO APOSENTADORIA
            4.000,00
            4 327
            2024/02
            IMPOSTO RENDA FONTE (FUNCEF)
            1.731,13
            4 309
            2024/02
            FUNCEF CONTRIBUICAO
            561,98
            4 310
            2024/13
            CONTR.FUNCEF SUPL. AB. ANUAL
            280,99

            OBS
            Renda Base
            10.000,00
            """;

    @Test
    @DisplayName("2017/05: remonta 4309 e demais rubricas do layout vertical")
    void pagina2017_05_contribucao4309() {
        String[] joined = parser.joinBrokenFuncefLines(PAGINA_2017_05.split("\\R"));
        assertTrue(List.of(joined).stream().anyMatch(l -> l.contains("4 309") && l.contains("447,88")),
                "join deve montar linha 4 309 com valor");

        List<PdfLineParser.ParsedLine> parsed = parser.parseLinesFuncef(PAGINA_2017_05, "2017-05");
        assertFalse(parsed.isEmpty());

        List<PdfLineParser.ParsedLine> c4309 = byCodigo(parsed, "4309");
        assertEquals(1, c4309.size(), () -> "esperado 4309; got=" + parsed);
        assertEquals("447,88", c4309.get(0).getValorStr());
        assertEquals("2017/05", c4309.get(0).getReferencia());
        assertTrue(c4309.get(0).getDescricao().toUpperCase().contains("FUNCEF CONTRIBUICAO"));

        assertEquals(1, byCodigo(parsed, "1409").size());
        assertEquals(1, byCodigo(parsed, "2409").size());
        assertEquals(1, byCodigo(parsed, "3309").size());
    }

    @Test
    @DisplayName("2024/02: remonta 4309 mensal e 4310 abono")
    void pagina2024_02_contribucoes4309e4310() {
        List<PdfLineParser.ParsedLine> parsed = parser.parseLinesFuncef(PAGINA_2024_02, "2024-02");

        List<PdfLineParser.ParsedLine> c4309 = byCodigo(parsed, "4309");
        assertEquals(1, c4309.size(), () -> "esperado 4309; got=" + parsed);
        assertEquals("561,98", c4309.get(0).getValorStr());
        assertEquals("2024/02", c4309.get(0).getReferencia());

        List<PdfLineParser.ParsedLine> c4310 = byCodigo(parsed, "4310");
        assertEquals(1, c4310.size(), () -> "esperado 4310; got=" + parsed);
        assertEquals("280,99", c4310.get(0).getValorStr());
        assertEquals("2024/13", c4310.get(0).getReferencia());
        assertTrue(c4310.get(0).getDescricao().toUpperCase().contains("CONTR.FUNCEF"));
    }

    @Test
    @DisplayName("layout Rita (linha única) continua intacto")
    void layoutLinhaUnicaNaoRegrediu() {
        String pagina = """
                Tipo / Rubrica Referência Discriminação Prazo Valor
                4 309 2017/06 FUNCEF CONTRIBUICAO R$ 200,42
                4 310 2017/13 CONTR.FUNCEF SUPL. AB. ANUAL R$ 100,21
                Renda Base
                R$ 1.000,00
                """;
        List<PdfLineParser.ParsedLine> parsed = parser.parseLines(pagina, DocumentType.FUNCEF);
        assertEquals("200,42", byCodigo(parsed, "4309").get(0).getValorStr());
        assertEquals("100,21", byCodigo(parsed, "4310").get(0).getValorStr());
    }

    private static List<PdfLineParser.ParsedLine> byCodigo(List<PdfLineParser.ParsedLine> parsed, String codigo) {
        return parsed.stream().filter(p -> codigo.equals(p.getCodigo())).toList();
    }
}
