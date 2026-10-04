package br.com.verticelabs.pdfprocessor.infrastructure.pdf;

import br.com.verticelabs.pdfprocessor.domain.model.DocumentType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Layout Funcef portal de autoatendimento — variante Rita de Cassia (APCEF BA):
 * prazo antes do {@code R$}, descrição quebrada em 3 linhas, linhas tipo 1/3
 * e rodapé colado ao último valor.
 */
@DisplayName("Funcef portal Rita — parser e detecção")
class FuncefPortalRitaParsingTest {

    private final PdfLineParser parser = new PdfLineParser(new PdfNormalizer());
    private final DocumentTypeDetectionServiceImpl typeDetection = new DocumentTypeDetectionServiceImpl();
    private final MonthYearDetectionServiceImpl monthYearDetection = new MonthYearDetectionServiceImpl();

    private static final String PAGINA_2016_01 = """
            FUNCEF
            Fundação dos Economiários Federais
            Mês/Ano Referência:
            2016/01
            Nº Benefício INSS:
            1291395153
            FUNDAÇÃO DOS ECONOMIÁRIOS FEDERAIS
            Nome:
            RITA DE CASSIA LOMBA PINTO
            Matrícula:
            0308086
            Tipo de Benefício: CPF:
            25006924500
            Tipo de Benefício INSS:
            INSS - Aposentadoria Tempo Contribuição
            Dep IR:
            2
            Banco:
            CAIXA
            Agência:
            15100
            Nº da Conta:
            00000772-6
            Tipo / Rubrica Referência Discriminação Prazo Valor
            2 187 2016/01 PROVENTOS INSS R$ 2.878,07

            4 328 2016/01 IMPOSTO RENDA FONTE (INSS) R$ 76,91
            4 335 2016/01 CAIXA - CONSIGNACOES 103 R$ 367,98
            4 335 2016/01 CAIXA - CONSIGNACOES 109 R$ 593,00

            Renda Base
            R$ 2.878,07
            Bruto
            R$ 2.878,07
            Descontos
            R$ 1.037,89
            Líquido
            R$ 1.840,18
            Margem Consignável
            159.48%
            IR Compensado
            R$ 0,00
            Excesso de débito
            R$ 0,00
            Base Deficit
            0%
            Observação:
            Documento emitido pelo portal de autoatendimento da fundação Página 1 de 1
            """;

    private static final String PAGINA_2017_06 = """
            FUNCEF
            Fundação dos Economiários Federais
            Mês/Ano Referência:
            2017/06
            Nº Benefício INSS:
            1291395153
            Nome:
            RITA DE CASSIA LOMBA PINTO
            Tipo / Rubrica Referência Discriminação Prazo Valor
            2 187 2017/06 PROVENTOS INSS R$ 3.067,43
            2 409 2017/06 BENEFICIO FUNCEF - IN1343 R$ 5.114,67

            4 327 2017/06 IMPOSTO RENDA FONTE (FUNCEF) R$ 75,25
            4 309 2017/06 FUNCEF CONTRIBUICAO R$ 200,42
            4 432 2017/06 EMPREST. NOVO CREDINAMICO FIXO -\s
            FGQC
            57 R$ 2,03
            4 431 2017/06 EMPREST. NOVO CREDINAMICO VARIAVEL -\s
            FGQC
            88 R$ 73,84
            4 437 2017/06 EMPREST. NOVO CREDINAMICO FIXO 57 R$ 47,28
            4 436 2017/06 EMPREST. NOVO CREDINAMICO VARIAVEL 88 R$ 1.108,97
            4 335 2017/06 CAIXA - CONSIGNACOES 92 R$ 593,00
            4 335 2017/06 CAIXA - CONSIGNACOES 105 R$ 158,61
            4 335 2017/06 CAIXA - CONSIGNACOES 86 R$ 367,98

            Renda Base
            R$ 8.182,10
            Documento emitido pelo portal de autoatendimento da fundação Página 1 de 1
            """;

