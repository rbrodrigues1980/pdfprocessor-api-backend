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
import br.com.verticelabs.pdfprocessor.domain.model.IrpfDeclaracaoData.PagamentoEfetuadoIrpf;
import br.com.verticelabs.pdfprocessor.infrastructure.config.IrTributacaoParametrosUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Elizete AC 2016 — pagamento código 50 (INSS patronal doméstico) via lista granular.
 */
class ElizeteAc2016PagamentosTest {

    private IrSimuladorMotorService motor;
    private ExcelIrpfSimulacaoMapper mapper;
    private IrParametrosAnuais params2016;

    @BeforeEach
    void setUp() {
        motor = new IrSimuladorMotorService(new IrCalculoProgressivoService(), new IrDoacoesDeducaoCalculator());
        mapper = new ExcelIrpfSimulacaoMapper(new IrPagamentosDeducaoAggregator());
        params2016 = IrParametrosAnuais.builder()
                .deducaoDependente(new BigDecimal("2275.08"))
                .limiteInstrucao(new BigDecimal("3561.50"))
                .limiteDescontoSimplificado(new BigDecimal("16754.34"))
                .limiteInssDomestico(IrTributacaoParametrosUtil.limiteInssDomestico(2016))
                .build();
    }

    @Test
    void codigo50_priorizaPagamentos_sobreResumo() {
        IrpfDeclaracaoData data = elizeteAc2016ComPagamentos();

        SimuladorIrpfRequest req = mapper.fromDeclaracao(data, new BigDecimal("5000.00"));

        assertEquals(new BigDecimal("1092.00"), req.getPrevidenciaEmpregadoDomestico());
        assertNull(req.getDespesasInstrucaoDeclarada());

        BigDecimal inssEfetivo = IrDoacoesDeducaoCalculator.calcularInssDomesticoEfetivo(
                req.getPrevidenciaEmpregadoDomestico(), 2016, params2016);
        assertEquals(new BigDecimal("1092.00"), inssEfetivo);

        SimuladorIrpfResponse resp = motor.simular(req, criarFaixas2016(), params2016);
        assertNotNull(resp.getModeloCompleto().getBaseCalculo());
        assertTrue(resp.getModeloCompleto().getTotalDeducoes()
                .compareTo(req.getPrevidenciaEmpregadoDomestico()) >= 0);
    }

    @Test
    void codigo50_respeitaTeto2016() {
        IrpfDeclaracaoData data = elizeteAc2016ComPagamentos();
        data.getPagamentosEfetuados().add(PagamentoEfetuadoIrpf.builder()
                .codigo("50")
                .valorPago(new BigDecimal("500.00"))
                .build());

        SimuladorIrpfRequest req = mapper.fromDeclaracao(data, BigDecimal.ZERO);
        assertEquals(new BigDecimal("1592.00"), req.getPrevidenciaEmpregadoDomestico());

        BigDecimal inssEfetivo = IrDoacoesDeducaoCalculator.calcularInssDomesticoEfetivo(
                req.getPrevidenciaEmpregadoDomestico(), 2016, params2016);
        assertEquals(new BigDecimal("1092.00"), inssEfetivo);
    }

    @Test
    void sim2_prevPlanilhaPrevaleceSobrePagamentosCod36() {
        IrpfDeclaracaoData data = elizeteAc2016PagamentosReais();
        BigDecimal prevPlanilha = new BigDecimal("5586.90");

        SimuladorIrpfRequest reqDecl = mapper.fromDeclaracao(data, prevPlanilha, false);
        assertEquals(new BigDecimal("3586.68"), reqDecl.getPrevidenciaPrivada());

        SimuladorIrpfRequest reqSim2 = mapper.fromDeclaracao(data, prevPlanilha, true);
        assertEquals(prevPlanilha, reqSim2.getPrevidenciaPrivada());
    }

    @Test
    void elizete2016_tresPagamentosDoPdf() {
        IrpfDeclaracaoData data = elizeteAc2016PagamentosReais();

        SimuladorIrpfRequest req = mapper.fromDeclaracao(data, BigDecimal.ZERO);

        assertEquals(new BigDecimal("2894.40"), req.getDespesasMedicas());
        assertEquals(new BigDecimal("3586.68"), req.getPrevidenciaPrivada());
        assertEquals(new BigDecimal("1372.80"), req.getPrevidenciaEmpregadoDomestico());

        BigDecimal inssEfetivo = IrDoacoesDeducaoCalculator.calcularInssDomesticoEfetivo(
                req.getPrevidenciaEmpregadoDomestico(), 2016, params2016);
        assertEquals(new BigDecimal("1092.00"), inssEfetivo);
    }

    private IrpfDeclaracaoData elizeteAc2016PagamentosReais() {
        List<PagamentoEfetuadoIrpf> pagamentos = new ArrayList<>();
        pagamentos.add(PagamentoEfetuadoIrpf.builder()
                .codigo("50")
                .nomeBeneficiario("EDINALVA TEIXEIRA DA SILVA")
                .cpfCnpj("903.582.014-20")
                .valorPago(new BigDecimal("1372.80"))
                .nitEmpregadoDomestico("126.97344.01-4")
                .build());
        pagamentos.add(PagamentoEfetuadoIrpf.builder()
                .codigo("36")
                .nomeBeneficiario("FUNCEF")
                .cpfCnpj("00.436.923/0001-90")
                .valorPago(new BigDecimal("3586.68"))
                .build());
        pagamentos.add(PagamentoEfetuadoIrpf.builder()
                .codigo("26")
                .nomeBeneficiario("CAIXA ECONOMICA FEDERAL")
                .cpfCnpj("00.360.305/0001-04")
                .valorPago(new BigDecimal("2894.40"))
                .build());

        return IrpfDeclaracaoData.builder()
                .exercicio("2017")
                .anoCalendario("2016")
                .cpfTitular("18522068453")
                .tipoTributacao("SIMPLIFICADO")
                .rendimentosTributaveisTotal(new BigDecimal("85741.06"))
                .pagamentosEfetuados(pagamentos)
                .build();
    }

    private IrpfDeclaracaoData elizeteAc2016ComPagamentos() {
        List<PagamentoEfetuadoIrpf> pagamentos = new ArrayList<>();
        pagamentos.add(PagamentoEfetuadoIrpf.builder()
                .codigo("50")
                .nomeBeneficiario("INSS")
                .valorPago(new BigDecimal("1092.00"))
                .build());
        pagamentos.add(PagamentoEfetuadoIrpf.builder()
                .codigo("26")
                .valorPago(new BigDecimal("3500.00"))
                .build());

        return IrpfDeclaracaoData.builder()
                .exercicio("2017")
                .anoCalendario("2016")
                .cpfTitular("00000000000")
                .rendimentosTributaveisTotal(new BigDecimal("65000.00"))
                .contribuicaoPrevidenciaSocial(new BigDecimal("5000.00"))
                .deducaoDependentes(new BigDecimal("4550.16"))
                .despesasMedicas(new BigDecimal("9999.00"))
                .despesasInstrucao(new BigDecimal("7123.00"))
                .contribuicaoPatronalPrevidenciaSocial(BigDecimal.ZERO)
                .pagamentosEfetuados(pagamentos)
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
