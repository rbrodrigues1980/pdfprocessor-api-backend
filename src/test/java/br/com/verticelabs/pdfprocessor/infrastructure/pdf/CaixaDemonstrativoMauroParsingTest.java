package br.com.verticelabs.pdfprocessor.infrastructure.pdf;

import br.com.verticelabs.pdfprocessor.domain.model.DocumentType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Demonstrativo de Pagamento CAIXA (ativo) — Mauro (APCEF BA): {@code R$} antes do valor,
 * {@code Mês / Ano de Pagamento} com espaços e códigos de 5 dígitos.
 */
@DisplayName("CAIXA demonstrativo Mauro — parser e detecção")
class CaixaDemonstrativoMauroParsingTest {

    private final PdfLineParser parser = new PdfLineParser(new PdfNormalizer());
    private final DocumentTypeDetectionServiceImpl typeDetection = new DocumentTypeDetectionServiceImpl();
    private final MonthYearDetectionServiceImpl monthYearDetection = new MonthYearDetectionServiceImpl();

    private static final String CABECALHO = """
            DEMONSTRATIVO DE PAGAMENTO
            Nome Data Matrícula
            Mês / Ano de Pagamento CA/SR Código de Lotação
            SiglaUnidade de Lotação
            %s SA-SEV FAROL DE ITAPUA 105 3351-6
            MAURO ANTONIO COSTA DE MENDONCA 07/06/2010 c109694-7
            AG. SALVADOR SHOPPING, BA PORTE: 1
            Tipo Discriminação da Rubrica Competência Prazo Valor
            """;

    private static final String RODAPE = """
            Salário Bruto Valor Descontos Salário Liquido
            Marg. Consignável 5% Cartões
            R$ 7.547,71 R$ 6.593,28 R$ 954,43
             --- R$ 0,00 R$ 0,00 R$ 245,53
            Excesso de Débito
            R$ 750,60
            Liquido creditado em 20/01/2016 Liquido creditado em
            R$ 916,55
            Marg. Consignável de 30%
            """;

    private static final String PAGINA_2016_01 = CABECALHO.formatted("JANEIRO / 2016") + """
            1034 AC  APIP/IP - CONVERSAO 01/2016 001 R$ 496,83
            1120 AC  GRAT NATAL - MEDIA HORA EXTRA 12/2015 R$ 48,88
            1362 AC  OUTROS VALORES RESSARCIR 01/2016 001 R$ 3.877,00
            2002 SALARIO PADRAO 01/2016 R$ 2.981,00
            2044 SERVICO EXTRAORDINARIO 01/2016 R$ 94,78
            2057 MEDIA HORA EXTRA - REPOUSO 01/2016 R$ 49,22
            3140 REP CTVA - FG/CC NAO EFETIVA 04/2013 001 R$ 1.254,00
            3260 REP MEDIA CTVA - REPOUSO REMUNERADO 04/2013 001 R$ 132,00
            3274 REP FUNCAO GRATIFICADA NAO EFETIVA 04/2013 001 R$ 1.387,00
            3278 REP MEDIA FUNCAO GRATIFICADA - RR 04/2013 001 R$ 146,00
            3280 REP PORTE UNIDADE - FUNCAO GRAT NAO 04/2013 001 R$ 958,46
            3326 REP IMPOSTO DE RENDA - GRAT NATAL 12/2015 R$ 11,00
            3362 REP OUTROS VALORES RESSARCIR 01/2016 002 R$ 1.938,50
            3454 REP ADIANTAMENTO OUTRAS DESPESAS 01/2016 001 R$ 496,83
            4317 SINDICATO - DESCONTO ASSISTENCIAL 01/2016 R$ 50,00
            4325 IMPOSTO DE RENDA 01/2016 999 R$ 85,51
            4415 VALE CULTURA 12/2015 R$ 4,00
            4460 SAUDE CAIXA - MENSALIDADE 01/2016 001 R$ 59,62
            4771 ASSOCIACAO - MENSALIDADE 01/2016 R$ 29,81
            4771 ASSOCIACAO - MENSALIDADE 01/2016 R$ 40,55
            """ + RODAPE;

