package br.com.verticelabs.pdfprocessor.infrastructure.incometax;

import br.com.verticelabs.pdfprocessor.domain.service.IncomeTaxDeclarationService.IncomeTaxInfo;
import br.com.verticelabs.pdfprocessor.infrastructure.ai.GeminiResponseParser;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IncomeTaxGeminiHelperTest {

    @Test
    void rejeitaExtracaoComIdentificadorMasValoresZerados() {
        String json = """
                {
                  "exercicio": "2020",
                  "anoCalendario": "2019",
                  "nome": "ADRIANA MARIA QUEIROZ FREITAS",
                  "cpf": "459.445.986-20",
                  "rendimentosTributaveis": 0.0,
                  "baseCalculoImposto": 0.0,
                  "totalImpostoDevido": 0.0,
                  "saldoImpostoPagar": 0.0,
                  "impostoRestituir": 0.0
                }
                """;

        var info = GeminiResponseParser.parseIncomeTaxResponse(json);

        assertFalse(IncomeTaxGeminiHelper.isIncomeTaxInfoSufficient(info));
    }

    @Test
    void aceitaExtracaoComRendimentosPositivos() {
        String json = """
                {
                  "exercicio": "2024",
                  "anoCalendario": "2023",
                  "nome": "FULANO DA SILVA",
                  "cpf": "123.456.789-00",
                  "rendimentosTributaveis": 120000.50,
                  "totalImpostoDevido": 8000.00
                }
                """;

        var info = GeminiResponseParser.parseIncomeTaxResponse(json);

        assertTrue(IncomeTaxGeminiHelper.isIncomeTaxInfoSufficient(info));
        assertTrue(info.getRendimentosTributaveis().compareTo(BigDecimal.ZERO) > 0);
    }

    @Test
    void enrichPreencheRendimentosPjEImpostoDevidoIQuandoGeminiRetornaApenasTotais() {
        IncomeTaxInfo parcial = new IncomeTaxInfo(
                "ADRIANA MARIA QUEIROZ FREITAS", "459.445.986-20", "2019", "2020",
                new BigDecimal("245616.27"), new BigDecimal("57112.15"), new BigDecimal("1713.36"), null,
                null, null, null,
                new BigDecimal("55398.79"), new BigDecimal("9653.02"),
                new BigDecimal("291368.16"), new BigDecimal("45751.89"),
                new BigDecimal("45745.77"), null, BigDecimal.ZERO,
                new BigDecimal("7707.96"), null, new BigDecimal("31578.25"), null, null,
                new BigDecimal("6465.68"), null, null, null, null,
                null, null, null, null, null, null, null,
                null, null,
                null, null, null, null, null,
                null, null, null, null,
                null, null,
                Collections.emptyList(), Collections.emptyList(),
                null, Collections.emptyList(), null, Collections.emptyList(),
                null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null, null, null, null,
                Collections.emptyList());

        IncomeTaxInfo enriched = IncomeTaxGeminiHelper.enrich(parcial);

        assertEquals(new BigDecimal("291368.16"), enriched.getRendimentosTributaveisTitularPJ());
        assertEquals(new BigDecimal("55398.79"), enriched.getImpostoDevidoI());
        assertEquals(new BigDecimal("45745.77"), enriched.getImpostoPagoTotal());
        assertEquals("COMPLETO", enriched.getTipoTributacao());
    }

    @Test
    void enrichCorrigePrevComplementarQuandoTotalDivergeDaSomaDasLinhas() {
        IncomeTaxInfo parcial = new IncomeTaxInfo(
                "ADRIANA MARIA QUEIROZ FREITAS", "459.445.986-20", "2019", "2020",
                new BigDecimal("245616.27"), null, null, null,
                null, null, null,
                null, null,
                new BigDecimal("291368.16"), new BigDecimal("49137.81"),
                null, null, null,
                new BigDecimal("7707.96"), null, new BigDecimal("31578.25"), null, null,
                new BigDecimal("6465.68"), null, null, null, null,
                null, null, null, null, null, null, null,
                null, null,
                null, null, null, null, null,
                null, null, null, null,
                null, null,
                Collections.emptyList(), Collections.emptyList(),
                null, Collections.emptyList(), null, Collections.emptyList(),
                null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null, null, null, null,
                Collections.emptyList());

        IncomeTaxInfo enriched = IncomeTaxGeminiHelper.enrich(parcial);

        assertEquals(new BigDecimal("34964.17"), enriched.getDeducoesContribPrevCompl());
        assertEquals(new BigDecimal("49137.81"), enriched.getDeducoes());
    }

    @Test
    void enrichCorrigePrevComplementarQuandoBaseCalculoIndicaTotalMaior() {
        IncomeTaxInfo parcial = new IncomeTaxInfo(
                "ADRIANA MARIA QUEIROZ FREITAS", "459.445.986-20", "2019", "2020",
                new BigDecimal("242230.35"), null, null, null,
                null, null, null,
                null, null,
                new BigDecimal("291368.16"), new BigDecimal("45751.89"),
                null, null, null,
                new BigDecimal("7707.96"), null, new BigDecimal("31578.25"), null, null,
                new BigDecimal("6465.68"), null, null, null, null,
                null, null, null, null, null, null, null,
                null, null,
                null, null, null, null, null,
                null, null, null, null,
                null, null,
                Collections.emptyList(), Collections.emptyList(),
                null, Collections.emptyList(), null, Collections.emptyList(),
                null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null, null, null, null,
                Collections.emptyList());

        IncomeTaxInfo enriched = IncomeTaxGeminiHelper.enrich(parcial);

        assertEquals(new BigDecimal("34964.17"), enriched.getDeducoesContribPrevCompl());
        assertEquals(new BigDecimal("49137.81"), enriched.getDeducoes());
    }

    @Test
    void reconcileDeducoesNaoAlteraQuandoValoresJaSaoConsistentes() {
        var result = IncomeTaxGeminiHelper.reconcileDeducoes(
                new BigDecimal("291368.16"),
                new BigDecimal("242230.35"),
                new BigDecimal("49137.81"),
                new BigDecimal("7707.96"), null, new BigDecimal("34964.17"),
                null, null, new BigDecimal("6465.68"),
                null, null, null, null, null);

        assertEquals(new BigDecimal("49137.81"), result.deducoesTotal());
        assertEquals(new BigDecimal("34964.17"), result.prevComplementar());
    }

    @Test
    void enrichDerivaImpostoDevidoRRAQuandoGeminiOmiteCampo_nadelson2022() {
        // Espelho RFB: I 52.161,90 + RRA 28.033,36 = Total 80.195,26; Gemini omite RRA
        IncomeTaxInfo parcial = new IncomeTaxInfo(
                "NADELSON LIMA DE CARVALHO", "000.000.000-00", "2022", "2023",
                new BigDecimal("227615.37"), new BigDecimal("52161.90"), BigDecimal.ZERO,
                new BigDecimal("52161.90"),
                null, null, null,
                new BigDecimal("80195.26"), null,
                new BigDecimal("300000.00"), null,
                null, null, null,
                null, null, null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null,
                null, new BigDecimal("17.39"),
                "COMPLETO", null, null, null, null,
                null, null, null, null,
                null, null,
                Collections.emptyList(), Collections.emptyList(),
                null, Collections.emptyList(), null, Collections.emptyList(),
                null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null, null, null, null,
                Collections.emptyList());

        IncomeTaxInfo enriched = IncomeTaxGeminiHelper.enrich(parcial);

        assertEquals(0, new BigDecimal("28033.36").compareTo(enriched.getImpostoDevidoRRA()));
        assertEquals(0, new BigDecimal("52161.90").compareTo(enriched.getImpostoDevidoI()));
        assertEquals(0, new BigDecimal("80195.26").compareTo(enriched.getTotalImpostoDevido()));
    }

    @Test
    void enrichNaoDerivaRRAQuandoTotalIgualImpostoDevidoI() {
        IncomeTaxInfo parcial = new IncomeTaxInfo(
                "FULANO", "123.456.789-00", "2022", "2023",
                new BigDecimal("100000.00"), new BigDecimal("10000.00"), BigDecimal.ZERO,
                new BigDecimal("10000.00"),
                null, null, null,
                new BigDecimal("10000.00"), null,
                new BigDecimal("120000.00"), null,
                null, null, null,
                null, null, null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null,
                null, null,
                "COMPLETO", null, null, null, null,
                null, null, null, null,
                null, null,
                Collections.emptyList(), Collections.emptyList(),
                null, Collections.emptyList(), null, Collections.emptyList(),
                null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null, null, null, null,
                Collections.emptyList());

        IncomeTaxInfo enriched = IncomeTaxGeminiHelper.enrich(parcial);

        assertTrue(enriched.getImpostoDevidoRRA() == null
                || enriched.getImpostoDevidoRRA().compareTo(BigDecimal.ZERO) == 0);
    }

    @Test
    void enrichPreservaRRAJaExtraidoPeloGemini() {
        IncomeTaxInfo parcial = new IncomeTaxInfo(
                "NADELSON LIMA DE CARVALHO", "000.000.000-00", "2022", "2023",
                new BigDecimal("227615.37"), new BigDecimal("52161.90"), BigDecimal.ZERO,
                new BigDecimal("52161.90"),
                null, null, new BigDecimal("28033.36"),
                new BigDecimal("80195.26"), null,
                new BigDecimal("300000.00"), null,
                null, null, null,
                null, null, null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null,
                null, null,
                "COMPLETO", null, null, null, null,
                null, null, null, null,
                null, null,
                Collections.emptyList(), Collections.emptyList(),
                null, Collections.emptyList(), null, Collections.emptyList(),
                null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null, null, null, null,
                Collections.emptyList());

        IncomeTaxInfo enriched = IncomeTaxGeminiHelper.enrich(parcial);

        assertEquals(0, new BigDecimal("28033.36").compareTo(enriched.getImpostoDevidoRRA()));
    }

    @Test
    void parseJsonMultiPageComImpostoDevidoRRA() {
        String json = """
                {
                  "exercicio": "2023",
                  "anoCalendario": "2022",
                  "nome": "NADELSON LIMA DE CARVALHO",
                  "cpf": "000.000.000-00",
                  "modeloDeclaracao": "COMPLETA",
                  "rendimentosTributaveis": 300000.00,
                  "baseCalculoImposto": 227615.37,
                  "impostoDevido": 52161.90,
                  "deducaoIncentivo": 0.00,
                  "impostoDevidoI": 52161.90,
                  "impostoDevidoRRA": 28033.36,
                  "totalImpostoDevido": 80195.26,
                  "aliquotaEfetiva": 17.39
                }
                """;

        var info = GeminiResponseParser.parseIncomeTaxResponse(json);

        assertTrue(IncomeTaxGeminiHelper.isIncomeTaxInfoSufficient(info));
        assertEquals(0, new BigDecimal("28033.36").compareTo(info.getImpostoDevidoRRA()));
        assertEquals(0, new BigDecimal("80195.26").compareTo(info.getTotalImpostoDevido()));
    }
}
