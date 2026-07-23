package br.com.verticelabs.pdfprocessor.application.incometax;

import br.com.verticelabs.pdfprocessor.domain.model.IrpfDeclaracaoData;
import br.com.verticelabs.pdfprocessor.domain.service.IncomeTaxDeclarationService.IncomeTaxInfo;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

@DisplayName("IrpfDeclaracaoDataMapper — dedução de dependentes")
class IrpfDeclaracaoDataMapperTest {

    private final IrpfDeclaracaoDataMapper mapper = new IrpfDeclaracaoDataMapper();

    @Test
    @DisplayName("Gemini: totalDeducaoDependentes null → usa deducoesDependentes do RESUMO")
    void geminiUsaDeducoesDependentesDoResumo() {
        IncomeTaxInfo info = minimalInfo(
                null,
                new BigDecimal("4550.16"));

        IrpfDeclaracaoData data = mapper.fromIncomeTaxInfo(info);

        assertEquals(0, new BigDecimal("4550.16").compareTo(data.getDeducaoDependentes()));
        assertEquals(0, new BigDecimal("4550.16").compareTo(data.getTotalDeducaoDependentes()));
    }

    @Test
    @DisplayName("iText: total da página 1 prevalece sobre RESUMO")
    void totalPagina1PrevaleceSobreResumo() {
        IncomeTaxInfo info = minimalInfo(
                new BigDecimal("4550.16"),
                new BigDecimal("9999.99"));

        IrpfDeclaracaoData data = mapper.fromIncomeTaxInfo(info);

        assertEquals(0, new BigDecimal("4550.16").compareTo(data.getDeducaoDependentes()));
    }

    @Test
    @DisplayName("sem nenhum valor → null")
    void semValorFicaNull() {
        IncomeTaxInfo info = minimalInfo(null, null);
        IrpfDeclaracaoData data = mapper.fromIncomeTaxInfo(info);
        assertNull(data.getDeducaoDependentes());
    }

    @Test
    @DisplayName("impostoDevidoRRA → impostoSobreRRA")
    void mapeiaImpostoDevidoRRA() {
        IncomeTaxInfo info = new IncomeTaxInfo(
                "NADELSON LIMA DE CARVALHO", "000.000.000-00", "2022", "2023",
                new BigDecimal("227615.37"), new BigDecimal("52161.90"), BigDecimal.ZERO,
                new BigDecimal("52161.90"),
                null, null, new BigDecimal("28033.36"),
                new BigDecimal("80195.26"), null,
                new BigDecimal("300000.00"), null,
                null, null, null,
                null, null, null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null,
                null, null,
                "COMPLETO", null, null, null, null,
                null, null, null, null,
                null, null,
                Collections.emptyList(), Collections.emptyList(),
                null, Collections.emptyList(), null, Collections.emptyList(),
                null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null, null, null, null,
                Collections.emptyList());

        IrpfDeclaracaoData data = mapper.fromIncomeTaxInfo(info);

        assertEquals(0, new BigDecimal("28033.36").compareTo(data.getImpostoSobreRRA()));
        assertEquals(0, new BigDecimal("80195.26").compareTo(data.getTotalImpostoDevido()));
    }

    private static IncomeTaxInfo minimalInfo(BigDecimal totalPagina1, BigDecimal resumoDependentes) {
        return new IncomeTaxInfo(
                "MARIA TERESA BADDINI", "596.545.387-68", "2016", "2017",
                null, null, null, null,
                null, null, null,
                null, null,
                new BigDecimal("100000.00"), null,
                null, null, null,
                null, null, null, resumoDependentes, null,
                null, null, null, null, null,
                null, null, null, null, null, null, null,
                null, null,
                "COMPLETO", null, null, null, null,
                null, null, null, null,
                null, null,
                Collections.emptyList(), Collections.emptyList(),
                null, Collections.emptyList(), totalPagina1, Collections.emptyList(),
                null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null, null, null, null,
                Collections.emptyList());
    }
}
