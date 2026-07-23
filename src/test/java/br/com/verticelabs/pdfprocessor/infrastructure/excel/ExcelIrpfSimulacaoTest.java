package br.com.verticelabs.pdfprocessor.infrastructure.excel;

import br.com.verticelabs.pdfprocessor.application.tributacao.IrCalculoProgressivoService;
import br.com.verticelabs.pdfprocessor.application.tributacao.IrDoacoesDeducaoCalculator;
import br.com.verticelabs.pdfprocessor.application.tributacao.IrPagamentosDeducaoAggregator;
import br.com.verticelabs.pdfprocessor.application.tributacao.IrSimuladorMotorService;
import br.com.verticelabs.pdfprocessor.application.tributacao.dto.SimuladorIrpfRequest;
import br.com.verticelabs.pdfprocessor.application.tributacao.dto.SimuladorIrpfResponse;
import br.com.verticelabs.pdfprocessor.domain.model.IrParametrosAnuais;
import br.com.verticelabs.pdfprocessor.domain.model.IrTabelaTributacao;
import br.com.verticelabs.pdfprocessor.domain.model.IrpfDeclaracaoData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Regressão Elizabeth AC 2016 / Ex. 2017 — dupla simulação Excel via motor unificado.
 */
class ExcelIrpfSimulacaoTest {

    private IrSimuladorMotorService motor;
    private ExcelIrpfSimulacaoMapper mapper;
    private List<IrTabelaTributacao> faixas2016;
    private IrParametrosAnuais params2016;

    @BeforeEach
    void setUp() {
        motor = new IrSimuladorMotorService(new IrCalculoProgressivoService(), new IrDoacoesDeducaoCalculator());
        mapper = new ExcelIrpfSimulacaoMapper(new IrPagamentosDeducaoAggregator());
        faixas2016 = criarFaixas2016();
        params2016 = IrParametrosAnuais.builder()
                .deducaoDependente(new BigDecimal("2275.08"))
                .limiteInstrucao(new BigDecimal("3561.50"))
                .limiteDescontoSimplificado(new BigDecimal("16754.34"))
                .build();
    }

    @Test
    void elizabeth_ac2016_deducoesDeclaracao_total51757() {
        IrpfDeclaracaoData data = elizabethAc2016Detalhada();
        SimuladorIrpfRequest req = mapper.fromDeclaracao(data, new BigDecimal("13426.14"));
        SimuladorIrpfResponse resp = motor.simular(req, faixas2016, params2016);

        assertEquals(new BigDecimal("51757.34"), resp.getModeloCompleto().getTotalDeducoes());
        assertEquals(new BigDecimal("99968.27"), resp.getModeloCompleto().getBaseCalculo());
        assertEquals(new BigDecimal("17058.95"), resp.getModeloCompleto().getImpostoDevidoFinal());
    }

    @Test
    void elizabeth_ac2016_sim1_prevDeclarada() {
        IrpfDeclaracaoData data = elizabethAc2016();
        BigDecimal prevDeclarada = new BigDecimal("13426.14");

        SimuladorIrpfRequest req = mapper.fromDeclaracao(data, prevDeclarada);
        SimuladorIrpfResponse resp = motor.simular(req, faixas2016, params2016);

        assertEquals(new BigDecimal("99968.27"), resp.getModeloCompleto().getBaseCalculo());
        assertEquals(new BigDecimal("17058.95"), resp.getModeloCompleto().getImpostoDevidoFinal());
        assertEquals(new BigDecimal("134971.27"), resp.getModeloSimplificado().getBaseCalculo());
        assertTrue(resp.getModeloSimplificado().getImpostoDevidoFinal()
                .compareTo(resp.getModeloCompleto().getImpostoDevidoFinal()) > 0);
    }

    @Test
    void elizabeth_ac2016_sim2_prevPlanilhaContracheques() {
        IrpfDeclaracaoData data = elizabethAc2016();
        BigDecimal prevPlanilha = new BigDecimal("14142.43");

        SimuladorIrpfRequest req = mapper.fromDeclaracao(data, prevPlanilha, true);
        SimuladorIrpfResponse resp = motor.simular(req, faixas2016, params2016);

        assertEquals(new BigDecimal("99251.98"), resp.getModeloCompleto().getBaseCalculo());
        assertEquals(new BigDecimal("16861.97"), resp.getModeloCompleto().getImpostoDevidoFinal());
    }

    @Test
    void elizabeth_ac2016_sim1_naoUsaPrevPlanilhaQuandoDeclaracaoTemValor() {
        IrpfDeclaracaoData data = elizabethAc2016();
        BigDecimal prevPlanilha = new BigDecimal("14142.43");

        SimuladorIrpfRequest req = mapper.fromDeclaracao(data, prevPlanilha, false);

        assertEquals(new BigDecimal("13426.14"), req.getPrevidenciaPrivada());
    }

    @Test
    void deducaoDependentesNull_motorUsaQtdVezesParametro() {
        IrpfDeclaracaoData data = IrpfDeclaracaoData.builder()
                .anoCalendario("2016")
                .rendimentosTributaveisTotal(new BigDecimal("100000.00"))
                .contribuicaoPrevidenciaSocial(new BigDecimal("5000.00"))
                .dependentes(List.of(
                        IrpfDeclaracaoData.PessoaRelacionada.builder().nome("A").cpf("1").build(),
                        IrpfDeclaracaoData.PessoaRelacionada.builder().nome("B").cpf("2").build()))
                .deducaoDependentes(null)
                .build();

        SimuladorIrpfRequest req = mapper.fromDeclaracao(data, BigDecimal.ZERO);
        assertEquals(2, req.getQtdDependentes());
        assertEquals(null, req.getDeducaoDependentesDeclarada());

        SimuladorIrpfResponse resp = motor.simular(req, faixas2016, params2016);
        // 5000 prev + 2×2275.08 dependentes = 9550.16
        assertEquals(0, new BigDecimal("9550.16").compareTo(resp.getModeloCompleto().getTotalDeducoes()));
    }

