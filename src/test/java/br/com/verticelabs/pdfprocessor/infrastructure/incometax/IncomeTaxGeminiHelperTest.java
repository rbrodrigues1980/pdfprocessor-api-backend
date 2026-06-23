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
}
