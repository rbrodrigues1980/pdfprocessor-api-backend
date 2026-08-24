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
        assertTrue(PrevComplPlanilhaHelper.isCnpjIgnorado("65.471.914/0001-86"));
        assertFalse(PrevComplPlanilhaHelper.isCnpjIgnorado("03.730.204/0001-76"));
    }

    /**
     * Antônio AC 2020: Simulação 2 não pode somar declaração (15.205,21) + planilha (14.620,50).
     */
    @Test
    void antonio2020_ignoraCnpjSabesprev_naSimulacao2() {
        BigDecimal planilha = new BigDecimal("14620.50");
        Map<String, BigDecimal> valores3347 = new HashMap<>();
        valores3347.put("2020-01", planilha);

        ConsolidatedResponse consolidated = ConsolidatedResponse.builder()
                .origem("SABESP")
                .rubricas(List.of(
                        ConsolidationRow.builder().codigo("3347").valores(valores3347).build()))
                .build();

        IrpfDeclaracaoData declaracao = IrpfDeclaracaoData.builder()
                .pagamentosEfetuados(List.of(
                        PagamentoEfetuadoIrpf.builder()
                                .codigo("36")
                                .nomeBeneficiario("FUND. SABESP DE SEGURIDADE SOCIAL")
                                .cpfCnpj("65.471.914/0001-86")
                                .valorPago(new BigDecimal("15205.21"))
                                .build(),
                        PagamentoEfetuadoIrpf.builder()
                                .codigo("26")
                                .nomeBeneficiario("FUNDACAO CESP")
                                .cpfCnpj("62.465.117/0001-06")
                                .valorPago(new BigDecimal("9943.05"))
                                .build()))
                .build();

        assertEquals(BigDecimal.ZERO.setScale(2),
                PrevComplPlanilhaHelper.somarPagamentosPrevidenciaExternos(declaracao));

        assertEquals(
                planilha,
                PrevComplPlanilhaHelper.calcularPrevComplSimulacao(consolidated, "2020", declaracao));

        // Soma bruta errada que o bug produzia
        assertEquals(new BigDecimal("29825.71"),
                planilha.add(new BigDecimal("15205.21")));
    }

    @Test
    void sabesp_planilhaMaisExtraCodigo38_outroCnpj() {
        BigDecimal planilha = new BigDecimal("14620.50");
        Map<String, BigDecimal> valores3347 = new HashMap<>();
        valores3347.put("2020-01", planilha);

        ConsolidatedResponse consolidated = ConsolidatedResponse.builder()
                .origem("SABESP")
                .rubricas(List.of(
                        ConsolidationRow.builder().codigo("3347").valores(valores3347).build()))
                .build();

        IrpfDeclaracaoData declaracao = IrpfDeclaracaoData.builder()
                .pagamentosEfetuados(List.of(
                        PagamentoEfetuadoIrpf.builder()
                                .codigo("36")
                                .cpfCnpj("65.471.914/0001-86")
                                .valorPago(new BigDecimal("15205.21"))
                                .build(),
                        PagamentoEfetuadoIrpf.builder()
                                .codigo("38")
                                .cpfCnpj("12.345.678/0001-99")
                                .valorPago(new BigDecimal("2500.00"))
                                .build()))
                .build();

        assertEquals(
                planilha.add(new BigDecimal("2500.00")),
                PrevComplPlanilhaHelper.calcularPrevComplSimulacao(consolidated, "2020", declaracao));
    }

    @Test
    void sabesp_planilhaMaisExtraCodigo36_outroCnpj() {
        BigDecimal planilha = new BigDecimal("14620.50");
        Map<String, BigDecimal> valores3347 = new HashMap<>();
        valores3347.put("2020-01", planilha);

        ConsolidatedResponse consolidated = ConsolidatedResponse.builder()
                .origem("SABESP")
                .rubricas(List.of(
                        ConsolidationRow.builder().codigo("3347").valores(valores3347).build()))
                .build();

        IrpfDeclaracaoData declaracao = IrpfDeclaracaoData.builder()
                .pagamentosEfetuados(List.of(
                        PagamentoEfetuadoIrpf.builder()
                                .codigo("36")
                                .cpfCnpj("65.471.914/0001-86")
                                .valorPago(new BigDecimal("15205.21"))
                                .build(),
                        PagamentoEfetuadoIrpf.builder()
                                .codigo("36")
                                .cpfCnpj("03.730.204/0001-76")
                                .valorPago(new BigDecimal("1724.48"))
                                .build()))
                .build();

        assertEquals(
                planilha.add(new BigDecimal("1724.48")),
                PrevComplPlanilhaHelper.calcularPrevComplSimulacao(consolidated, "2020", declaracao));
    }

    @Test
    void sabesp3347e3349_entramNoPrevComplDaSimulacao() {
        Map<String, BigDecimal> valores3347 = new HashMap<>();
        valores3347.put("2020-01", new BigDecimal("700.39"));
        Map<String, BigDecimal> valores3349 = new HashMap<>();
        valores3349.put("2020-01", new BigDecimal("58.36"));

        ConsolidatedResponse consolidated = ConsolidatedResponse.builder()
                .origem("SABESP")
                .rubricas(List.of(
                        ConsolidationRow.builder().codigo("3347").valores(valores3347).build(),
                        ConsolidationRow.builder().codigo("3349").valores(valores3349).build()))
                .build();

        assertEquals(
                new BigDecimal("758.75"),
                PrevComplPlanilhaHelper.calcularPrevComplSimulacao(consolidated, "2020", null));
    }

    /**
     * Heitor 2017: soma bruta das rubricas = CONTRIBUIÇÃO+DEVOLUÇÃO = 3763,05;
     * prevCompl da simulação deve usar TOTAL líquido do rodapé = 3637,19.
     */
    @Test
    void heitor2017_sabesprevUsaTotalLiquidoRodape_naoSomaBruta() {
        Map<String, BigDecimal> valores7400 = new HashMap<>();
        valores7400.put("2017-01", new BigDecimal("3700.12"));
        Map<String, BigDecimal> valores7404 = new HashMap<>();
        valores7404.put("2017-07", new BigDecimal("62.93"));

        ConsolidatedResponse consolidated = ConsolidatedResponse.builder()
                .origem("SABESPREV")
                .rubricas(List.of(
                        ConsolidationRow.builder().codigo("7400").valores(valores7400).build(),
                        ConsolidationRow.builder().codigo("7404").valores(valores7404).build()))
                .build();

        // Soma bruta (caminho APCEF) seria 3763.05 — não usar
        assertEquals(
                new BigDecimal("3763.05"),
                ConsolidationAnoTotalsHelper.calcularTotalContracheques(consolidated, "2017")
                        .setScale(2));

        assertEquals(
                new BigDecimal("3637.19"),
                PrevComplPlanilhaHelper.calcularPrevComplSimulacao(consolidated, "2017", null));
    }

    @Test
    void heitor2017_detectaFichaPorCodigosMesmoComOrigemMista() {
        Map<String, BigDecimal> valores7400 = new HashMap<>();
        valores7400.put("2017-01", new BigDecimal("3700.12"));
        Map<String, BigDecimal> valores7404 = new HashMap<>();
        valores7404.put("2017-07", new BigDecimal("62.93"));

        ConsolidatedResponse consolidated = ConsolidatedResponse.builder()
                .origem(null) // misto / indefinido
                .rubricas(List.of(
                        ConsolidationRow.builder().codigo("7400").valores(valores7400).build(),
                        ConsolidationRow.builder().codigo("7404").valores(valores7404).build()))
                .build();

        assertEquals(
                new BigDecimal("3637.19"),
                PrevComplPlanilhaHelper.calcularPrevComplSimulacao(consolidated, "2017", null));
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
