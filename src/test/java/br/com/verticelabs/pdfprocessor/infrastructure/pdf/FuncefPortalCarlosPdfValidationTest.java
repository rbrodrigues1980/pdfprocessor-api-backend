package br.com.verticelabs.pdfprocessor.infrastructure.pdf;

import br.com.verticelabs.pdfprocessor.domain.model.DocumentType;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Validação opcional contra o PDF agrupado Carlos (temp/). Skip se arquivo ausente.
 */
class FuncefPortalCarlosPdfValidationTest {

    private static final Path PDF = Path.of(
            "temp/20260722_111149_APCEF_SE_CONTRACHEQUE_AGRUPADO_FUNCEF_10310754534_CARLOS_AUGUSTO_ALVES_DE_SOUZA.pdf");

    @Test
    @DisplayName("PDF Carlos: meses header + 2017/11 completo (2033 + 4776)")
    void validaPdfCarlos() throws Exception {
        Assumptions.assumeTrue(Files.exists(PDF), "PDF Carlos não encontrado em temp/");

        PdfLineParser parser = new PdfLineParser(new PdfNormalizer());
        DocumentTypeDetectionServiceImpl typeDetection = new DocumentTypeDetectionServiceImpl();
        MonthYearDetectionServiceImpl monthYear = new MonthYearDetectionServiceImpl();

        Set<String> mesesHeader = new HashSet<>();
        int totalRubricas = 0;
        String valor2033 = null;
        String valor4776 = null;

        try (PDDocument doc = Loader.loadPDF(PDF.toFile())) {
            String firstPage;
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setStartPage(1);
            stripper.setEndPage(1);
            firstPage = stripper.getText(doc);
            assertEquals(DocumentType.FUNCEF, typeDetection.detectType(firstPage).block());

            int pages = doc.getNumberOfPages();
            for (int p = 1; p <= pages; p++) {
                stripper.setStartPage(p);
                stripper.setEndPage(p);
                String text = stripper.getText(doc);

                monthYear.detectMonthYear(text).block().ifPresent(mesesHeader::add);

                List<PdfLineParser.ParsedLine> parsed = parser.parseLinesFuncef(text, null);
                totalRubricas += parsed.size();

                if (p == 14) {
                    valor2033 = parsed.stream()
                            .filter(l -> "2033".equals(l.getCodigo()))
                            .map(PdfLineParser.ParsedLine::getValorStr)
                            .findFirst()
                            .orElse(null);
                }
                if (p == 15) {
                    valor4776 = parsed.stream()
                            .filter(l -> "4776".equals(l.getCodigo()))
                            .map(PdfLineParser.ParsedLine::getValorStr)
                            .findFirst()
                            .orElse(null);
                    assertTrue(DocumentTypeDetectionServiceImpl.looksLikeFuncefPortalContinuation(text));
                }
            }
        }

        assertTrue(mesesHeader.contains("2016-12"), "faltou 2016-12: " + mesesHeader);
        assertTrue(mesesHeader.contains("2026-01"), "faltou 2026-01: " + mesesHeader);
        assertTrue(mesesHeader.size() >= 100, "esperava >=100 meses, veio " + mesesHeader.size());
        assertEquals("11.622,86", valor2033);
        assertEquals("30,28", valor4776);
        assertTrue(totalRubricas > 500, "poucas rubricas: " + totalRubricas);
    }
}