    private static final String PAGINA_2017_01 = CABECALHO.formatted("JANEIRO / 2017") + """
            1034 AC  APIP/IP - CONVERSAO 01/2017 001 R$ 561,33
            1090 AC  GRAT NATAL - 13 SALARIO 12/2016 R$ 6,54
            1120 AC  GRAT NATAL - MEDIA HORA EXTRA 12/2016 R$ 205,63
            2002 SALARIO PADRAO 01/2017 R$ 3.526,00
            2044 SERVICO EXTRAORDINARIO 01/2017 R$ 688,03
            2057 MEDIA HORA EXTRA - REPOUSO 01/2017 R$ 334,40
            3315 REP INSS - CONTR GRAT NATAL 12/2016 R$ 23,34
            3326 REP IMPOSTO DE RENDA - GRAT NATAL 12/2016 R$ 42,36
            3369 REP FUNCEF - GRAT NATAL NOVO PLANO 12/2016 R$ 0,58
            3454 REP ADIANTAMENTO OUTRAS DESPESAS 01/2017 001 R$ 561,33
            4313 INSS CONTRIBUICAO 01/2017 001 R$ 500,32
            4317 SINDICATO - DESCONTO ASSISTENCIAL 01/2017 R$ 50,00
            4325 IMPOSTO DE RENDA 01/2017 999 R$ 171,08
            4335 CEF - CONSIGNACOES 01/2011 094 R$ 165,92
            4335 CEF - CONSIGNACOES 05/2012 093 R$ 80,67
            4335 CEF - CONSIGNACOES 07/2011 082 R$ 132,22
            4335 CEF - CONSIGNACOES 04/2012 091 R$ 185,34
            4335 CEF - CONSIGNACOES 03/2011 091 R$ 132,57
            4346 FUNCEF - NOVO PLANO 01/2017 999 R$ 352,60
            4410 SINDICATO MENSALIDADE 01/2017 R$ 52,89
            4415 VALE CULTURA 12/2016 R$ 4,00
            4460 SAUDE CAIXA - MENSALIDADE 01/2017 001 R$ 67,36
            4461 SAUDE CAIXA - PARTICIPACAO 01/2017 001 R$ 16,00
            4771 ASSOCIACAO - MENSALIDADE 01/2017 R$ 35,26
            4771 ASSOCIACAO - MENSALIDADE 01/2017 R$ 43,80
            """ + RODAPE;

    private static final String PAGINA_2025_02_CONTINUACAO = CABECALHO.formatted("FEVEREIRO / 2025") + """
            43345 CAIXA CONSIGNACOES SINCR 01/2022 087 R$ 237,56
            43345 CAIXA CONSIGNACOES SINCR 01/2024 133 R$ 234,62
            43345 CAIXA CONSIGNACOES SINCR 01/2025 144 R$ 300,00
            43345 CAIXA CONSIGNACOES SINCR 05/2021 076 R$ 185,39
            43345 CAIXA CONSIGNACOES SINCR 04/2023 124 R$ 197,37
            """ + RODAPE;

    private static final String PAGINA_2026_02 = CABECALHO.formatted("FEVEREIRO / 2026") + """
            1034 AC  APIP/IP - CONVERSAO 02/2026 001 R$ 346,06
            2002 SALARIO PADRAO 02/2026 R$ 7.513,00
            2068 ADIANT GRATIFICACAO NATAL 02/2026 001 R$ 5.191,00
            4346 FUNCEF - NOVO PLANO 02/2026 999 R$ 830,56
            4369 FUNCEF - GRAT NATAL NOVO PLANO 02/2026 001 R$ 415,28
            43355 EMPRESTIMO E-CONSIGNADO 02/2026 079 R$ 145,92
            43355 EMPRESTIMO E-CONSIGNADO 02/2026 143 R$ 242,65
            """ + RODAPE;

    private static final String CONTA_ENERGIA = """
            Consumo-TUSD kWh           100,00  0,74841431             74,84              3,45
            Consumo-TE kWh           100,00  0,37020123             37,02              1,70
            Ilum. Púb. Municipal             13,86
            www.neoenergia.com|Ligue grátis 116
             DANFE - DOCUMENTO AUXILIAR DA NOTA FISCAL DE ENERGIA ELÉTRICA ELETRÔNICA
             COMPANHIA DE ELETRICIDADE DO ESTADO DA BAHIA
             NOME DO CLIENTE:
             MAURO ANTONIO COSTA DE MENDONCA
            """;

