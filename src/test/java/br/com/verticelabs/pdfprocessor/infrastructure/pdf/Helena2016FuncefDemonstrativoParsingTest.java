package br.com.verticelabs.pdfprocessor.infrastructure.pdf;

import br.com.verticelabs.pdfprocessor.domain.model.DocumentType;
import br.com.verticelabs.pdfprocessor.infrastructure.ai.GeminiResponseParser;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Maria Helena Monteiro Ferraz 2016: demonstrativo FUNCEF com código de 6 dígitos
 * e OCR (PAGAMENTC, 1312016, prazo "o").
 */
@DisplayName("FUNCEF demonstrativo Helena 2016")
class Helena2016FuncefDemonstrativoParsingTest {

    private static final Path PDF = Path.of(
            "temp/PASTADOCUMENTOS/Maria Helena Monteiro Ferraz - APCEF PI nâo faz leitura dos contracheques 2016 - Rogério/ContraCheques_CAIXA_2016.pdf");
    private static final Path PDF_COPY = Path.of("temp/_helena/contracheques_2016.pdf");

    /**
     * Recorte real do PDF 2016 (OCR): título PAGAMENTC, competência colada e prazo "o".
     */
    private static final String PAGINA_OCR = """
            FUNCEF
            FUNDACAO DOS ECONOMIARIOS FEDERAIS FUNCEF
            DEMONSTRATIVO DE PAGAMENTC
            PATROCINADORA PLANO
            CAIXA REG/REPLAN
            TITULAR Més Pagto
            MARIA HELENA MONTEIRO FERRAZ 2016/04
            MATRICULA INSCRIGAO N° Benef. INSS
            6830814 6830814 1526793595
            PROVENTO
            Més Ref. Data Inicio Cédigo Descrigdo Valor Residuo Prazo
            02/2016 103304 AC. SUPL. APOS. TEMPO CONTRIB. BENEF. SALD. 3.332,57 0,00 0
            DESCONTO
            02/2016 336204 REP. TAXA ADMINISTRATIVA - SALDADO 29,99 0,00 0
            02/2016 336204 REP. TAXA ADMINISTRATIVA - SALDADO 765,40 0,00 o
            04/2016 436204 TAXA ADMINISTRATIVA - SALDADO 59,99 0,00 0
            04/2016 443004 CONTRIBUIGAO EXTRAORDINARIA 2014 185,29 0,00 0
            04/2016 443204 EMPREST. NOVO CREDINAMICO FIXO - FGQC 46,46 0,00 0
            1312016 436404 TAXA ADM. AB. ANUAL FUNCEF - SALDADO 27,49 0,00 0
            052016 436204 TAXA ADMINISTRATIVA - SALDADO 59,99 0,00 0
            1312016 445904 CONT. EXTRAORDINARIA ABONO ANUAL - 2014 169,85 0,00 0
            05/2016 478016 SIND. EMPREG./MENSALIDADE - PI 19,00 0,00 o
            """;

    private final PdfLineParser parser = new PdfLineParser(new PdfNormalizer());
    private final DocumentTypeDetectionServiceImpl detector = new DocumentTypeDetectionServiceImpl();

    @Test
    @DisplayName("OCR PAGAMENTC é classificado como FUNCEF_DEMONSTRATIVO")
    void detectaDemonstrativoComTituloOcr() {
        DocumentType type = detector.detectType(PAGINA_OCR).block();
        assertEquals(DocumentType.FUNCEF_DEMONSTRATIVO, type);
    }

    @Test
    @DisplayName("extrai 4 dígitos e competências OCR (3362, 4362, 4430, 4364, 4459)")
    void extraiRubricasCadastradasDoOcr() {
        List<PdfLineParser.ParsedLine> parsed = parser.parseLines(PAGINA_OCR, DocumentType.FUNCEF_DEMONSTRATIVO);

        assertEquals("3362", first(parsed, "3362").getCodigo());
        assertEquals("29,99", first(parsed, "3362").getValorStr());
        assertEquals("02/2016", first(parsed, "3362").getReferencia());

        assertTrue(parsed.stream().anyMatch(p -> "4362".equals(p.getCodigo()) && "04/2016".equals(p.getReferencia())));
        assertTrue(parsed.stream().anyMatch(p -> "4362".equals(p.getCodigo()) && "05/2016".equals(p.getReferencia())));

        assertEquals("4430", first(parsed, "4430").getCodigo());
        assertEquals("185,29", first(parsed, "4430").getValorStr());

        assertEquals("4364", first(parsed, "4364").getCodigo());
        assertEquals("13/2016", first(parsed, "4364").getReferencia());
        assertEquals("27,49", first(parsed, "4364").getValorStr());

        assertEquals("4459", first(parsed, "4459").getCodigo());
        assertEquals("13/2016", first(parsed, "4459").getReferencia());
        assertEquals("169,85", first(parsed, "4459").getValorStr());

        assertEquals("4432", first(parsed, "4432").getCodigo());
        assertTrue(first(parsed, "4432").getDescricao().toUpperCase().contains("EMPREST"));
    }

