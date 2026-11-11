package br.com.verticelabs.pdfprocessor.infrastructure.ai;

import br.com.verticelabs.pdfprocessor.application.incometax.IrpfDeclaracaoDataMapper;
import br.com.verticelabs.pdfprocessor.application.incometax.IrpfPrevidenciaOficialResolver;
import br.com.verticelabs.pdfprocessor.application.tributacao.IrPagamentosDeducaoAggregator;
import br.com.verticelabs.pdfprocessor.application.tributacao.dto.SimuladorIrpfRequest;
import br.com.verticelabs.pdfprocessor.domain.model.IrpfDeclaracaoData;
import br.com.verticelabs.pdfprocessor.domain.service.IncomeTaxDeclarationService.IncomeTaxInfo;
import br.com.verticelabs.pdfprocessor.domain.service.IncomeTaxDeclarationService.IncomeTaxInfo.FontePagadora;
import br.com.verticelabs.pdfprocessor.infrastructure.excel.ExcelIrpfSimulacaoMapper;
import br.com.verticelabs.pdfprocessor.infrastructure.incometax.IncomeTaxGeminiHelper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Cláudio Roberto — PDF escaneado: fontes PJ via Gemini e simulação Completa
 * quando a DIRPF foi Simplificada (prev. oficial só nas fontes).
 */
@DisplayName("Gemini fontes pagadoras — Cláudio")
class GeminiFontesParserTest {

