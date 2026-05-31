package br.com.verticelabs.pdfprocessor.infrastructure.pdf;

import br.com.verticelabs.pdfprocessor.domain.model.DocumentType;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * Regressão: demonstrativo FUNCEF com texto selecionável (ex: ELIZETE 2016/01).
 */
class FuncefDemonstrativoParsingTest {

    private static final Path PDF = Path.of("docs/pdf/ContraCheques_2016.pdf");

    @Test
    void deveExtrairSeisRubricasDoDemonstrativoFuncef() throws Exception {
        String pageText;
        try (PDDocument doc = Loader.loadPDF(PDF.toFile())) {
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setStartPage(1);
            stripper.setEndPage(1);
            pageText = stripper.getText(doc);
        }

        DocumentType docType = new DocumentTypeDetectionServiceImpl().detectType(pageText).block();
        assertEquals(DocumentType.FUNCEF, docType);

        PdfLineParser parser = new PdfLineParser(new PdfNormalizer());
        List<PdfLineParser.ParsedLine> parsed = parser.parseLinesFuncef(pageText, "2016/01");

        assertEquals(6, parsed.size());
        assertEquals("2409", parsed.get(0).getCodigo());
        assertEquals("2016/01", parsed.get(0).getReferencia());
        assertEquals("5.613,99", parsed.get(0).getValorStr());
        assertEquals("BENEFICIO FUNCEF - IN1343", parsed.get(0).getDescricao());

        // Linhas com prazo: descrição não deve incluir o número de parcelas
        PdfLineParser.ParsedLine emprestimo = parsed.stream()
                .filter(p -> "4437".equals(p.getCodigo()))
                .findFirst()
                .orElseThrow();
        assertEquals("EMPREST. NOVO CREDINAMICO FIXO", emprestimo.getDescricao());
        assertFalse(emprestimo.getDescricao().endsWith("58"));
    }
}
