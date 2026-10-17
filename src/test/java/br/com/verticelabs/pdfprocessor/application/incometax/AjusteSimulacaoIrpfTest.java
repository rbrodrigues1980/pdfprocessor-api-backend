package br.com.verticelabs.pdfprocessor.application.incometax;

import br.com.verticelabs.pdfprocessor.application.tributacao.IrCalculoProgressivoService;
import br.com.verticelabs.pdfprocessor.application.tributacao.IrDoacoesDeducaoCalculator;
import br.com.verticelabs.pdfprocessor.application.tributacao.IrPagamentosDeducaoAggregator;
import br.com.verticelabs.pdfprocessor.application.tributacao.IrSimuladorMotorService;
import br.com.verticelabs.pdfprocessor.domain.model.IrpfDeclaracaoData;
import br.com.verticelabs.pdfprocessor.infrastructure.excel.ExcelIrpfDeducoesResumoHelper;
import br.com.verticelabs.pdfprocessor.infrastructure.excel.ExcelIrpfSimulacaoMapper;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AjusteSimulacaoIrpfTest {

    @Test
    void semAjusteDevolveAMesmaDeclaracao() {
        IrpfDeclaracaoData data = declaracao();
        assertSame(data, AjusteSimulacaoIrpf.paraSimulacao(data));
        assertNull(AjusteSimulacaoIrpf.impostoDepositoJudicialCivel(data));
    }

    @Test
    void rendimentosPjTitularSubstituiERecalculaTotalELimiteDe12PorCento() {
        IrpfDeclaracaoData data = declaracao();
        data.setAjusteSimulacaoRendimentosPjTitular(new BigDecimal("200.00"));

        IrpfDeclaracaoData simulacao = AjusteSimulacaoIrpf.paraSimulacao(data);

        assertEquals(new BigDecimal("100.00"), data.getRendimentosTributaveisTitularPJ());
        assertEquals(new BigDecimal("150.00"), data.getRendimentosTributaveisTotal());
        assertEquals(new BigDecimal("200.00"), simulacao.getRendimentosTributaveisTitularPJ());
        assertEquals(new BigDecimal("250.00"), simulacao.getRendimentosTributaveisTotal());

        ExcelIrpfDeducoesResumoHelper helper = new ExcelIrpfDeducoesResumoHelper(
                new IrSimuladorMotorService(new IrCalculoProgressivoService(), new IrDoacoesDeducaoCalculator()),
                new IrPagamentosDeducaoAggregator());
        ExcelIrpfSimulacaoMapper mapper = new ExcelIrpfSimulacaoMapper(new IrPagamentosDeducaoAggregator());
        var deducoes = helper.montar(
                simulacao,
                mapper.fromDeclaracao(simulacao, BigDecimal.ZERO, true),
                BigDecimal.ZERO,
                null);
        assertEquals(new BigDecimal("30.00"), deducoes.getLimite12PctRendimentos());
        assertEquals(new BigDecimal("250.00"),
                mapper.fromDeclaracao(simulacao, BigDecimal.ZERO, true).getRendimentosTributaveis());
    }

    @Test
    void impostoDepositoJudicialSomaNoTotalPago() {
        IrpfDeclaracaoData data = declaracao();
        data.setImpostoPagoTotal(new BigDecimal("18127.98"));
        data.setAjusteSimulacaoImpostoDepositoJudicialCivel(new BigDecimal("6423.85"));

        assertEquals(new BigDecimal("24551.83"),
                AjusteSimulacaoIrpf.somarImpostoDepositoJudicialCivel(data.getImpostoPagoTotal(), data));
    }

    @Test
    void deducoesManuaisSubstituemAsTresLinhasEEntramNoCalculo() {
        IrpfDeclaracaoData data = declaracao();
        data.setContribuicaoPrevidenciaOficialResumo(new BigDecimal("1000.00"));
        data.setContribuicaoPrevidenciaPrivada(new BigDecimal("999.00"));
        data.setAjusteSimulacaoPrevOficialPublica(new BigDecimal("100.00"));
        data.setAjusteSimulacaoPrevOficialRra(new BigDecimal("40.00"));
        data.setAjusteSimulacaoPrevComplementar(new BigDecimal("25.00"));

        ExcelIrpfDeducoesResumoHelper helper = new ExcelIrpfDeducoesResumoHelper(
                new IrSimuladorMotorService(new IrCalculoProgressivoService(), new IrDoacoesDeducaoCalculator()),
                new IrPagamentosDeducaoAggregator());
        ExcelIrpfSimulacaoMapper mapper = new ExcelIrpfSimulacaoMapper(new IrPagamentosDeducaoAggregator());
        var request = mapper.fromDeclaracao(data, new BigDecimal("16810.01"), true);
        var deducoes = helper.montar(data, request, new BigDecimal("16810.01"), null);

        assertEquals(new BigDecimal("1000.00"), data.getContribuicaoPrevidenciaOficialResumo());
        assertEquals(new BigDecimal("100.00"), deducoes.getPrevOficialPublica());
        assertEquals(new BigDecimal("40.00"), deducoes.getPrevOficialRra());
        assertEquals(new BigDecimal("25.00"), deducoes.getPrevComplementarPrivadaEfetiva());
        assertEquals(new BigDecimal("165.00"), deducoes.getTotalDeducoes());
        assertEquals(new BigDecimal("140.00"), request.getPrevidenciaOficial());
        assertEquals(new BigDecimal("25.00"), request.getPrevidenciaPrivada());
        assertTrue(request.getPrevidenciaPrivadaSemTeto());
    }

    @Test
    void preservarCopiaOsCamposQuandoAExtracaoSubstituiADeclaracao() {
        IrpfDeclaracaoData anterior = declaracao();
        anterior.setAjusteSimulacaoRendimentosPjTitular(new BigDecimal("10.00"));
        anterior.setAjusteSimulacaoImpostoDepositoJudicialCivel(new BigDecimal("20.00"));
        anterior.setAjusteSimulacaoPrevOficialPublica(new BigDecimal("30.00"));
        anterior.setAjusteSimulacaoPrevOficialRra(new BigDecimal("40.00"));
        anterior.setAjusteSimulacaoPrevComplementar(new BigDecimal("50.00"));
        IrpfDeclaracaoData novo = IrpfDeclaracaoData.builder()
                .rendimentosTributaveisTitularPJ(new BigDecimal("1.00"))
                .build();

        AjusteSimulacaoIrpf.preservar(anterior, novo);

        assertEquals(new BigDecimal("10.00"), novo.getAjusteSimulacaoRendimentosPjTitular());
        assertEquals(new BigDecimal("20.00"), novo.getAjusteSimulacaoImpostoDepositoJudicialCivel());
        assertEquals(new BigDecimal("30.00"), novo.getAjusteSimulacaoPrevOficialPublica());
        assertEquals(new BigDecimal("40.00"), novo.getAjusteSimulacaoPrevOficialRra());
        assertEquals(new BigDecimal("50.00"), novo.getAjusteSimulacaoPrevComplementar());
        assertEquals(new BigDecimal("1.00"), novo.getRendimentosTributaveisTitularPJ());
    }

    private static IrpfDeclaracaoData declaracao() {
        return IrpfDeclaracaoData.builder()
                .anoCalendario("2019")
                .exercicio("2020")
                .tipoTributacao("COMPLETO")
                .rendimentosTributaveisTitularPJ(new BigDecimal("100.00"))
                .rendimentosTributaveisDependentesPJ(new BigDecimal("50.00"))
                .rendimentosTributaveisTotal(new BigDecimal("150.00"))
                .impostoRetidoFonteTitular(new BigDecimal("10.00"))
                .impostoPagoTotal(new BigDecimal("10.00"))
                .build();
    }
}
