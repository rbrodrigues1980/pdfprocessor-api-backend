package br.com.verticelabs.pdfprocessor.infrastructure.excel;

import br.com.verticelabs.pdfprocessor.application.incometax.IrpfDeclaracaoDataMapper;
import br.com.verticelabs.pdfprocessor.application.tributacao.IrPagamentosDeducaoAggregator;
import br.com.verticelabs.pdfprocessor.application.tributacao.dto.SimuladorIrpfRequest;
import br.com.verticelabs.pdfprocessor.domain.model.IrpfDeclaracaoData;
import br.com.verticelabs.pdfprocessor.domain.model.IrpfDeclaracaoData.PagamentoEfetuadoIrpf;
import br.com.verticelabs.pdfprocessor.domain.service.IncomeTaxDeclarationService.IncomeTaxInfo;
import br.com.verticelabs.pdfprocessor.infrastructure.ai.GeminiResponseParser;
import br.com.verticelabs.pdfprocessor.infrastructure.incometax.IncomeTaxGeminiHelper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Cláudio AC 2016 — RESUMO Completa correto; pagamentos parciais não podem
 * reduzir médicas/prev. compl. na simulação.
 */
@DisplayName("Cláudio 2016 — RESUMO vs pagamentos parciais")
class Claudio2016ResumoVsPagamentosTest {

    @Test
    @DisplayName("médicas: RESUMO 9443,84 prevalece sobre só cód. 26 (8363,84)")
    void medicasPrefereResumoQuandoGranularParcial() {
        IrpfDeclaracaoData data = IrpfDeclaracaoData.builder()
                .anoCalendario("2016")
                .rendimentosTributaveisTotal(new BigDecimal("285807.50"))
                .despesasMedicas(new BigDecimal("9443.84"))
                .contribuicaoPrevidenciaPrivada(new BigDecimal("30283.45"))
                .pagamentosEfetuados(List.of(
                        PagamentoEfetuadoIrpf.builder()
                                .codigo("26")
                                .nomeBeneficiario("SAUDE CAIXA")
                                .cpfCnpj("00.360.305/0001-04")
                                .valorPago(new BigDecimal("8363.84"))
                                .parcNaoDedutivel(BigDecimal.ZERO)
                                .build()))
                .build();

        SimuladorIrpfRequest request = new ExcelIrpfSimulacaoMapper(new IrPagamentosDeducaoAggregator())
                .fromDeclaracao(data, BigDecimal.ZERO, false);

        assertEquals(0, new BigDecimal("9443.84").compareTo(request.getDespesasMedicas()));
        assertEquals(0, new BigDecimal("30283.45").compareTo(request.getPrevidenciaPrivada()));
    }

    @Test
    @DisplayName("médicas: RESUMO vazio + pagamentos 26+10+22 preenche 9443,84 no mapper")
    void medicasPreencheResumoVazioPelosPagamentos() {
        IncomeTaxInfo base = GeminiResponseParser.parseIncomeTaxResponse("""
                {
                  "exercicio": "2017",
                  "anoCalendario": "2016",
                  "nome": "CLAUDIO",
                  "cpf": "379.391.906-44",
                  "modeloDeclaracao": "COMPLETA",
                  "rendimentosTributaveis": 285807.50,
                  "deducoes": {
                    "previdenciaOficial": 6850.56,
                    "previdenciaComplementar": null,
                    "despesasMedicas": null,
                    "dependentes": 4550.16
                  }
                }
                """);

        var pagamentos = GeminiResponseParser.parsePagamentosResponse("""
                {
                  "pagamentos": [
                    {"codigo":"36","nomeBeneficiario":"FUNCEF","cpfCnpj":"00.436.923/0001-90",
                     "valorPago":30283.45,"parcNaoDedutivel":0},
                    {"codigo":"26","nomeBeneficiario":"SAUDE CAIXA","cpfCnpj":"00.360.305/0001-04",
                     "valorPago":8363.84,"parcNaoDedutivel":0},
                    {"codigo":"10","nomeBeneficiario":"ELAINE C BORGES","cpfCnpj":"277.897.908-54",
                     "valorPago":180.00,"parcNaoDedutivel":0},
                    {"codigo":"22","nomeBeneficiario":"CLINICA ODONTOLOGICA","cpfCnpj":"00.000.000/0001-00",
                     "valorPago":900.00,"parcNaoDedutivel":0}
                  ]
                }
                """);

        IncomeTaxInfo withPag = IncomeTaxGeminiHelper.withPagamentos(base, pagamentos);
        IrpfDeclaracaoData data = new IrpfDeclaracaoDataMapper().fromIncomeTaxInfo(withPag);

        assertEquals(0, new BigDecimal("9443.84").compareTo(data.getDespesasMedicas()));
        assertEquals(0, new BigDecimal("30283.45").compareTo(data.getContribuicaoPrevidenciaPrivada()));

        SimuladorIrpfRequest request = new ExcelIrpfSimulacaoMapper(new IrPagamentosDeducaoAggregator())
                .fromDeclaracao(data, BigDecimal.ZERO, false);
        assertEquals(0, new BigDecimal("9443.84").compareTo(request.getDespesasMedicas()));
        assertEquals(0, new BigDecimal("30283.45").compareTo(request.getPrevidenciaPrivada()));
    }

    @Test
    @DisplayName("preferirMaiorPositivo escolhe RESUMO quando maior")
    void preferirMaiorPositivo() {
        assertEquals(0, new BigDecimal("9443.84").compareTo(
                ExcelIrpfSimulacaoMapper.preferirMaiorPositivo(
                        new BigDecimal("9443.84"), new BigDecimal("8363.84"))));
        assertEquals(0, new BigDecimal("8363.84").compareTo(
                ExcelIrpfSimulacaoMapper.preferirMaiorPositivo(
                        null, new BigDecimal("8363.84"))));
    }
}
