package br.com.verticelabs.pdfprocessor.infrastructure.incometax;

import br.com.verticelabs.pdfprocessor.application.incometax.IrpfDeclaracaoDataMapper;
import br.com.verticelabs.pdfprocessor.application.tributacao.IrCalculoProgressivoService;
import br.com.verticelabs.pdfprocessor.application.tributacao.IrDoacoesDeducaoCalculator;
import br.com.verticelabs.pdfprocessor.application.tributacao.IrPagamentosDeducaoAggregator;
import br.com.verticelabs.pdfprocessor.application.tributacao.IrSimuladorMotorService;
import br.com.verticelabs.pdfprocessor.application.tributacao.dto.SimuladorIrpfRequest;
import br.com.verticelabs.pdfprocessor.domain.model.IrParametrosAnuais;
import br.com.verticelabs.pdfprocessor.domain.model.IrpfDeclaracaoData;
import br.com.verticelabs.pdfprocessor.domain.service.IncomeTaxDeclarationService.IncomeTaxInfo;
import br.com.verticelabs.pdfprocessor.infrastructure.config.IrTributacaoParametrosUtil;
import br.com.verticelabs.pdfprocessor.infrastructure.excel.ExcelIrpfDeducoesResumoDTO;
import br.com.verticelabs.pdfprocessor.infrastructure.excel.ExcelIrpfDeducoesResumoHelper;
import br.com.verticelabs.pdfprocessor.infrastructure.excel.ExcelIrpfSimulacaoMapper;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.io.FileInputStream;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Edirce AC 2019 — DEPENDENTES Sem Informações. A simulação não pode usar
 * 170.498,81 (total de rendimentos do titular) como dedução de dependentes.
 */
class Edirce2019DependentesSemInformacoesTest {

    private static final Path PDF_COPIA = Paths.get("temp/_edirce/declaracao_2019.pdf");
    private static final Path PDF_ORIG = Paths.get(
            "temp/PASTADOCUMENTOS/Edirce Soares Ramos de Moura - AEA PE INCONSISTÊNCIA ANOS 2019 E 2020 OBS IRS",
            "DeclaraçãodeImpostodeRenda_2019.pdf");

    private static final BigDecimal RENDIMENTOS = new BigDecimal("170498.81");

    @Test
    void extraiZeroDependentesQuandoSecaoSemInformacoes() throws Exception {
        Path pdf = resolverPdf();
        Assumptions.assumeTrue(pdf != null && Files.exists(pdf), "PDF Edirce 2019 não encontrado");

        ITextIncomeTaxServiceImpl service = new ITextIncomeTaxServiceImpl();
        IncomeTaxInfo info = service.extractIncomeTaxInfo(new FileInputStream(pdf.toFile())).block();
        assertNotNull(info);

        assertTrue(info.getDependentes() == null || info.getDependentes().isEmpty());
        assertEquals(0, nvl(info.getDeducoesDependentes()).compareTo(BigDecimal.ZERO),
                "RESUMO Simplificado não tem linha Dependentes");
        assertEquals(0, nvl(info.getTotalDeducaoDependentes()).compareTo(BigDecimal.ZERO));
        assertEquals(0, RENDIMENTOS.compareTo(info.getRendimentosTributaveis()));

        IrpfDeclaracaoData data = new IrpfDeclaracaoDataMapper().fromIncomeTaxInfo(info);
        assertEquals(0, nvl(data.getDeducaoDependentes()).compareTo(BigDecimal.ZERO));

        ExcelIrpfSimulacaoMapper excelMapper = new ExcelIrpfSimulacaoMapper(new IrPagamentosDeducaoAggregator());
        IrSimuladorMotorService motor = new IrSimuladorMotorService(
                new IrCalculoProgressivoService(), new IrDoacoesDeducaoCalculator());
        ExcelIrpfDeducoesResumoHelper helper = new ExcelIrpfDeducoesResumoHelper(
                motor, new IrPagamentosDeducaoAggregator());
        IrParametrosAnuais params = IrParametrosAnuais.builder()
                .deducaoDependente(new BigDecimal("2275.08"))
                .limiteInstrucao(new BigDecimal("3561.50"))
                .limiteDescontoSimplificado(new BigDecimal("16754.34"))
                .limiteInssDomestico(IrTributacaoParametrosUtil.limiteInssDomestico(2019))
                .build();

        SimuladorIrpfRequest request = excelMapper.fromDeclaracao(
                data, data.getContribuicaoPrevidenciaPrivada(), true);
        ExcelIrpfDeducoesResumoDTO simulacao = helper.montar(
                data, request, data.getContribuicaoPrevidenciaPrivada(), params);

        assertEquals(0, nvl(request.getDeducaoDependentesDeclarada()).compareTo(BigDecimal.ZERO));
        assertEquals(0, nvl(request.getQtdDependentes()));
        assertEquals(0, nvl(simulacao.getDependentes()).compareTo(BigDecimal.ZERO),
                "Simulação não pode copiar rendimentos 170.498,81 para Dependentes");
    }

    private static Path resolverPdf() {
        if (Files.exists(PDF_COPIA)) {
            return PDF_COPIA;
        }
        if (Files.exists(PDF_ORIG)) {
            return PDF_ORIG;
        }
        return PDF_COPIA;
    }

    private static BigDecimal nvl(BigDecimal v) {
        return v != null ? v : BigDecimal.ZERO;
    }

    private static int nvl(Integer v) {
        return v != null ? v : 0;
    }
}
