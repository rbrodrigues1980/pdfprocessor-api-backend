package br.com.verticelabs.pdfprocessor.infrastructure.excel;

import br.com.verticelabs.pdfprocessor.application.incometax.IrpfDeclaracaoDataMapper;
import br.com.verticelabs.pdfprocessor.application.tributacao.IrCalculoProgressivoService;
import br.com.verticelabs.pdfprocessor.application.tributacao.IrDoacoesDeducaoCalculator;
import br.com.verticelabs.pdfprocessor.application.tributacao.IrPagamentosDeducaoAggregator;
import br.com.verticelabs.pdfprocessor.application.tributacao.IrSimuladorMotorService;
import br.com.verticelabs.pdfprocessor.application.tributacao.dto.SimuladorIrpfRequest;
import br.com.verticelabs.pdfprocessor.application.tributacao.dto.SimuladorIrpfResponse;
import br.com.verticelabs.pdfprocessor.domain.model.IrParametrosAnuais;
import br.com.verticelabs.pdfprocessor.domain.model.IrTabelaTributacao;
import br.com.verticelabs.pdfprocessor.domain.model.IrpfDeclaracaoData;
import br.com.verticelabs.pdfprocessor.infrastructure.config.IrTributacaoParametrosUtil;
import br.com.verticelabs.pdfprocessor.infrastructure.incometax.ITextIncomeTaxServiceImpl;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.FileInputStream;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Regressão Elizete AC 2018 — bloco 1 (espelho simplificado) e bloco 2 (Completa + planilha).
 */
class Elizete2018ExcelSimulacaoLayoutTest {

    private static final Path ELIZETE_PDF_2018 = Paths.get(
            "temp/PASTADOCUMENTOSDESSIMULADORIRPF - Rogerio Rodrigues",
            "Elizete de Almeida Lins Santos - AEA AL",
            "2018",
            "DeclaraçãodeImpostodeRenda_2018.pdf");

    /** Total prev. complementar da planilha de contracheques (referência). */
    private static final BigDecimal PREV_PLANILHA_2018 = new BigDecimal("14884.95");

    private IrSimuladorMotorService motor;
    private ExcelIrpfSimulacaoMapper excelMapper;
    private ExcelIrpfDeducoesResumoHelper deducoesHelper;
    private IrParametrosAnuais params2018;
    private List<IrTabelaTributacao> faixas2018;

    @BeforeEach
    void setUp() {
        motor = new IrSimuladorMotorService(new IrCalculoProgressivoService(), new IrDoacoesDeducaoCalculator());
        excelMapper = new ExcelIrpfSimulacaoMapper(new IrPagamentosDeducaoAggregator());
        deducoesHelper = new ExcelIrpfDeducoesResumoHelper(motor, new IrPagamentosDeducaoAggregator());
        params2018 = IrParametrosAnuais.builder()
                .deducaoDependente(new BigDecimal("2275.08"))
                .limiteInstrucao(new BigDecimal("3561.50"))
                .limiteDescontoSimplificado(new BigDecimal("16754.34"))
                .limiteInssDomestico(IrTributacaoParametrosUtil.limiteInssDomestico(2018))
                .build();
        faixas2018 = criarFaixas2018();
    }

    @Test
    void bloco1_espelhoSimplificado_camposResumoReferencia() {
        IrpfDeclaracaoData data = elizete2018ResumoReferencia();

        assertEquals(new BigDecimal("118376.76"), data.getRendimentosTributaveisTotal());
        assertEquals(new BigDecimal("16754.34"), data.getDescontoSimplificado());
        assertEquals(new BigDecimal("101622.42"), data.getBaseCalculoImposto());
        assertEquals(new BigDecimal("17513.84"), data.getImpostoDevido());
        assertEquals(new BigDecimal("27866.00"), data.getImpostoSobreRRA());
        assertEquals(new BigDecimal("45379.84"), data.getTotalImpostoDevido());
        assertEquals(new BigDecimal("22495.49"), data.getImpostoPagoTotal());
        assertEquals(new BigDecimal("22884.35"), data.getSaldoImpostoPagar());
    }

    @Test
    void bloco1_pdf_extraiRendimentosEPrevOficial() throws Exception {
        IrpfDeclaracaoData data = carregarElizete2018();
        Assumptions.assumeTrue("SIMPLIFICADO".equalsIgnoreCase(data.getTipoTributacao()));

        assertEquals(new BigDecimal("118376.76"), data.getRendimentosTributaveisTotal());
        assertEquals(new BigDecimal("16754.34"), data.getDescontoSimplificado());
        assertEquals(new BigDecimal("101622.42"), data.getBaseCalculoImposto());
        assertEquals(new BigDecimal("44.73"), data.getContribuicaoPrevidenciaSocial());
    }

