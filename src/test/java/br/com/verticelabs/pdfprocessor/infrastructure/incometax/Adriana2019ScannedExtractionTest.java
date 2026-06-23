package br.com.verticelabs.pdfprocessor.infrastructure.incometax;

import br.com.verticelabs.pdfprocessor.domain.service.PdfService;
import br.com.verticelabs.pdfprocessor.infrastructure.pdf.PdfServiceImpl;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.io.FileInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Adriana 2019 — PDF digitalizado (CAIXA); iText extrai ~211 chars, sem RESUMO.
 */
class Adriana2019ScannedExtractionTest {

    private static final Path ADRIANA_PDF_2019 = Paths.get(
            "temp/PASTADOCUMENTOSDESSIMULADORIRPF - Rogerio Rodrigues",
            "ADRIANA MARIA QUEIROZ FREITAS - APCEF MG TEM DEDUÇÕES DE INCENTIVO(DOAÇÕES)",
            "2019",
            "DeclaraçãodeImpostodeRenda_CAIXA_2019.pdf");

    @Test
    void pdfDigitalizadoTemTextoInsuficienteParaIText() throws Exception {
        Assumptions.assumeTrue(Files.exists(ADRIANA_PDF_2019));

        PdfService pdfService = new PdfServiceImpl();
        int pages = pdfService.getTotalPages(new FileInputStream(ADRIANA_PDF_2019.toFile())).block();
        int totalChars = 0;
        for (int p = 1; p <= pages; p++) {
            String text = pdfService.extractTextFromPage(new FileInputStream(ADRIANA_PDF_2019.toFile()), p).block();
            totalChars += text != null ? text.length() : 0;
        }
        assertTrue(totalChars < 500, "PDF digitalizado deve ter pouco texto embutido, obteve " + totalChars);

        ITextIncomeTaxServiceImpl service = new ITextIncomeTaxServiceImpl();
        try {
            service.extractIncomeTaxInfo(new FileInputStream(ADRIANA_PDF_2019.toFile())).block();
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("RESUMO"));
        }
    }
}
