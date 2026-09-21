package br.com.verticelabs.pdfprocessor.infrastructure.incometax;

import br.com.verticelabs.pdfprocessor.application.incometax.IrpfDeclaracaoDataMapper;
import br.com.verticelabs.pdfprocessor.domain.model.IrpfDeclaracaoData;
import br.com.verticelabs.pdfprocessor.domain.service.IncomeTaxDeclarationService.IncomeTaxInfo;
import br.com.verticelabs.pdfprocessor.infrastructure.excel.ExcelIrpfDeducoesResumoDTO;
import br.com.verticelabs.pdfprocessor.infrastructure.excel.ExcelIrpfDeducoesResumoHelper;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.FileInputStream;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Miguel Geovane AC 2016: Base de cálculo do imposto = rendimentos − total de deduções.
 * O RESUMO em duas colunas extraía 184.878,98 (sem dependentes) em vez de 178.053,74.
 */
@DisplayName("Miguel 2016 — base de cálculo do imposto")
class Miguel2016BaseCalculoImpostoTest {

    private static final Path PDF = Paths.get("temp/_miguel/irpf_2016.pdf");
    private static final BigDecimal RENDIMENTOS = new BigDecimal("234487.34");
    private static final BigDecimal DEDUCOES = new BigDecimal("56433.60");
    private static final BigDecimal BASE_CORRETA = new BigDecimal("178053.74");

    @Test
    @DisplayName("identidade: 234.487,34 − 56.433,60 = 178.053,74")
    void identidadeRendimentosMenosDeducoes() {
        assertEquals(0, BASE_CORRETA.compareTo(
                IrpfDeclaracaoDataMapper.baseCalculoRendimentosMenosDeducoes(RENDIMENTOS, DEDUCOES)));
    }

    @Test
    @DisplayName("PDF 2016: extração + mapper geram base 178.053,74")
    void pdfEMapperUsamBaseCorreta() throws Exception {
        Assumptions.assumeTrue(Files.isRegularFile(PDF), "PDF Miguel 2016 não disponível: " + PDF.toAbsolutePath());

        ITextIncomeTaxServiceImpl service = new ITextIncomeTaxServiceImpl();
        IncomeTaxInfo info = service.extractIncomeTaxInfo(new FileInputStream(PDF.toFile())).block();

        assertEquals(0, RENDIMENTOS.compareTo(info.getRendimentosTributaveis()),
                "rendimentos extraídos=" + info.getRendimentosTributaveis());

        IrpfDeclaracaoData data = new IrpfDeclaracaoDataMapper().fromIncomeTaxInfo(info);
        assertEquals(0, RENDIMENTOS.compareTo(data.getRendimentosTributaveisTotal()));
        assertEquals(0, BASE_CORRETA.compareTo(data.getBaseCalculoImposto()),
                "base extraída/mapeada=" + data.getBaseCalculoImposto()
                        + " (deve ser rendimentos − deduções, não 184878.98)");

        ExcelIrpfDeducoesResumoHelper helper = new ExcelIrpfDeducoesResumoHelper(null, null);
        ExcelIrpfDeducoesResumoDTO deducoes = helper.montarConformeDeclaracao(data);
        assertEquals(0, DEDUCOES.compareTo(deducoes.getTotalDeducoes()),
                "total deduções da planilha=" + deducoes.getTotalDeducoes());
        assertEquals(0, BASE_CORRETA.compareTo(
                IrpfDeclaracaoDataMapper.baseCalculoRendimentosMenosDeducoes(
                        data.getRendimentosTributaveisTotal(), deducoes.getTotalDeducoes())));
    }
}