    @Test
    void bloco2_simulacaoCompletaPlanilha_valoresReferencia() throws Exception {
        IrpfDeclaracaoData data = carregarElizete2018();

        SimuladorIrpfRequest request = excelMapper.fromDeclaracao(data, PREV_PLANILHA_2018, true);
        request.setInssDomesticoComoCreditoImposto(true);

        ExcelIrpfDeducoesResumoDTO ded = deducoesHelper.montar(data, request, PREV_PLANILHA_2018, params2018);
        assertEquals(new BigDecimal("44.73"), ded.getPrevOficialPublica());
        assertEquals(new BigDecimal("14205.21"), ded.getPrevComplementarPrivadaEfetiva());
        assertEquals(new BigDecimal("2634.01"), ded.getDespesasMedicas());
        assertEquals(new BigDecimal("16883.95"), ded.getTotalDeducoes());

        SimuladorIrpfResponse resp = motor.simular(request, faixas2018, params2018);
        var modelo = resp.getModeloCompleto();
        assertNotNull(modelo);

        assertEquals(new BigDecimal("101492.81"), modelo.getBaseCalculo());
        assertEquals(new BigDecimal("17478.20"), modelo.getImpostoDevidoFinal());
        assertTrue(nvl(modelo.getCreditoInssDomestico()).compareTo(BigDecimal.ZERO) > 0);
        assertEquals(new BigDecimal("16534.89"), modelo.getImpostoDevidoII());
        assertEquals(new BigDecimal("44400.89"), modelo.getResumo().getTotalImpostoDevido());
        assertEquals(new BigDecimal("13.96"), modelo.getAliquotaEfetiva());

        BigDecimal saldo = modelo.getResumo().getTotalImpostoDevido()
                .subtract(data.getImpostoPagoTotal());
        assertEquals(new BigDecimal("21905.40"), saldo);
    }

    @Test
    void helper_somaDeducoes_semInssNaBase() throws Exception {
        IrpfDeclaracaoData data = IrpfDeclaracaoData.builder()
                .anoCalendario("2018")
                .rendimentosTributaveisTotal(new BigDecimal("118376.76"))
                .rendimentosFontesTitular(List.of(
                        IrpfDeclaracaoData.FontePagadoraIrpf.builder()
                                .contrPrevOficial(new BigDecimal("44.73")).build()))
                .pagamentosEfetuados(List.of(
                        IrpfDeclaracaoData.PagamentoEfetuadoIrpf.builder()
                                .codigo("26").valorPago(new BigDecimal("2634.01")).build(),
                        IrpfDeclaracaoData.PagamentoEfetuadoIrpf.builder()
                                .codigo("50").valorPago(new BigDecimal("943.31")).build()))
                .build();

        SimuladorIrpfRequest req = excelMapper.fromDeclaracao(data, PREV_PLANILHA_2018, true);
        req.setInssDomesticoComoCreditoImposto(true);

        ExcelIrpfDeducoesResumoDTO ded = deducoesHelper.montar(data, req, PREV_PLANILHA_2018, params2018);
        assertEquals(new BigDecimal("16883.95"), ded.getTotalDeducoes());
        assertEquals(new BigDecimal("943.31"), ded.getInssDomesticoCredito());
    }

    private IrpfDeclaracaoData elizete2018ResumoReferencia() {
        return IrpfDeclaracaoData.builder()
                .exercicio("2019")
                .anoCalendario("2018")
                .tipoTributacao("SIMPLIFICADO")
                .rendimentosTributaveisTotal(new BigDecimal("118376.76"))
                .rendimentosTributaveisTitularPJ(new BigDecimal("118376.76"))
                .descontoSimplificado(new BigDecimal("16754.34"))
                .baseCalculoImposto(new BigDecimal("101622.42"))
                .impostoDevido(new BigDecimal("17513.84"))
                .impostoSobreRRA(new BigDecimal("27866.00"))
                .aliquotaEfetiva(new BigDecimal("14.79"))
                .totalImpostoDevido(new BigDecimal("45379.84"))
                .impostoRetidoFonteTitular(new BigDecimal("10840.13"))
                .impostoRetidoRRA(new BigDecimal("11655.36"))
                .impostoPagoTotal(new BigDecimal("22495.49"))
                .impostoRestituir(BigDecimal.ZERO)
                .saldoImpostoPagar(new BigDecimal("22884.35"))
                .build();
    }

    private IrpfDeclaracaoData carregarElizete2018() throws Exception {
        Assumptions.assumeTrue(Files.exists(ELIZETE_PDF_2018),
                "PDF 2018 da Elizete não encontrado: " + ELIZETE_PDF_2018.toAbsolutePath());

        ITextIncomeTaxServiceImpl service = new ITextIncomeTaxServiceImpl();
        var info = service.extractIncomeTaxInfo(new FileInputStream(ELIZETE_PDF_2018.toFile())).block();
        assertNotNull(info);
        return new IrpfDeclaracaoDataMapper().fromIncomeTaxInfo(info);
    }

    private List<IrTabelaTributacao> criarFaixas2018() {
        List<IrTabelaTributacao> faixas = new ArrayList<>();
        faixas.add(faixa(2018, 1, "0", "22847.76", "0", "0"));
        faixas.add(faixa(2018, 2, "22847.77", "33919.80", "0.075", "1713.58"));
        faixas.add(faixa(2018, 3, "33919.81", "45012.60", "0.15", "4257.57"));
        faixas.add(faixa(2018, 4, "45012.61", "55976.16", "0.225", "7633.51"));
        faixas.add(faixa(2018, 5, "55976.17", null, "0.275", "10432.32"));
        return faixas;
    }

    private IrTabelaTributacao faixa(int ano, int n, String inf, String sup, String aliq, String ded) {
        return IrTabelaTributacao.builder()
                .anoCalendario(ano)
                .tipoIncidencia("ANUAL")
                .faixa(n)
                .limiteInferior(new BigDecimal(inf))
                .limiteSuperior(sup != null ? new BigDecimal(sup) : null)
                .aliquota(new BigDecimal(aliq))
                .deducao(new BigDecimal(ded))
                .build();
    }

    private BigDecimal nvl(BigDecimal v) {
        return v != null ? v : BigDecimal.ZERO;
    }
}
