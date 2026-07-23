package br.com.verticelabs.pdfprocessor.infrastructure.ai;

import br.com.verticelabs.pdfprocessor.application.tributacao.IrPagamentosDeducaoAggregator;
import br.com.verticelabs.pdfprocessor.application.tributacao.dto.DeducoesPagamentosDTO;
import br.com.verticelabs.pdfprocessor.domain.model.IrpfDeclaracaoData;
import br.com.verticelabs.pdfprocessor.domain.model.IrpfDeclaracaoData.PagamentoEfetuadoIrpf;
import br.com.verticelabs.pdfprocessor.domain.service.IncomeTaxDeclarationService.IncomeTaxInfo.PagamentoEfetuado;
import br.com.verticelabs.pdfprocessor.infrastructure.incometax.IncomeTaxGeminiHelper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("GeminiResponseParser — pagamentos e dependentes")
class GeminiPagamentosDependentesParserTest {

    @Test
    @DisplayName("parseia pagamentos 21+26+36 (Maria 2024)")
    void parsePagamentosMaria2024() {
        String json = """
                {
                  "pagamentos": [
                    {
                      "codigo": "21",
                      "nomeBeneficiario": "LONGEVIDADE CLINICA DA SAUDE LTDA.",
                      "cpfCnpj": "18.101.092/0001-61",
                      "valorPago": 350.00,
                      "parcNaoDedutivel": 0.00
                    },
                    {
                      "codigo": "26",
                      "nomeBeneficiario": "CAIXA ECONOMICA FEDERAL",
                      "cpfCnpj": "00.360.305/0001-04",
                      "valorPago": 7349.58,
                      "parcNaoDedutivel": 0.00
                    },
                    {
                      "codigo": "36",
                      "nomeBeneficiario": "FUNDACAO DOS ECONOMIARIOS FEDERAIS FUNCEF",
                      "cpfCnpj": "00.436.923/0001-90",
                      "valorPago": 914.52,
                      "parcNaoDedutivel": 0.00
                    }
                  ]
                }
                """;

        List<PagamentoEfetuado> pagamentos = GeminiResponseParser.parsePagamentosResponse(json);
        assertEquals(3, pagamentos.size());

        List<PagamentoEfetuadoIrpf> irpf = pagamentos.stream()
                .map(p -> PagamentoEfetuadoIrpf.builder()
                        .codigo(p.getCodigo())
                        .nomeBeneficiario(p.getNomeBeneficiario())
                        .cpfCnpj(p.getCpfCnpj())
                        .valorPago(p.getValorPago())
                        .parcNaoDedutivel(p.getParcNaoDedutivel())
                        .build())
                .toList();

        DeducoesPagamentosDTO agg = new IrPagamentosDeducaoAggregator()
                .aggregate(irpf, IrpfDeclaracaoData.builder().build());

        assertTrue(agg.isFonteGranular());
        assertEquals(0, new BigDecimal("7699.58").compareTo(agg.getDespesasMedicas()));
        assertEquals(0, new BigDecimal("914.52").compareTo(agg.getPrevidenciaPrivada()));
    }

    @Test
    @DisplayName("parseia dependentes + total (Maria 2016)")
    void parseDependentesMaria2016() {
        String json = """
                {
                  "dependentes": [
                    {
                      "codigo": "22",
                      "nome": "BERNARDO BADDINI CALDARARO",
                      "dataNascimento": "19/02/1994",
                      "cpf": "125.715.767-12"
                    },
                    {
                      "codigo": "22",
                      "nome": "BRUNO BADDINI CALDARARO",
                      "dataNascimento": "17/03/1995",
                      "cpf": "125.715.777-94"
                    }
                  ],
                  "totalDeducaoDependentes": 4550.16
                }
                """;

        var result = GeminiResponseParser.parseDependentesResponse(json);
        assertEquals(2, result.dependentes().size());
        assertEquals(0, new BigDecimal("4550.16").compareTo(result.totalDeducao()));
        assertEquals("BERNARDO BADDINI CALDARARO", result.dependentes().get(0).getNome());
    }

    @Test
    @DisplayName("withPagamentos preserva demais campos")
    void withPagamentosPreservaCampos() {
        var base = GeminiResponseParser.parseIncomeTaxResponse("""
                {
                  "exercicio": "2025",
                  "anoCalendario": "2024",
                  "nome": "MARIA",
                  "cpf": "596.545.387-68",
                  "modeloDeclaracao": "SIMPLIFICADA",
                  "rendimentosTributaveis": 100000.00,
                  "descontoSimplificado": 20000.00
                }
                """);

        var pagamentos = GeminiResponseParser.parsePagamentosResponse("""
                {"pagamentos":[{"codigo":"26","nomeBeneficiario":"CAIXA","cpfCnpj":"00.360.305/0001-04","valorPago":100.00,"parcNaoDedutivel":0}]}
                """);

        var merged = IncomeTaxGeminiHelper.withPagamentos(base, pagamentos);
        assertEquals(1, merged.getPagamentosEfetuados().size());
        assertEquals("MARIA", merged.getNome());
        assertEquals("SIMPLIFICADO", merged.getTipoTributacao());
    }
}
