package br.com.verticelabs.pdfprocessor.infrastructure.pdf;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Validação opcional contra os PDFs FUNCEF da Carmen (temp/). Skip se ausentes.
 */
class FuncefCarmenPdfValidationTest {

    private static final Path DIR = Path.of(
            "temp/PASTADOCUMENTOS/CARMEN LUCIA DOMINGUES CUNHA");

    private final PdfLineParser parser = new PdfLineParser(new PdfNormalizer());

    @Test
    @DisplayName("PDF 2017: páginas de aposentadoria (mai+) extraem 4309")
    void contracheques2017Extrai4309() throws Exception {
        Path pdf = DIR.resolve("ContraCheques_APCEF DF_2017.pdf");
        Assumptions.assumeTrue(Files.exists(pdf), "PDF Carmen 2017 não encontrado");

        int paginasCom4309 = 0;
        try (PDDocument doc = Loader.loadPDF(pdf.toFile())) {
            PDFTextStripper stripper = new PDFTextStripper();
            for (int p = 1; p <= doc.getNumberOfPages(); p++) {
                stripper.setStartPage(p);
                stripper.setEndPage(p);
                String text = stripper.getText(doc);
                if (!text.contains("FUNCEF CONTRIBUICAO") || !text.contains("4 309")) {
                    continue;
                }
                List<PdfLineParser.ParsedLine> parsed = parser.parseLinesFuncef(text, null);
                boolean tem = parsed.stream().anyMatch(l -> "4309".equals(l.getCodigo()));
                assertTrue(tem, "p" + p + " deveria extrair 4309; got=" + parsed);
                paginasCom4309++;
            }
        }
        assertTrue(paginasCom4309 >= 8, "esperado >=8 páginas com 4309; foi=" + paginasCom4309);
    }

    @Test
    @DisplayName("PDF 2024: extrai 4309 mensal e 4310 abono")
    void contracheques2024Extrai4309e4310() throws Exception {
        Path pdf = DIR.resolve("ContraCheques_APCEF DF_2024.pdf");
        Assumptions.assumeTrue(Files.exists(pdf), "PDF Carmen 2024 não encontrado");

        int com4309 = 0;
        int com4310 = 0;
        try (PDDocument doc = Loader.loadPDF(pdf.toFile())) {
            PDFTextStripper stripper = new PDFTextStripper();
            for (int p = 1; p <= doc.getNumberOfPages(); p++) {
                stripper.setStartPage(p);
                stripper.setEndPage(p);
                String text = stripper.getText(doc);
                List<PdfLineParser.ParsedLine> parsed = parser.parseLinesFuncef(text, null);
                if (parsed.stream().anyMatch(l -> "4309".equals(l.getCodigo()))) {
                    com4309++;
                }
                if (parsed.stream().anyMatch(l -> "4310".equals(l.getCodigo()))) {
                    com4310++;
                }
            }
        }
        assertTrue(com4309 >= 12, "esperado 4309 em 12 meses; foi=" + com4309);
        assertTrue(com4310 >= 2, "esperado 4310 em abonos; foi=" + com4310);
    }
}