    private static final String PAGINA_2025_11 = """
            FUNCEF
            Fundação dos Economiários Federais
            Mês/Ano Referência:
            2025/11
            Nº Benefício INSS:
            1291395153
            Nome:
            RITA DE CASSIA LOMBA PINTO
            Tipo / Rubrica Referência Discriminação Prazo Valor
            2 125 2025/11 SUPLEMENTACAO APOSENTADORIA R$ 7.770,51
            2 187 2025/11 PROVENTOS INSS R$ 4.523,71
            2 232 2025/11 FUNÇÃO CONF. - JUDICIAL R$ 785,90
            2 131 2025/13 SUPLEMENTACAO ABONO ANUAL R$ 7.770,51
            1 317 2025/13 AC.  CONT.FUN.ADT.SUP.AB.ANU R$ 160,35
            1 729 2025/13 AC.  UNEICEF - MENSALIDADE 1 R$ 54,00
            1 771 2025/13 AC. APCEF MENSALIDADE - BA 1 R$ 37,95
            2 236 2025/13 FUNÇÃO CONF. - JUDICIAL 13º SAL R$ 785,90

            4 427 2025/13 IR ABONO ANUAL FUNCEF R$ 1.356,09
            4 328 2025/11 IMPOSTO RENDA FONTE (INSS) R$ 342,34
            4 327 2025/11 IMPOSTO RENDA FONTE (FUNCEF) R$ 1.356,09
            4 698 2025/11 DIFERENÇA IR TOTAL R$ 901,68
            3 130 2025/13 REP. ADIANT.SUPL.ABONO ANUAL R$ 3.885,26
            4 309 2025/11 FUNCEF CONTRIBUICAO R$ 320,69
            4 310 2025/13 CONTR.FUNCEF SUPL. AB. ANUAL R$ 320,69
            3 236 2025/13 REP. FUNÇÃO CONF. - JUDICIAL 13º SAL R$ 392,95
            4 492 2025/11 EMPREST. CREDPLAN VARIAVEL - FGQC 219 R$ 60,02
            4 491 2025/11 EMPREST. CREDPLAN VARIAVEL 219 R$ 2.086,20
            4 495 2025/11 EMPREST. CREDPLAN 13º SAL R$ 2.082,13
            4 335 2025/11 CAIXA - CONSIGNACOES 48 R$ 154,96
            4 335 2025/11 CAIXA - CONSIGNACOES 102 R$ 500,00
            4 335 2025/11 CAIXA - CONSIGNACOES 108 R$ 599,99
            4 335 2025/11 CAIXA - CONSIGNACOES 111 R$ 500,00Documento emitido pelo portal de autoatendimento da fundação Página 1 de 2
            """;

    private static final String PAGINA_2025_12 = """
            FUNCEF
            Fundação dos Economiários Federais
            Mês/Ano Referência:
            2025/12
            Nº Benefício INSS:
            1291395153
            Nome:
            RITA DE CASSIA LOMBA PINTO
            Tipo / Rubrica Referência Discriminação Prazo Valor
            2 125 2025/12 SUPLEMENTACAO APOSENTADORIA R$ 7.770,51
            2 187 2025/12 PROVENTOS INSS R$ 4.523,71
            2 232 2025/12 FUNÇÃO CONF. - JUDICIAL R$ 785,90

            4 328 2025/12 IMPOSTO RENDA FONTE (INSS) R$ 342,34
            4 327 2025/12 IMPOSTO RENDA FONTE (FUNCEF) R$ 1.356,09
            4 698 2025/12 DIFERENÇA IR TOTAL R$ 901,68
            4 309 2025/12 FUNCEF CONTRIBUICAO R$ 320,69
            4 492 2025/12 EMPREST. CREDPLAN VARIAVEL - FGQC 218 R$ 60,05
            4 491 2025/12 EMPREST. CREDPLAN VARIAVEL 218 R$ 2.088,19
            4 335 2025/12 CAIXA - CONSIGNACOES 120 R$ 154,96
            4 335 2025/12 CAIXA - CONSIGNACOES 101 R$ 500,00
            4 335 2025/12 CAIXA - CONSIGNACOES 110 R$ 500,00
            4 335 2025/12 CAIXA - CONSIGNACOES 107 R$ 599,99
            4 729 2025/12 UNEICEF - MENSALIDADE R$ 75,00
            4 771 2025/12 APCEF MENSALIDADE - BA R$ 75,90

            Renda Base
            R$ 13.080,12
            Documento emitido pelo portal de autoatendimento da fundação Página 1 de 1
            """;