    @Test
    void simplificadoComPagamentosMedicos_completaUsaAgregador() {
        IrpfDeclaracaoData data = IrpfDeclaracaoData.builder()
                .anoCalendario("2024")
                .tipoTributacao("SIMPLIFICADO")
                .rendimentosTributaveisTotal(new BigDecimal("100000.00"))
                .despesasMedicas(null)
                .pagamentosEfetuados(List.of(
                        IrpfDeclaracaoData.PagamentoEfetuadoIrpf.builder()
                                .codigo("21").nomeBeneficiario("CLINICA")
                                .cpfCnpj("18.101.092/0001-61")
                                .valorPago(new BigDecimal("350.00"))
                                .parcNaoDedutivel(BigDecimal.ZERO)
                                .build(),
                        IrpfDeclaracaoData.PagamentoEfetuadoIrpf.builder()
                                .codigo("26").nomeBeneficiario("CAIXA")
                                .cpfCnpj("00.360.305/0001-04")
                                .valorPago(new BigDecimal("7349.58"))
                                .parcNaoDedutivel(BigDecimal.ZERO)
                                .build()))
                .build();

        SimuladorIrpfRequest req = mapper.fromDeclaracao(data, BigDecimal.ZERO);
        assertEquals(0, new BigDecimal("7699.58").compareTo(req.getDespesasMedicas()));
    }

    @Test
    void nadelson_impostoDevidoRRAEntraNoTotalDaSimulacao() {
        IrpfDeclaracaoData data = IrpfDeclaracaoData.builder()
                .anoCalendario("2016")
                .rendimentosTributaveisTotal(new BigDecimal("227615.37"))
                .contribuicaoPrevidenciaSocial(new BigDecimal("10000.00"))
                .impostoSobreRRA(new BigDecimal("28033.36"))
                .build();

        SimuladorIrpfRequest req = mapper.fromDeclaracao(data, BigDecimal.ZERO);
        assertEquals(0, new BigDecimal("28033.36").compareTo(req.getImpostoDevidoRRA()));

        SimuladorIrpfResponse resp = motor.simular(req, faixas2016, params2016);
        BigDecimal impostoProgressivo = resp.getModeloCompleto().getImpostoDevidoFinal();
        BigDecimal totalDevido = resp.getModeloCompleto().getResumo().getTotalImpostoDevido();
        assertEquals(0, impostoProgressivo.add(new BigDecimal("28033.36")).compareTo(totalDevido));
    }

    private IrpfDeclaracaoData elizabethAc2016Detalhada() {
        return IrpfDeclaracaoData.builder()
                .exercicio("2017")
                .anoCalendario("2016")
                .tipoTributacao("COMPLETO")
                .tipoDeclaracao("Original")
                .rendimentosTributaveisTotal(new BigDecimal("151725.61"))
                .contribuicaoPrevidenciaSocial(new BigDecimal("8012.16"))
                .deducaoDependentes(new BigDecimal("6825.24"))
                .despesasInstrucao(new BigDecimal("7123.00"))
                .despesasMedicas(new BigDecimal("16370.80"))
                .contribuicaoPrevidenciaPrivada(new BigDecimal("13426.14"))
                .deducoesTotal(new BigDecimal("51757.34"))
                .build();
    }

    private IrpfDeclaracaoData elizabethAc2016() {
        return IrpfDeclaracaoData.builder()
                .exercicio("2017")
                .anoCalendario("2016")
                .tipoTributacao("COMPLETO")
                .tipoDeclaracao("Original")
                .rendimentosTributaveisTotal(new BigDecimal("151725.61"))
                .contribuicaoPrevidenciaSocial(new BigDecimal("38331.20"))
                .contribuicaoPrevidenciaPrivada(new BigDecimal("13426.14"))
                .build();
    }

    private List<IrTabelaTributacao> criarFaixas2016() {
        List<IrTabelaTributacao> faixas = new ArrayList<>();
        faixas.add(faixa(2016, 1, "0", "22847.76", "0", "0", "Isento"));
        faixas.add(faixa(2016, 2, "22847.77", "33919.80", "0.075", "1713.58", "7,5%"));
        faixas.add(faixa(2016, 3, "33919.81", "45012.60", "0.15", "4257.57", "15%"));
        faixas.add(faixa(2016, 4, "45012.61", "55976.16", "0.225", "7633.51", "22,5%"));
        faixas.add(faixa(2016, 5, "55976.17", null, "0.275", "10432.32", "27,5%"));
        return faixas;
    }

    private IrTabelaTributacao faixa(int ano, int n, String inf, String sup, String aliq, String ded, String desc) {
        return IrTabelaTributacao.builder()
                .anoCalendario(ano)
                .tipoIncidencia("ANUAL")
                .faixa(n)
                .limiteInferior(new BigDecimal(inf))
                .limiteSuperior(sup != null ? new BigDecimal(sup) : null)
                .aliquota(new BigDecimal(aliq))
                .deducao(new BigDecimal(ded))
                .descricao(desc)
                .build();
    }
}