    @Test
    @DisplayName("parseia fontes 2018: soma contrPrevOficial = 1039,19")
    void parseFontesClaudio2018() {
        String json = """
                {
                  "fontes": [
                    {
                      "nome": "FUNCEF",
                      "cnpjCpf": "00.436.923/0001-90",
                      "rendimentoTributavel": 129765.72,
                      "contribuicaoPrevOficial": 1038.14,
                      "impostoRetidoFonte": 23716.43,
                      "decimoTerceiro": 16797.92,
                      "irrfDecimoTerceiro": 1537.09
                    },
                    {
                      "nome": "FUNDO DO REGIME GERAL DE PREV SOCIAL INSS",
                      "cnpjCpf": "16.727.230/0001-97",
                      "rendimentoTributavel": 42832.18,
                      "contribuicaoPrevOficial": 0.00,
                      "impostoRetidoFonte": 2167.13,
                      "decimoTerceiro": 3393.89,
                      "irrfDecimoTerceiro": 181.50
                    },
                    {
                      "nome": "WIZ SOLUCOES E CORRETAGEM DE SEGUROS",
                      "cnpjCpf": "42.278.473/0001-03",
                      "rendimentoTributavel": 129.89,
                      "contribuicaoPrevOficial": 1.05,
                      "impostoRetidoFonte": 0.00,
                      "decimoTerceiro": 0.00,
                      "irrfDecimoTerceiro": 0.00
                    }
                  ]
                }
                """;

        List<FontePagadora> fontes = GeminiResponseParser.parseFontesResponse(json);
        assertEquals(3, fontes.size());

        BigDecimal soma = fontes.stream()
                .map(f -> f.getContribuicaoPrevOficial() != null
                        ? f.getContribuicaoPrevOficial()
                        : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        assertEquals(0, new BigDecimal("1039.19").compareTo(soma));
    }

    @Test
    @DisplayName("parseia fontes 2016: soma contrPrevOficial = 6850,56")
    void parseFontesClaudio2016() {
        String json = """
                {
                  "fontes": [
                    {
                      "nome": "CAIXA ECONOMICA FEDERAL",
                      "cnpjCpf": "00.360.305/0001-04",
                      "rendimentoTributavel": 253733.02,
                      "contribuicaoPrevOficial": 6850.56,
                      "impostoRetidoFonte": 46996.66,
                      "decimoTerceiro": 14409.61,
                      "irrfDecimoTerceiro": 4266.59
                    },
                    {
                      "nome": "FPC PAR CORRETORA DE SEGUROS S/A",
                      "cnpjCpf": "42.278.473/0001-03",
                      "rendimentoTributavel": 267.38,
                      "contribuicaoPrevOficial": 0.00,
                      "impostoRetidoFonte": 0.00,
                      "decimoTerceiro": 0.00,
                      "irrfDecimoTerceiro": 0.00
                    },
                    {
                      "nome": "FUNDO DO REGIME GERAL DE PREVIDENCIA FRGPS",
                      "cnpjCpf": "16.727.230/0001-97",
                      "rendimentoTributavel": 31807.10,
                      "contribuicaoPrevOficial": 0.00,
                      "impostoRetidoFonte": 1310.94,
                      "decimoTerceiro": 2954.42,
                      "irrfDecimoTerceiro": 103.95
                    }
                  ]
                }
                """;

        List<FontePagadora> fontes = GeminiResponseParser.parseFontesResponse(json);
        BigDecimal soma = fontes.stream()
                .map(f -> f.getContribuicaoPrevOficial() != null
                        ? f.getContribuicaoPrevOficial()
                        : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        assertEquals(0, new BigDecimal("6850.56").compareTo(soma));
    }

    @Test
    @DisplayName("Simplificado 2018: simulação usa prev. oficial das fontes (1039,19)")
    void simulacaoSimplificadaUsaFontes() {
        IncomeTaxInfo base = GeminiResponseParser.parseIncomeTaxResponse("""
                {
                  "exercicio": "2019",
                  "anoCalendario": "2018",
                  "nome": "CLAUDIO ROBERTO MENEZES DE ARAUJO",
                  "cpf": "379.391.906-44",
                  "modeloDeclaracao": "SIMPLIFICADA",
                  "rendimentosTributaveis": 172727.79,
                  "descontoSimplificado": 16754.34,
                  "deducoes": {
                    "previdenciaOficial": null,
                    "previdenciaComplementar": null,
                    "despesasMedicas": null
                  }
                }
                """);

        List<FontePagadora> fontes = GeminiResponseParser.parseFontesResponse("""
                {
                  "fontes": [
                    {"nome":"FUNCEF","cnpjCpf":"00.436.923/0001-90","rendimentoTributavel":129765.72,
                     "contribuicaoPrevOficial":1038.14,"impostoRetidoFonte":23716.43,
                     "decimoTerceiro":16797.92,"irrfDecimoTerceiro":1537.09},
                    {"nome":"INSS","cnpjCpf":"16.727.230/0001-97","rendimentoTributavel":42832.18,
                     "contribuicaoPrevOficial":0.00,"impostoRetidoFonte":2167.13,
                     "decimoTerceiro":3393.89,"irrfDecimoTerceiro":181.50},
                    {"nome":"WIZ","cnpjCpf":"42.278.473/0001-03","rendimentoTributavel":129.89,
                     "contribuicaoPrevOficial":1.05,"impostoRetidoFonte":0.00,
                     "decimoTerceiro":0.00,"irrfDecimoTerceiro":0.00}
                  ]
                }
                """);

        IncomeTaxInfo withFontes = IncomeTaxGeminiHelper.withFontes(base, fontes);
        IrpfDeclaracaoData data = new IrpfDeclaracaoDataMapper().fromIncomeTaxInfo(withFontes);

        assertEquals(0, new BigDecimal("1039.19").compareTo(
                IrpfPrevidenciaOficialResolver.resolver(data)));

        SimuladorIrpfRequest request = new ExcelIrpfSimulacaoMapper(new IrPagamentosDeducaoAggregator())
                .fromDeclaracao(data, BigDecimal.ZERO, false);
        assertEquals(0, new BigDecimal("1039.19").compareTo(request.getPrevidenciaOficial()));
    }

    @Test
    @DisplayName("withFontes preserva demais campos")
    void withFontesPreservaCampos() {
        IncomeTaxInfo base = GeminiResponseParser.parseIncomeTaxResponse("""
                {
                  "exercicio": "2019",
                  "anoCalendario": "2018",
                  "nome": "CLAUDIO",
                  "cpf": "379.391.906-44",
                  "modeloDeclaracao": "SIMPLIFICADA",
                  "rendimentosTributaveis": 100000.00,
                  "descontoSimplificado": 16754.34
                }
                """);
        List<FontePagadora> fontes = GeminiResponseParser.parseFontesResponse("""
                {"fontes":[{"nome":"FUNCEF","cnpjCpf":"00.436.923/0001-90",
                 "rendimentoTributavel":1000.00,"contribuicaoPrevOficial":100.00,
                 "impostoRetidoFonte":0,"decimoTerceiro":0,"irrfDecimoTerceiro":0}]}
                """);
        IncomeTaxInfo merged = IncomeTaxGeminiHelper.withFontes(base, fontes);
        assertEquals(1, merged.getFontesPagadoras().size());
        assertEquals("CLAUDIO", merged.getNome());
        assertEquals("SIMPLIFICADO", merged.getTipoTributacao());
        assertTrue(merged.getPagamentosEfetuados() == null || merged.getPagamentosEfetuados().isEmpty());
    }
}
