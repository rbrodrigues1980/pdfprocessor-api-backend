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
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Regressão Elizete AC 2019 — bloco 1 (espelho Completo) e bloco 2 (simulação planilha).
 */
class Elizete2019ExcelSimulacaoLayoutTest {

    private static final Path ELIZETE_PDF_2019 = Paths.get(
            "temp/PASTADOCUMENTOSDESSIMULADORIRPF - Rogerio Rodrigues",
            "Elizete de Almeida Lins Santos - AEA AL",
            "2019",
            "DeclaraçãodeImpostodeRenda_2019.pdf");

    /** Total prev. complementar da planilha de contracheques (referência). */
    private static final BigDecimal PREV_PLANILHA_2019 = new BigDecimal("14849.68");

    private IrSimuladorMotorService motor;
    private ExcelIrpfSimulacaoMapper excelMapper;
    private ExcelIrpfDeducoesResumoHelper deducoesHelper;
    private IrParametrosAnuais params2019;
    private List<IrTabelaTributacao> faixas2019;

    @BeforeEach
    void setUp() {
        motor = new IrSimuladorMotorService(new IrCalculoProgressivoService(), new IrDoacoesDeducaoCalculator());
        excelMapper = new ExcelIrpfSimulacaoMapper(new IrPagamentosDeducaoAggregator());
        deducoesHelper = new ExcelIrpfDeducoesResumoHelper(motor, new IrPagamentosDeducaoAggregator());
        params2019 = IrParametrosAnuais.builder()
                .deducaoDependente(new BigDecimal("2275.08"))
                .limiteInstrucao(new BigDecimal("3561.50"))
                .limiteDescontoSimplificado(new BigDecimal("16754.34"))
                .limiteInssDomestico(IrTributacaoParametrosUtil.limiteInssDomestico(2019))
                .build();
        faixas2019 = criarFaixas2019();
    }

    @Test
    void bloco1_espelhoCompleto_camposResumoReferencia() {
        IrpfDeclaracaoData data = elizete2019ResumoReferencia();
        ExcelIrpfDeducoesResumoDTO ded = deducoesHelper.montarConformeDeclaracao(data);

        assertEquals("COMPLETO", data.getTipoTributacao());
        assertEquals(new BigDecimal("123747.32"), data.getRendimentosTributaveisTotal());
        assertEquals(new BigDecimal("94.67"), ded.getPrevOficialPublica());
        assertEquals(new BigDecimal("3184.77"), ded.getPrevComplementarPrivadaEfetiva());
        assertEquals(new BigDecimal("4406.06"), ded.getDespesasMedicas());
        assertEquals(new BigDecimal("7685.50"), ded.getTotalDeducoes());
        assertEquals(new BigDecimal("14849.68"), ded.getLimite12PctRendimentos());
        assertEquals(new BigDecimal("116061.82"), data.getBaseCalculoImposto());
        assertEquals(new BigDecimal("21484.68"), data.getImpostoDevido());
        assertEquals(new BigDecimal("87925.78"), data.getImpostoSobreRRA());
        assertEquals(new BigDecimal("109410.46"), data.getTotalImpostoDevido());
        assertEquals(new BigDecimal("96977.88"), data.getImpostoPagoTotal());
        assertEquals(new BigDecimal("12432.58"), data.getSaldoImpostoPagar());
    }

    @Test
    void bloco1_pdf_extraiDeducoesEImposto() throws Exception {
        IrpfDeclaracaoData data = carregarElizete2019();
        Assumptions.assumeTrue("COMPLETO".equalsIgnoreCase(data.getTipoTributacao()));

        ExcelIrpfDeducoesResumoDTO ded = deducoesHelper.montarConformeDeclaracao(data);

        assertEquals(new BigDecimal("123747.32"), data.getRendimentosTributaveisTotal());
        assertEquals(new BigDecimal("7685.50"), ded.getTotalDeducoes());
        assertEquals(new BigDecimal("116061.82"), data.getBaseCalculoImposto());
        assertEquals(new BigDecimal("109410.46"), data.getTotalImpostoDevido());
        assertEquals(new BigDecimal("12432.58"), data.getSaldoImpostoPagar());
        assertNull(data.getImpostoDevidoII());
    }