    @Test
    @DisplayName("2016/01: detecta FUNCEF, mês/ano e separa prazo do valor")
    void pagina2016_01() {
        assertEquals(DocumentType.FUNCEF, typeDetection.detectType(PAGINA_2016_01).block());
        Optional<String> my = monthYearDetection.detectMonthYear(PAGINA_2016_01).block();
        assertTrue(my.isPresent());
        assertEquals("2016-01", my.get());

        List<PdfLineParser.ParsedLine> parsed = parser.parseLinesFuncef(PAGINA_2016_01, "2016-01");
        assertEquals(4, parsed.size());
        assertEquals("76,91", byCodigo(parsed, "4328").get(0).getValorStr());

        List<PdfLineParser.ParsedLine> consignacoes = byCodigo(parsed, "4335");
        assertEquals(2, consignacoes.size());
        assertEquals("367,98", consignacoes.get(0).getValorStr());
        assertEquals("593,00", consignacoes.get(1).getValorStr());
        assertEquals("CAIXA - CONSIGNACOES", consignacoes.get(0).getDescricao());
        assertFalse(consignacoes.get(0).getDescricao().contains("R$"));
        assertFalse(consignacoes.get(0).getDescricao().contains("103"));
    }

    @Test
    @DisplayName("2017/06: junta descrição quebrada em 3 linhas com prazo na última")
    void pagina2017_06() {
        assertEquals(DocumentType.FUNCEF, typeDetection.detectType(PAGINA_2017_06).block());

        List<PdfLineParser.ParsedLine> parsed = parser.parseLinesFuncef(PAGINA_2017_06, "2017-06");
        assertEquals(11, parsed.size());

        PdfLineParser.ParsedLine l4432 = byCodigo(parsed, "4432").get(0);
        assertEquals("2,03", l4432.getValorStr());
        assertTrue(l4432.getDescricao().contains("FGQC"));
        assertFalse(l4432.getDescricao().contains("57"));

        PdfLineParser.ParsedLine l4431 = byCodigo(parsed, "4431").get(0);
        assertEquals("73,84", l4431.getValorStr());
        assertTrue(l4431.getDescricao().contains("FGQC"));

        assertEquals("75,25", byCodigo(parsed, "4327").get(0).getValorStr());
        assertEquals("1.108,97", byCodigo(parsed, "4436").get(0).getValorStr());
        assertEquals(3, byCodigo(parsed, "4335").size());
    }

    @Test
    @DisplayName("2025/11: linhas tipo 1/3, referência 2025/13 e rodapé colado ao último valor")
    void pagina2025_11() {
        assertEquals(DocumentType.FUNCEF, typeDetection.detectType(PAGINA_2025_11).block());
        assertEquals("2025-11", monthYearDetection.detectMonthYear(PAGINA_2025_11).block().orElseThrow());

        List<PdfLineParser.ParsedLine> parsed = parser.parseLinesFuncef(PAGINA_2025_11, "2025-11");
        assertEquals(23, parsed.size());

        PdfLineParser.ParsedLine l1317 = byCodigo(parsed, "1317").get(0);
        assertEquals("160,35", l1317.getValorStr());
        assertEquals("2025/13", l1317.getReferencia());

        PdfLineParser.ParsedLine l1729 = byCodigo(parsed, "1729").get(0);
        assertEquals("54,00", l1729.getValorStr());
        assertFalse(l1729.getDescricao().endsWith(" 1"));

        assertEquals("3.885,26", byCodigo(parsed, "3130").get(0).getValorStr());
        assertEquals("2025/13", byCodigo(parsed, "3130").get(0).getReferencia());
        assertEquals("1.356,09", byCodigo(parsed, "4327").get(0).getValorStr());

        List<PdfLineParser.ParsedLine> consignacoes = byCodigo(parsed, "4335");
        assertEquals(4, consignacoes.size());
        assertEquals("500,00", consignacoes.get(3).getValorStr());
        assertFalse(consignacoes.get(3).getDescricao().toUpperCase().contains("DOCUMENTO"));
    }

    @Test
    @DisplayName("2025/12: extrai as 15 rubricas, incluindo 4729 e 4771")
    void pagina2025_12() {
        List<PdfLineParser.ParsedLine> parsed = parser.parseLinesFuncef(PAGINA_2025_12, "2025-12");
        assertEquals(15, parsed.size());
        assertEquals("75,00", byCodigo(parsed, "4729").get(0).getValorStr());
        assertEquals("75,90", byCodigo(parsed, "4771").get(0).getValorStr());
        assertEquals("APCEF MENSALIDADE - BA", byCodigo(parsed, "4771").get(0).getDescricao());
        assertEquals("60,05", byCodigo(parsed, "4492").get(0).getValorStr());
    }

    private static List<PdfLineParser.ParsedLine> byCodigo(List<PdfLineParser.ParsedLine> parsed, String codigo) {
        return parsed.stream().filter(l -> codigo.equals(l.getCodigo())).toList();
    }
}
