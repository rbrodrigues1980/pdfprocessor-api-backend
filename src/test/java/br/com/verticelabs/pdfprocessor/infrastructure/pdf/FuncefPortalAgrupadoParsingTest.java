package br.com.verticelabs.pdfprocessor.infrastructure.pdf;

import br.com.verticelabs.pdfprocessor.domain.model.DocumentType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Layout Funcef portal de autoatendimento (PDF agrupado multi-ano).
 * Fixtures baseadas no contracheque Carlos Augusto (2017/12 e 2017/11 págs. 14–15).
 */
@DisplayName("Funcef portal agrupado — parser e detecção")
class FuncefPortalAgrupadoParsingTest {

    private final PdfLineParser parser = new PdfLineParser(new PdfNormalizer());
    private final DocumentTypeDetectionServiceImpl typeDetection = new DocumentTypeDetectionServiceImpl();
    private final MonthYearDetectionServiceImpl monthYearDetection = new MonthYearDetectionServiceImpl();

    private static final String PAGINA_2017_12 = """
            FUNCEF
            Fundação dos Economiários Federais
            Mês/Ano Referência:
            2017/12
            Nº Benefício INSS:
            1331288638
            FUNDAÇÃO DOS ECONOMIÁRIOS FEDERAIS
            Nome:
            CARLOS AUGUSTO ALVES DE SOUZA
            Matrícula:
            2072877
            Tipo de Benefício:
            REG/REPLAN - Benef Programado Pleno
            CPF:
            10310754534
            Tipo de Benefício INSS:
            INSS - Aposentadoria Tempo Contribuição
            Dep IR:
            0
            Banco:
            CAIXA
            Agência:
            20814
            Nº da Conta:
            00100020238-5
            Tipo / Rubrica Referência Discriminação Prazo Valor
            2 187 2017/12 PROVENTOS INSS R$ 3.659,69
            2 033 2017/12 SUPL. APOS. TEMPO CONTRIB. BENEF. SALD. R$ 11.622,86
            4 430 2017/12 CONTRIBUIÇÃO EXTRAORDINARIA 2014 R$ 323,12
            4 477 2017/12 CONTRIBUIÇÃO EXTRAORDINARIA 2015 R$ 913,56
            4 328 2017/12 IMPOSTO RENDA FONTE (INSS) R$ 194,15
            4 327 2017/12 IMPOSTO RENDA FONTE (FUNCEF) R$ 2.298,96
            4 362 2017/12 TAXA ADMINISTRATIVA - SALDADO R$ 101,70
            4 771 2017/12 APCEF MENSALIDADE - SE R$ 56,43
            4 776 2017/12 ASSOC. APOSENT./MENSALIDADE - SE R$ 30,28
            Renda Base
            R$ 15.282,55
            Bruto
            R$ 15.282,55
            Descontos
            R$ 3.918,20
            Líquido
            R$ 11.364,35
            Documento emitido pelo portal de autoatendimento da fundação Página 1 de 1
            """;

    private static final String PAGINA_14_2017_11 = """
            FUNCEF
            Fundação dos Economiários Federais
            Mês/Ano Referência:
            2017/11
            Nº Benefício INSS:
            1331288638
            FUNDAÇÃO DOS ECONOMIÁRIOS FEDERAIS
            Nome:
            CARLOS AUGUSTO ALVES DE SOUZA
            Tipo / Rubrica Referência Discriminação Prazo Valor
            2 187 2017/11 PROVENTOS INSS R$ 3.659,69
            2 033 2017/11 SUPL. APOS. TEMPO CONTRIB. BENEF.\s
            SALD.
            R$ 11.622,86
            2 110 2017/13 ABONO ANUAL FUNCEF R$ 8.717,15
            4 459 2017/13 CONT. EXTRAORDINARIA ABONO ANUAL -\s
            2014
            R$ 242,34
            4 771 2017/11 APCEF MENSALIDADE - SE R$ 56,43
            Documento emitido pelo portal de autoatendimento da fundação Página 1 de 2
            """;

    private static final String PAGINA_15_CONTINUACAO = """
            4 776 2017/11 ASSOC. APOSENT./MENSALIDADE - SE R$ 30,28

            Renda Base
            R$ 15.282,55
            Bruto
            R$ 27.659,39
            Descontos
            R$ 8.340,38
            Líquido
            R$ 19.319,01
            Documento emitido pelo portal de autoatendimento da fundação Página 2 de 2
            """;

    @Test
    @DisplayName("detecta layout portal como FUNCEF")
    void detectaPortalComoFuncef() {
        DocumentType type = typeDetection.detectType(PAGINA_2017_12).block();
        assertEquals(DocumentType.FUNCEF, type);
    }

