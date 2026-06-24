package br.com.verticelabs.pdfprocessor.infrastructure.excel;

import br.com.verticelabs.pdfprocessor.application.tributacao.IrPagamentosDeducaoAggregator;
import br.com.verticelabs.pdfprocessor.domain.model.IrpfDeclaracaoData;
import br.com.verticelabs.pdfprocessor.domain.model.IrpfDeclaracaoData.PagamentoEfetuadoIrpf;
import br.com.verticelabs.pdfprocessor.interfaces.consolidation.dto.ConsolidatedResponse;
import br.com.verticelabs.pdfprocessor.interfaces.consolidation.dto.ConsolidationRow;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PrevComplPlanilhaHelperTest {

    private static final BigDecimal PLANILHA_MARGARIDA = new BigDecimal("22238.28");
    private static final BigDecimal EXTRA_CAIXA_VIDA = new BigDecimal("1724.48");
    private static final BigDecimal TOTAL_MARGARIDA = new BigDecimal("23962.76");

    @Test
    void margarida2016_somaExtraCaixaVida_ignoraFuncef() {
        IrpfDeclaracaoData data = margaridaAc2016Pagamentos();

        assertEquals(EXTRA_CAIXA_VIDA, PrevComplPlanilhaHelper.somarPagamentosPrevidenciaExternos(data));

        ConsolidatedResponse consolidated = consolidatedComTotalAno("2016", PLANILHA_MARGARIDA);
        BigDecimal total = PrevComplPlanilhaHelper.calcularPrevComplSimulacao(consolidated, "2016", data);

        assertEquals(TOTAL_MARGARIDA, total);
    }

    @Test
    void codigo37_cnpjIgnorado_naoSoma() {
        IrpfDeclaracaoData data = IrpfDeclaracaoData.builder()
                .pagamentosEfetuados(List.of(
                        PagamentoEfetuadoIrpf.builder()
                                .codigo("37")
                                .cpfCnpj("00.436.923/0001-90")
                                .valorPago(new BigDecimal("5000.00"))
                                .build()))
                .build();

        assertEquals(BigDecimal.ZERO.setScale(2), PrevComplPlanilhaHelper.somarPagamentosPrevidenciaExternos(data));
    }

    @Test
    void codigo37_cnpjExterno_soma() {
        IrpfDeclaracaoData data = IrpfDeclaracaoData.builder()
                .pagamentosEfetuados(List.of(
                        PagamentoEfetuadoIrpf.builder()
                                .codigo("37")
                                .cpfCnpj("12.345.678/0001-99")
                                .valorPago(new BigDecimal("1500.00"))
                                .build()))
                .build();

        assertEquals(new BigDecimal("1500.00"), PrevComplPlanilhaHelper.somarPagamentosPrevidenciaExternos(data));
    }

    @Test
    void semDeclaracao_apenasContracheques() {
        ConsolidatedResponse consolidated = consolidatedComTotalAno("2016", PLANILHA_MARGARIDA);

        assertEquals(
                PLANILHA_MARGARIDA,
                PrevComplPlanilhaHelper.calcularPrevComplSimulacao(consolidated, "2016", null));
    }

    @Test
    void parcNaoDedutivel_reduzValorSomado() {
        IrpfDeclaracaoData data = IrpfDeclaracaoData.builder()
                .pagamentosEfetuados(List.of(
                        PagamentoEfetuadoIrpf.builder()
                                .codigo("36")
                                .cpfCnpj("03.730.204/0001-76")
                                .valorPago(new BigDecimal("2000.00"))
                                .parcNaoDedutivel(new BigDecimal("200.00"))
                                .build()))
                .build();

        assertEquals(new BigDecimal("1800.00"), PrevComplPlanilhaHelper.somarPagamentosPrevidenciaExternos(data));
    }

    @Test
    void sim2_elizete_planilhaSemIncremento_quandoSoCnpjFuncef() {
        IrpfDeclaracaoData data = IrpfDeclaracaoData.builder()
                .pagamentosEfetuados(List.of(
                        PagamentoEfetuadoIrpf.builder()
                                .codigo("36")
                                .cpfCnpj("00.436.923/0001-90")
                                .valorPago(new BigDecimal("3586.68"))
                                .build()))
                .build();

        BigDecimal prevPlanilha = new BigDecimal("5586.90");
        ConsolidatedResponse consolidated = consolidatedComTotalAno("2016", prevPlanilha);

        assertEquals(
                prevPlanilha,
                PrevComplPlanilhaHelper.calcularPrevComplSimulacao(consolidated, "2016", data));

        ExcelIrpfSimulacaoMapper mapper = new ExcelIrpfSimulacaoMapper(new IrPagamentosDeducaoAggregator());
        assertEquals(
                prevPlanilha,
                mapper.fromDeclaracao(data, prevPlanilha, true).getPrevidenciaPrivada());
    }

    @Test
    void isCnpjIgnorado_normalizaFormatacao() {
        assertTrue(PrevComplPlanilhaHelper.isCnpjIgnorado("00.436.923/0001-90"));
        assertTrue(PrevComplPlanilhaHelper.isCnpjIgnorado("00.360.305/0001-04"));
        assertFalse(PrevComplPlanilhaHelper.isCnpjIgnorado("03.730.204/0001-76"));
    }

    private static IrpfDeclaracaoData margaridaAc2016Pagamentos() {
        List<PagamentoEfetuadoIrpf> pagamentos = new ArrayList<>();
        pagamentos.add(PagamentoEfetuadoIrpf.builder()
                .codigo("36")
                .nomeBeneficiario("FUNDACAO DOS ECONOMIARIOS FEDERAIS")
                .cpfCnpj("00.436.923/0001-90")
                .valorPago(new BigDecimal("21183.99"))
                .build());
        pagamentos.add(PagamentoEfetuadoIrpf.builder()
                .codigo("36")
                .nomeBeneficiario("CAIXA E VIDA PREVIDENCIA")
                .cpfCnpj("03.730.204/0001-76")
                .valorPago(EXTRA_CAIXA_VIDA)
                .build());

        return IrpfDeclaracaoData.builder()
                .anoCalendario("2016")
                .exercicio("2017")
                .pagamentosEfetuados(pagamentos)
                .build();
    }

    private static ConsolidatedResponse consolidatedComTotalAno(String ano, BigDecimal totalAno) {
        Map<String, BigDecimal> valores = new HashMap<>();
        valores.put(ano + "-01", totalAno);
        return ConsolidatedResponse.builder()
                .rubricas(List.of(ConsolidationRow.builder()
                        .codigo("4482")
                        .valores(valores)
                        .build()))
                .build();
    }
}
