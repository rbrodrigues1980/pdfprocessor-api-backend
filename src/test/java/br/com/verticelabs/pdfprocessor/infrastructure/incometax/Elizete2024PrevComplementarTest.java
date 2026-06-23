package br.com.verticelabs.pdfprocessor.infrastructure.incometax;

import br.com.verticelabs.pdfprocessor.domain.service.IncomeTaxDeclarationService.IncomeTaxInfo;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.io.FileInputStream;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Elizete AC 2024 — prev. complementar 4.622,64 (label em duas linhas no PDF).
 */
class Elizete2024PrevComplementarTest {

    private static final Path ELIZETE_PDF_2024 = Paths.get(
            "temp/PASTADOCUMENTOSDESSIMULADORIRPF - Rogerio Rodrigues",
            "Elizete de Almeida Lins Santos - AEA AL",
            "2024",
            "DeclaraçãodeImpostodeRenda_2024.pdf");

    @Test
    void extraiContribuicaoPrevComplementar4622() throws Exception {
        Assumptions.assumeTrue(Files.exists(ELIZETE_PDF_2024),
                "PDF 2024 da Elizete não encontrado: " + ELIZETE_PDF_2024.toAbsolutePath());

        ITextIncomeTaxServiceImpl service = new ITextIncomeTaxServiceImpl();
        IncomeTaxInfo info = service.extractIncomeTaxInfo(new FileInputStream(ELIZETE_PDF_2024.toFile())).block();
        assertNotNull(info);

        assertEquals(new BigDecimal("4622.64"), info.getDeducoesContribPrevCompl(),
                "Contribuição à prev. complementar deve ser 4.622,64");
    }

    @Test
    void extraiImpostoDevidoSecao2024() throws Exception {
        Assumptions.assumeTrue(Files.exists(ELIZETE_PDF_2024));

        ITextIncomeTaxServiceImpl service = new ITextIncomeTaxServiceImpl();
        IncomeTaxInfo info = service.extractIncomeTaxInfo(new FileInputStream(ELIZETE_PDF_2024.toFile())).block();
        assertNotNull(info);
        assertEquals(new BigDecimal("119543.06"), info.getBaseCalculoImposto());
        assertEquals(new BigDecimal("22133.36"), info.getImpostoDevido());
        assertEquals(new BigDecimal("0.00"), info.getDeducaoIncentivo());
        assertEquals(new BigDecimal("0.00"), info.getImpostoDevidoI());
        assertEquals(new BigDecimal("22133.36"), info.getTotalImpostoDevido());
    }

    @Test
    void extraiTotalDeducoes18564() throws Exception {
        Assumptions.assumeTrue(Files.exists(ELIZETE_PDF_2024));

        ITextIncomeTaxServiceImpl service = new ITextIncomeTaxServiceImpl();
        IncomeTaxInfo info = service.extractIncomeTaxInfo(new FileInputStream(ELIZETE_PDF_2024.toFile())).block();
        assertNotNull(info);

        assertEquals(new BigDecimal("18564.56"), info.getDeducoes(),
                "Total deduções deve ser 18.564,56");
    }
}
