package br.com.verticelabs.pdfprocessor.infrastructure.pdf;

import br.com.verticelabs.pdfprocessor.domain.model.DocumentType;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Validação opcional contra os PDFs do portal Funcef da Rita (temp/). Skip se ausentes.
 */
class FuncefPortalRitaPdfValidationTest {

    private static final Path DIR = Path.of(
            "temp/PASTADOCUMENTOS/Rita de Cassia Lomba Pinto - APCEF BA Outo Layout FUNCEF a partir de 2020");

    private static final Pattern RUBRICA_LINE = Pattern.compile("(?m)^\\s*\\d\\s+\\d{3}\\s+\\d{4}/\\d{1,2}\\b");
    private static final Pattern HEADER_PORTAL = Pattern.compile("(?i)TIPO\\s*/\\s*RUBRICA");

    @ParameterizedTest
    @ValueSource(strings = {
            "ContraCheques_CAIXA_2016.pdf",
            "ContraCheques_CAIXA_2017.pdf",
            "ContraCheques_CAIXA_2025.pdf"})
    @DisplayName("PDF Rita: cada página detecta FUNCEF, mês/ano e extrai todas as linhas de rubrica")
    void validaPdfRita(String arquivo) throws Exception {
        Path pdf = DIR.resolve(arquivo);
        Assumptions.assumeTrue(Files.exists(pdf), "PDF Rita não encontrado: " + pdf);

        PdfLineParser parser = new PdfLineParser(new PdfNormalizer());
        DocumentTypeDetectionServiceImpl typeDetection = new DocumentTypeDetectionServiceImpl();
        MonthYearDetectionServiceImpl monthYear = new MonthYearDetectionServiceImpl();

        List<String> falhas = new ArrayList<>();
        int totalRubricas = 0;

        try (PDDocument doc = Loader.loadPDF(pdf.toFile())) {
            PDFTextStripper stripper = new PDFTextStripper();
            for (int p = 1; p <= doc.getNumberOfPages(); p++) {
                stripper.setStartPage(p);
                stripper.setEndPage(p);
                String text = stripper.getText(doc);

                if (HEADER_PORTAL.matcher(text).find()) {
                    DocumentType type = typeDetection.detectType(text).block();
                    if (type != DocumentType.FUNCEF) {
                        falhas.add("p" + p + ": tipo " + type);
                    }
                    Optional<String> my = monthYear.detectMonthYear(text).block();
                    if (my == null || my.isEmpty()) {
                        falhas.add("p" + p + ": mês/ano não detectado");
                    }
                }

                long esperadas = RUBRICA_LINE.matcher(text).results().count();
                int extraidas = parser.parseLinesFuncef(text, null).size();
                totalRubricas += extraidas;
                if (extraidas != esperadas) {
                    falhas.add("p" + p + ": esperadas " + esperadas + ", extraídas " + extraidas);
                }
            }
        }

        assertTrue(falhas.isEmpty(), arquivo + " → " + falhas);
        assertTrue(totalRubricas > 0);
    }
}
