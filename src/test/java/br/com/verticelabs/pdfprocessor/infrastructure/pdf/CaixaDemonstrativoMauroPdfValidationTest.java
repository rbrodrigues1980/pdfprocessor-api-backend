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
 * Validação opcional contra os PDFs CAIXA do Mauro (temp/). Skip se ausentes.
 */
class CaixaDemonstrativoMauroPdfValidationTest {

    private static final Path DIR = Path.of(
            "temp/PASTADOCUMENTOS/Mauro Antonio Costa de Mendonca - APCEF BA - ContraChequeCaixa");

    private static final Pattern RUBRICA_LINE = Pattern.compile(
            "^\\d{4,5}\\s.*R\\$\\s*[0-9]{1,3}(?:\\.[0-9]{3})*,\\d{2}\\s*$");

    @ParameterizedTest
    @ValueSource(strings = {
            "ContraCheques_CAIXA_2016.pdf",
            "ContraCheques_CAIXA_2017.pdf",
            "ContraCheques_CAIXA_2025.pdf",
            "ContraCheques_CAIXA_2026.pdf"})
    @DisplayName("PDF Mauro: páginas de demonstrativo saem CAIXA, com mês e todas as rubricas")
    void validaPdfMauro(String arquivo) throws Exception {
        Path pdf = DIR.resolve(arquivo);
        Assumptions.assumeTrue(Files.exists(pdf), "PDF Mauro não encontrado: " + pdf);

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

                if (!text.toUpperCase().contains("DEMONSTRATIVO DE PAGAMENTO")) {
                    if (DocumentTypeDetectionServiceImpl.looksLikePayslipPage(text)) {
                        falhas.add("p" + p + ": página sem demonstrativo marcada como contracheque");
                    }
                    continue;
                }

                DocumentType type = typeDetection.detectType(text).block();
                if (type != DocumentType.CAIXA) {
                    falhas.add("p" + p + ": tipo " + type);
                }
                Optional<String> my = monthYear.detectMonthYear(text).block();
                if (my == null || my.isEmpty()) {
                    falhas.add("p" + p + ": mês/ano não detectado");
                }

                long esperadas = text.lines().map(String::trim)
                        .filter(l -> RUBRICA_LINE.matcher(l).matches()).count();
                List<PdfLineParser.ParsedLine> parsed = parser.parseLines(text, DocumentType.CAIXA);
                totalRubricas += parsed.size();
                if (parsed.size() != esperadas) {
                    falhas.add("p" + p + ": esperadas " + esperadas + ", extraídas " + parsed.size());
                }
                for (PdfLineParser.ParsedLine l : parsed) {
                    if (l.getDescricao() == null || l.getDescricao().contains("R$") || l.getReferencia() == null) {
                        falhas.add("p" + p + ": linha suja " + l.getCodigo() + " [" + l.getDescricao() + "]");
                    }
                }
            }
        }

        assertTrue(falhas.isEmpty(), arquivo + " → " + falhas);
        assertTrue(totalRubricas > 0);
    }
}