    @Test
    void bloco2_simulacaoPlanilha_semInss_impostoIINulo() throws Exception {
        IrpfDeclaracaoData data = carregarElizete2019();
        Assumptions.assumeTrue("COMPLETO".equalsIgnoreCase(data.getTipoTributacao()));

        SimuladorIrpfRequest request = excelMapper.fromDeclaracao(data, PREV_PLANILHA_2019, true);
        request.setInssDomesticoComoCreditoImposto(true);

        SimuladorIrpfResponse resp = motor.simular(request, faixas2019, params2019);
        var modelo = resp.getModeloCompleto();
        assertNotNull(modelo);

        assertNull(modelo.getImpostoDevidoII());
        assertEquals(0, nvl(modelo.getCreditoInssDomestico()).compareTo(BigDecimal.ZERO));

        BigDecimal totalEsperado = modelo.getImpostoDevidoFinal().add(data.getImpostoSobreRRA());
        assertEquals(totalEsperado, modelo.getResumo().getTotalImpostoDevido());
        assertEquals(new BigDecimal("106202.61"), modelo.getResumo().getTotalImpostoDevido());
        assertEquals(new BigDecimal("18276.83"), modelo.getImpostoDevidoFinal());

        BigDecimal saldo = modelo.getResumo().getTotalImpostoDevido().subtract(data.getImpostoPagoTotal());
        assertEquals(new BigDecimal("9224.73"), saldo);
    }

    private IrpfDeclaracaoData elizete2019ResumoReferencia() {
        return IrpfDeclaracaoData.builder()
                .exercicio("2020")
                .anoCalendario("2019")
                .tipoTributacao("COMPLETO")
                .rendimentosTributaveisTotal(new BigDecimal("123747.32"))
                .rendimentosTributaveisTitularPJ(new BigDecimal("123747.32"))
                .contribuicaoPrevidenciaOficialResumo(new BigDecimal("94.67"))
                .contribuicaoPrevidenciaSocial(new BigDecimal("94.67"))
                .contribuicaoPrevidenciaPrivada(new BigDecimal("3184.77"))
                .despesasMedicas(new BigDecimal("4406.06"))
                .deducoesTotal(new BigDecimal("7685.50"))
                .baseCalculoImposto(new BigDecimal("116061.82"))
                .impostoDevido(new BigDecimal("21484.68"))
                .impostoDevidoI(new BigDecimal("21484.68"))
                .impostoSobreRRA(new BigDecimal("87925.78"))
                .aliquotaEfetiva(new BigDecimal("17.36"))
                .totalImpostoDevido(new BigDecimal("109410.46"))
                .impostoRetidoFonteTitular(new BigDecimal("9052.09"))
                .impostoRetidoRRA(new BigDecimal("87925.79"))
                .impostoPagoTotal(new BigDecimal("96977.88"))
                .impostoRestituir(BigDecimal.ZERO)
                .saldoImpostoPagar(new BigDecimal("12432.58"))
                .build();
    }

    private IrpfDeclaracaoData carregarElizete2019() throws Exception {
        Assumptions.assumeTrue(Files.exists(ELIZETE_PDF_2019),
                "PDF 2019 da Elizete não encontrado: " + ELIZETE_PDF_2019.toAbsolutePath());

        ITextIncomeTaxServiceImpl service = new ITextIncomeTaxServiceImpl();
        var info = service.extractIncomeTaxInfo(new FileInputStream(ELIZETE_PDF_2019.toFile())).block();
        assertNotNull(info);
        return new IrpfDeclaracaoDataMapper().fromIncomeTaxInfo(info);
    }

    private List<IrTabelaTributacao> criarFaixas2019() {
        List<IrTabelaTributacao> faixas = new ArrayList<>();
        faixas.add(faixa(2019, 1, "0", "22847.76", "0", "0"));
        faixas.add(faixa(2019, 2, "22847.77", "33919.80", "0.075", "1713.58"));
        faixas.add(faixa(2019, 3, "33919.81", "45012.60", "0.15", "4257.57"));
        faixas.add(faixa(2019, 4, "45012.61", "55976.16", "0.225", "7633.51"));
        faixas.add(faixa(2019, 5, "55976.17", null, "0.275", "10432.32"));
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
