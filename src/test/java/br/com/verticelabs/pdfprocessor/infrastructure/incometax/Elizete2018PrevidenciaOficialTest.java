package br.com.verticelabs.pdfprocessor.infrastructure.incometax;

import br.com.verticelabs.pdfprocessor.application.incometax.IrpfDeclaracaoDataMapper;
import br.com.verticelabs.pdfprocessor.application.incometax.IrpfPrevidenciaOficialResolver;
import br.com.verticelabs.pdfprocessor.domain.model.IrpfDeclaracaoData;
import br.com.verticelabs.pdfprocessor.domain.service.IncomeTaxDeclarationService.IncomeTaxInfo;
import br.com.verticelabs.pdfprocessor.domain.service.IncomeTaxDeclarationService.IncomeTaxInfo.FontePagadora;
import br.com.verticelabs.pdfprocessor.infrastructure.excel.ExcelIrpfSimulacaoMapper;
import br.com.verticelabs.pdfprocessor.application.tributacao.IrPagamentosDeducaoAggregator;
import br.com.verticelabs.pdfprocessor.application.tributacao.dto.SimuladorIrpfRequest;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.io.FileInputStream;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Elizete AC 2018 — previdência oficial da Wiz (44,73) deve entrar na dedução.
 */
class Elizete2018PrevidenciaOficialTest {

    private static final Path ELIZETE_PDF_2018 = Paths.get(
            "temp/PASTADOCUMENTOSDESSIMULADORIRPF - Rogerio Rodrigues",
            "Elizete de Almeida Lins Santos - AEA AL",
            "2018",
            "DeclaraçãodeImpostodeRenda_2018.pdf");

    @Test
    void extraiFontesPagadorasComPrevOficialWiz() throws Exception {
        Assumptions.assumeTrue(Files.exists(ELIZETE_PDF_2018),
                "PDF 2018 da Elizete não encontrado: " + ELIZETE_PDF_2018.toAbsolutePath());

        ITextIncomeTaxServiceImpl service = new ITextIncomeTaxServiceImpl();
        IncomeTaxInfo info = service.extractIncomeTaxInfo(new FileInputStream(ELIZETE_PDF_2018.toFile())).block();
        assertNotNull(info);

        List<FontePagadora> fontes = info.getFontesPagadoras();
        assertNotNull(fontes);
        assertTrue(fontes.size() >= 3, "Deve extrair pelo menos 3 fontes pagadoras PJ");

        BigDecimal somaPrev = fontes.stream()
                .map(FontePagadora::getContribuicaoPrevOficial)
                .filter(v -> v != null)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        assertEquals(new BigDecimal("44.73"), somaPrev, "Soma contr. prev. oficial das fontes PJ");

        FontePagadora wiz = fontes.stream()
                .filter(f -> f.getNome() != null && f.getNome().toUpperCase().contains("WIZ"))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Fonte WIZ não encontrada"));
        assertEquals(new BigDecimal("44.73"), wiz.getContribuicaoPrevOficial());
    }

    @Test
    void mapperESimulacaoUsamSomaPrevOficialDasFontes() throws Exception {
        Assumptions.assumeTrue(Files.exists(ELIZETE_PDF_2018));

        ITextIncomeTaxServiceImpl service = new ITextIncomeTaxServiceImpl();
        IncomeTaxInfo info = service.extractIncomeTaxInfo(new FileInputStream(ELIZETE_PDF_2018.toFile())).block();
        assertNotNull(info);

        IrpfDeclaracaoData data = new IrpfDeclaracaoDataMapper().fromIncomeTaxInfo(info);
        assertEquals(new BigDecimal("44.73"), data.getContribuicaoPrevidenciaSocial());

        ExcelIrpfSimulacaoMapper excelMapper = new ExcelIrpfSimulacaoMapper(new IrPagamentosDeducaoAggregator());
        SimuladorIrpfRequest req = excelMapper.fromDeclaracao(data, BigDecimal.ZERO);
        assertEquals(new BigDecimal("44.73"), req.getPrevidenciaOficial());
    }

    @Test
    void resolverSomaMultiplasFontes() {
        var fontes = List.of(
                IrpfDeclaracaoData.FontePagadoraIrpf.builder()
                        .nome("EMPRESA A").contrPrevOficial(BigDecimal.ZERO).build(),
                IrpfDeclaracaoData.FontePagadoraIrpf.builder()
                        .nome("WIZ").contrPrevOficial(new BigDecimal("44.73")).build());

        BigDecimal resolvido = IrpfPrevidenciaOficialResolver.resolver(fontes, null);
        assertEquals(new BigDecimal("44.73"), resolvido);
    }
}