    @Test
    @DisplayName("se classificado como FUNCEF clássico, ainda parseia o demonstrativo")
    void fallbackFuncefClassicoParaDemonstrativo() {
        List<PdfLineParser.ParsedLine> parsed = parser.parseLinesFuncef(PAGINA_OCR, "2016/04");
        assertTrue(parsed.stream().anyMatch(p -> "4362".equals(p.getCodigo())));
        assertTrue(parsed.stream().anyMatch(p -> "4459".equals(p.getCodigo())));
    }

    @Test
    @DisplayName("Gemini com código de 6 dígitos reduz para a rubrica de 4")
    void geminiReduzCodigoDeSeisDigitos() {
        String json = """
                {
                  "nome": "MARIA HELENA MONTEIRO FERRAZ",
                  "competencia": "04/2016",
                  "rubricas": [
                    {"codigo": "436204", "descricao": "TAXA ADMINISTRATIVA - SALDADO", "desconto": 59.99},
                    {"codigo": "445904", "descricao": "CONT. EXTRAORDINARIA ABONO ANUAL - 2014", "desconto": 169.85}
                  ]
                }
                """;
        GeminiResponseParser.ParsedPayrollData data = GeminiResponseParser.parsePayrollResponse(
                json, "doc", "tenant", "FUNCEF_DEMONSTRATIVO", 1);
        assertEquals("4362", data.getEntries().get(0).getRubricaCodigo());
        assertEquals("4459", data.getEntries().get(1).getRubricaCodigo());
    }

    @Test
    @DisplayName("PDF real (se presente): páginas 2016 extraem 4362/4430")
    void pdfRealExtraiRubricasCadastradas() throws Exception {
        Path pdf = Files.isRegularFile(PDF) ? PDF : PDF_COPY;
        assumeTrue(Files.isRegularFile(pdf), "PDF Helena 2016 não disponível: " + pdf.toAbsolutePath());

        PdfNormalizer normalizer = new PdfNormalizer();
        boolean found4362 = false;
        boolean found4430 = false;
        boolean found4459 = false;

        try (PDDocument doc = Loader.loadPDF(pdf.toFile())) {
            PDFTextStripper stripper = new PDFTextStripper();
            for (int page = 1; page <= doc.getNumberOfPages(); page++) {
                stripper.setStartPage(page);
                stripper.setEndPage(page);
                String pageText = stripper.getText(doc);
                DocumentType type = detector.detectType(pageText).block();
                assertEquals(DocumentType.FUNCEF_DEMONSTRATIVO, type,
                        "página " + page + " deveria ser FUNCEF_DEMONSTRATIVO");

                List<PdfLineParser.ParsedLine> parsed = parser.parseLines(pageText, DocumentType.FUNCEF_DEMONSTRATIVO);
                found4362 |= parsed.stream().anyMatch(p -> "4362".equals(p.getCodigo()));
                found4430 |= parsed.stream().anyMatch(p -> "4430".equals(p.getCodigo()));
                found4459 |= parsed.stream().anyMatch(p -> "4459".equals(p.getCodigo()));

                Optional<PdfLineParser.ParsedLine> taxa = parsed.stream()
                        .filter(p -> "4362".equals(p.getCodigo()))
                        .findFirst();
                if (taxa.isPresent()) {
                    assertEquals(0, normalizer.normalizeValue(taxa.get().getValorStr())
                            .compareTo(new java.math.BigDecimal("59.99")));
                }
            }
        }

        assertTrue(found4362, "PDF não extraiu 4362 (TAXA ADMINISTRATIVA - SALDADO)");
        assertTrue(found4430, "PDF não extraiu 4430 (CONTRIBUIÇÃO EXTRAORDINÁRIA 2014)");
        assertTrue(found4459, "PDF não extraiu 4459 (abono anual 2014)");
    }

    private static PdfLineParser.ParsedLine first(List<PdfLineParser.ParsedLine> parsed, String codigo) {
        return parsed.stream()
                .filter(p -> codigo.equals(p.getCodigo()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Rubrica " + codigo + " não encontrada. Parsed=" + parsed));
    }
}
