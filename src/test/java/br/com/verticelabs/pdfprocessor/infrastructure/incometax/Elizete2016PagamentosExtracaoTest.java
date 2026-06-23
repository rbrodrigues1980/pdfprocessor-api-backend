package br.com.verticelabs.pdfprocessor.infrastructure.incometax;

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
import br.com.verticelabs.pdfprocessor.domain.service.IncomeTaxDeclarationService.IncomeTaxInfo.PagamentoEfetuado;
import br.com.verticelabs.pdfprocessor.infrastructure.config.IrTributacaoParametrosUtil;
import br.com.verticelabs.pdfprocessor.infrastructure.excel.ExcelIrpfSimulacaoMapper;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.io.FileInputStream;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Extração de pagamentos efetuados — declaração Elizete AC 2016.
 */
class Elizete2016PagamentosExtracaoTest {

    private static final Path ELIZETE_PDF = Paths.get(
            "temp/PASTADOCUMENTOSDESSIMULADORIRPF - Rogerio Rodrigues",
            "Elizete de Almeida Lins Santos - AEA AL",
            "2016",
            "DeclaraçãodeImpostodeRenda_2016.pdf");

    @Test
    void extraiTresPagamentosDoPdfElizete() throws Exception {
        Assumptions.assumeTrue(Files.exists(ELIZETE_PDF),
                "PDF da Elizete não encontrado em: " + ELIZETE_PDF.toAbsolutePath());

        ITextIncomeTaxServiceImpl service = new ITextIncomeTaxServiceImpl();
        var info = service.extractIncomeTaxInfo(new FileInputStream(ELIZETE_PDF.toFile())).block();

        assert info != null;
        List<PagamentoEfetuado> pagamentos = info.getPagamentosEfetuados();
        assertEquals(3, pagamentos.size(), "Deve extrair 3 pagamentos (50, 36, 26)");

        PagamentoEfetuado cod50 = findByCodigo(pagamentos, "50").orElseThrow();
        assertEquals(new BigDecimal("1372.80"), cod50.getValorPago());
        assertEquals("903.582.014-20", cod50.getCpfCnpj());
        assertEquals("126.97344.01-4", cod50.getNitEmpregadoDomestico());
        assertTrue(cod50.getNomeBeneficiario().toUpperCase().contains("EDINALVA"));

        PagamentoEfetuado cod36 = findByCodigo(pagamentos, "36").orElseThrow();
        assertEquals(new BigDecimal("3586.68"), cod36.getValorPago());
        assertTrue(cod36.getNomeBeneficiario().toUpperCase().contains("FUNCEF"));

        PagamentoEfetuado cod26 = findByCodigo(pagamentos, "26").orElseThrow();
        assertEquals(new BigDecimal("2894.40"), cod26.getValorPago());
        assertTrue(cod26.getNomeBeneficiario().toUpperCase().contains("CAIXA"));
    }

    @Test
    void mapperPersistePagamentosNoIrpfData() throws Exception {
        Assumptions.assumeTrue(Files.exists(ELIZETE_PDF),
                "PDF da Elizete não encontrado em: " + ELIZETE_PDF.toAbsolutePath());

        ITextIncomeTaxServiceImpl service = new ITextIncomeTaxServiceImpl();
        var info = service.extractIncomeTaxInfo(new FileInputStream(ELIZETE_PDF.toFile())).block();
        assert info != null;

        IrpfDeclaracaoDataMapper mapper = new IrpfDeclaracaoDataMapper();
        IrpfDeclaracaoData data = mapper.fromIncomeTaxInfo(info);

        assertEquals(3, data.getPagamentosEfetuados().size());
        assertEquals("2016", data.getAnoCalendario());
    }

    @Test
    void pipelineCompleto_extracaoMapperSimulacaoCompletaESimplificada() throws Exception {
        Assumptions.assumeTrue(Files.exists(ELIZETE_PDF));

        ITextIncomeTaxServiceImpl service = new ITextIncomeTaxServiceImpl();
        var info = service.extractIncomeTaxInfo(new FileInputStream(ELIZETE_PDF.toFile())).block();
        assert info != null;

        IrpfDeclaracaoData data = new IrpfDeclaracaoDataMapper().fromIncomeTaxInfo(info);
        ExcelIrpfSimulacaoMapper excelMapper = new ExcelIrpfSimulacaoMapper(new IrPagamentosDeducaoAggregator());
        SimuladorIrpfRequest req = excelMapper.fromDeclaracao(data, BigDecimal.ZERO);

        assertEquals(new BigDecimal("1372.80"), req.getPrevidenciaEmpregadoDomestico());
        assertEquals(new BigDecimal("3586.68"), req.getPrevidenciaPrivada());
        assertEquals(new BigDecimal("2894.40"), req.getDespesasMedicas());

        BigDecimal inssEfetivo = IrDoacoesDeducaoCalculator.calcularInssDomesticoEfetivo(
                req.getPrevidenciaEmpregadoDomestico(), 2016, params2016());
        assertEquals(new BigDecimal("1092.00"), inssEfetivo);

        IrSimuladorMotorService motor = new IrSimuladorMotorService(
                new IrCalculoProgressivoService(), new IrDoacoesDeducaoCalculator());
        SimuladorIrpfResponse resp = motor.simular(req, faixas2016(), params2016());

        assertNotNull(resp.getModeloCompleto());
        assertNotNull(resp.getModeloSimplificado());
        assertTrue(resp.getModeloCompleto().getTotalDeducoes().compareTo(BigDecimal.ZERO) > 0);
    }

    private IrParametrosAnuais params2016() {
        return IrParametrosAnuais.builder()
                .deducaoDependente(new BigDecimal("2275.08"))
                .limiteInstrucao(new BigDecimal("3561.50"))
                .limiteDescontoSimplificado(new BigDecimal("16754.34"))
                .limiteInssDomestico(IrTributacaoParametrosUtil.limiteInssDomestico(2016))
                .build();
    }

    private List<IrTabelaTributacao> faixas2016() {
        List<IrTabelaTributacao> faixas = new ArrayList<>();
        faixas.add(faixa(2016, 1, "0", "22847.76", "0", "0"));
        faixas.add(faixa(2016, 2, "22847.77", "33919.80", "0.075", "1713.58"));
        faixas.add(faixa(2016, 3, "33919.81", "45012.60", "0.15", "4257.57"));
        faixas.add(faixa(2016, 4, "45012.61", "55976.16", "0.225", "7633.51"));
        faixas.add(faixa(2016, 5, "55976.17", null, "0.275", "10432.32"));
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

    private Optional<PagamentoEfetuado> findByCodigo(List<PagamentoEfetuado> pagamentos, String codigo) {
        return pagamentos.stream()
                .filter(p -> codigo.equals(p.getCodigo()) || codigo.equals(String.valueOf(Integer.parseInt(p.getCodigo()))))
                .findFirst();
    }
}