    @Test
    @DisplayName("2016/01: CAIXA, descrição limpa, competência e prazo separados do R$")
    void pagina2016_01() {
        assertEquals(DocumentType.CAIXA, typeDetection.detectType(PAGINA_2016_01).block());
        assertEquals("2016-01", monthYearDetection.detectMonthYear(PAGINA_2016_01).block().orElseThrow());

        List<PdfLineParser.ParsedLine> parsed = parser.parseLines(PAGINA_2016_01, DocumentType.CAIXA);
        assertEquals(20, parsed.size());

        PdfLineParser.ParsedLine l1034 = byCodigo(parsed, "1034").get(0);
        assertEquals("AC APIP/IP - CONVERSAO", l1034.getDescricao());
        assertEquals("01/2016", l1034.getReferencia());
        assertEquals("496,83", l1034.getValorStr());

        assertEquals("12/2015", byCodigo(parsed, "1120").get(0).getReferencia());
        assertEquals("85,51", byCodigo(parsed, "4325").get(0).getValorStr());
        assertEquals("IMPOSTO DE RENDA", byCodigo(parsed, "4325").get(0).getDescricao());
        assertTrue(parsed.stream().noneMatch(l -> l.getDescricao().contains("R$")));
    }

    @Test
    @DisplayName("2017/01 com rubrica FUNCEF - NOVO PLANO continua CAIXA")
    void pagina2017_01ComRubricaFuncef() {
        assertEquals(DocumentType.CAIXA, typeDetection.detectType(PAGINA_2017_01).block());

        List<PdfLineParser.ParsedLine> parsed = parser.parseLines(PAGINA_2017_01, DocumentType.CAIXA);
        assertEquals(25, parsed.size());
        assertTrue(parsed.stream().noneMatch(l -> l.getDescricao().contains("R$")));
        assertTrue(parsed.stream().noneMatch(l -> l.getDescricao().matches(".*\\d{2}/\\d{4}.*")));

        PdfLineParser.ParsedLine l4346 = byCodigo(parsed, "4346").get(0);
        assertEquals("FUNCEF - NOVO PLANO", l4346.getDescricao());
        assertEquals("352,60", l4346.getValorStr());
        assertEquals(5, byCodigo(parsed, "4335").size());
    }

    @Test
    @DisplayName("2025/02 continuação: código de 5 dígitos 43345")
    void continuacaoCodigoCincoDigitos() {
        assertEquals(DocumentType.CAIXA, typeDetection.detectType(PAGINA_2025_02_CONTINUACAO).block());

        List<PdfLineParser.ParsedLine> parsed = parser.parseLines(PAGINA_2025_02_CONTINUACAO, DocumentType.CAIXA);
        assertEquals(5, parsed.size());
        assertTrue(parsed.stream().allMatch(l -> "43345".equals(l.getCodigo())));
        assertEquals("CAIXA CONSIGNACOES SINCR", parsed.get(0).getDescricao());
        assertEquals("237,56", parsed.get(0).getValorStr());
    }

    @Test
    @DisplayName("2026/02: 43355 E-CONSIGNADO e 4369")
    void pagina2026_02() {
        assertEquals(DocumentType.CAIXA, typeDetection.detectType(PAGINA_2026_02).block());

        List<PdfLineParser.ParsedLine> parsed = parser.parseLines(PAGINA_2026_02, DocumentType.CAIXA);
        assertEquals(7, parsed.size());
        List<PdfLineParser.ParsedLine> consignado = byCodigo(parsed, "43355");
        assertEquals(2, consignado.size());
        assertEquals("EMPRESTIMO E-CONSIGNADO", consignado.get(0).getDescricao());
        assertEquals("242,65", consignado.get(1).getValorStr());
        assertEquals("415,28", byCodigo(parsed, "4369").get(0).getValorStr());
    }

    @Test
    @DisplayName("looksLikePayslipPage: conta de energia não; contracheques sim")
    void looksLikePayslipPage() {
        assertFalse(DocumentTypeDetectionServiceImpl.looksLikePayslipPage(CONTA_ENERGIA));
        assertTrue(DocumentTypeDetectionServiceImpl.looksLikePayslipPage(PAGINA_2016_01));
        assertTrue(DocumentTypeDetectionServiceImpl.looksLikePayslipPage(PAGINA_2025_02_CONTINUACAO));
        assertTrue(DocumentTypeDetectionServiceImpl.looksLikePayslipPage("""
                FUNCEF
                Mês/Ano Referência:
                2017/12
                Tipo / Rubrica Referência Discriminação Prazo Valor
                2 187 2017/12 PROVENTOS INSS R$ 3.659,69
                """));
    }

    private static List<PdfLineParser.ParsedLine> byCodigo(List<PdfLineParser.ParsedLine> parsed, String codigo) {
        return parsed.stream().filter(l -> codigo.equals(l.getCodigo())).toList();
    }
}