    @Test
    @DisplayName("Mês/Ano Referência → 2017-12")
    void detectaMesAnoReferencia() {
        Optional<String> my = monthYearDetection.detectMonthYear(PAGINA_2017_12).block();
        assertTrue(my.isPresent());
        assertEquals("2017-12", my.get());
    }

    @Test
    @DisplayName("página 2017/12 extrai códigos e valores com R$")
    void parsePagina2017_12() {
        List<PdfLineParser.ParsedLine> parsed = parser.parseLinesFuncef(PAGINA_2017_12, "2017-12");
        Map<String, PdfLineParser.ParsedLine> byCodigo = indexByCodigo(parsed);

        assertEquals(9, parsed.size());
        assertEquals("3.659,69", byCodigo.get("2187").getValorStr());
        assertEquals("11.622,86", byCodigo.get("2033").getValorStr());
        assertEquals("323,12", byCodigo.get("4430").getValorStr());
        assertEquals("2.298,96", byCodigo.get("4327").getValorStr());
        assertEquals("56,43", byCodigo.get("4771").getValorStr());
        assertFalse(byCodigo.get("2187").getDescricao().contains("R$"));
        assertEquals("PROVENTOS INSS", byCodigo.get("2187").getDescricao());
    }

    @Test
    @DisplayName("página 14: junta descrição quebrada SUPL + SALD + valor")
    void joinLinhasQuebradas() {
        List<PdfLineParser.ParsedLine> parsed = parser.parseLinesFuncef(PAGINA_14_2017_11, "2017-11");
        Map<String, PdfLineParser.ParsedLine> byCodigo = indexByCodigo(parsed);

        assertTrue(parsed.size() >= 4);
        assertEquals("11.622,86", byCodigo.get("2033").getValorStr());
        assertTrue(byCodigo.get("2033").getDescricao().toUpperCase().contains("SALD"));
        assertEquals("242,34", byCodigo.get("4459").getValorStr());
        assertEquals("2017/13", byCodigo.get("2110").getReferencia());
    }

    @Test
    @DisplayName("página 15 continuação: extrai 4776 e parece continuação portal")
    void parseContinuacaoPagina15() {
        assertTrue(DocumentTypeDetectionServiceImpl.looksLikeFuncefPortalContinuation(PAGINA_15_CONTINUACAO));
        assertFalse(DocumentTypeDetectionServiceImpl.looksLikeFuncefPortalContinuation(PAGINA_2017_12));

        List<PdfLineParser.ParsedLine> parsed = parser.parseLinesFuncef(PAGINA_15_CONTINUACAO, "2017-11");
        assertEquals(1, parsed.size());
        assertEquals("4776", parsed.get(0).getCodigo());
        assertEquals("30,28", parsed.get(0).getValorStr());
        assertEquals("2017/11", parsed.get(0).getReferencia());
    }

    @Test
    @DisplayName("mês na página de continuação via linha (sem cabeçalho)")
    void mesNaContinuacaoViaLinha() {
        Optional<String> my = monthYearDetection.detectMonthYear(PAGINA_15_CONTINUACAO).block();
        assertTrue(my.isPresent());
        assertEquals("2017-11", my.get());
    }

    private static final String PAGINA_2_SO_TOTAIS = """
            Renda Base
            R$ 15.282,55
            Bruto
            R$ 186.284,90
            Descontos
            R$ 29.343,54
            Líquido
            R$ 156.941,36
            Margem Consignável
            R$ 0,00
            Documento emitido pelo portal de autoatendimento da fundação Página 2 de 2
            """;

    private static final String PAGINA_2_SO_RODAPE = """
            Documento emitido pelo portal de autoatendimento da fundação Página 2 de 2
            """;

    @Test
    @DisplayName("Página 2 de 2 só com totais → totals-only (pular Gemini)")
    void detectaContinuacaoSoTotais() {
        assertTrue(DocumentTypeDetectionServiceImpl.isFuncefPortalTotalsOnlyContinuation(PAGINA_2_SO_TOTAIS));
        assertTrue(DocumentTypeDetectionServiceImpl.isFuncefPortalTotalsOnlyContinuation(PAGINA_2_SO_RODAPE));
        assertFalse(DocumentTypeDetectionServiceImpl.isFuncefPortalTotalsOnlyContinuation(PAGINA_15_CONTINUACAO));
        assertFalse(DocumentTypeDetectionServiceImpl.isFuncefPortalTotalsOnlyContinuation(PAGINA_2017_12));
    }

    private static Map<String, PdfLineParser.ParsedLine> indexByCodigo(List<PdfLineParser.ParsedLine> parsed) {
        return parsed.stream().collect(Collectors.toMap(
                PdfLineParser.ParsedLine::getCodigo,
                Function.identity(),
                (a, b) -> a));
    }
}
