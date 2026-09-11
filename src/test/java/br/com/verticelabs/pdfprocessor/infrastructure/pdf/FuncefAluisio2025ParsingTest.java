package br.com.verticelabs.pdfprocessor.infrastructure.pdf;

import br.com.verticelabs.pdfprocessor.domain.model.DocumentType;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Assumptions;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Regressão Aluísio 2025/05: rubrica 4534 (CONTRIBUIÇÃO EXTRAORDINARIA) deve ser extraída pelo regex.
 */
@DisplayName("FUNCEF Aluísio 2025 — rubrica 4534")
class FuncefAluisio2025ParsingTest {

    private static final Path PDF = Path.of(
            "temp/PASTADOCUMENTOS/ALUISIO JOSE DE PAIVA MARTINS - AEAP PE/2025/ContraCheques_2025.pdf");

    private static final String PAGINA_MAIO_2025 = """
            FUNCEF - Fundação dos Economiários Federais
            Ano Pagamento / Mês
            2025/05
            Nº Benefício INSS:
            1208529002
            DEMONSTRATIVO DE PROVENTOS PREVIDENCIÁRIOS
            Nome:
            ALUISIO JOSE DE PAIVA MARTINS
            Matrícula:
            0148122
            CPF:
            03018822404
            Tipo / Rubrica Referência Discriminação Prazo Valor
            2 187 2025/05 PROVENTOS INSS 6.717,63
            2 033 2025/05 SUPL. APOS. TEMPO CONTRIB. BENEF. SALD. 13.416,67
            2 222 2025/13 ABONO ANUAL INSS 6.717,63
            4 328 2025/05 IMPOSTO RENDA FONTE (INSS) 364,92
            4 327 2025/05 IMPOSTO RENDA FONTE (FUNCEF) 1.786,36
            4 698 2025/05 DIFERENÇA IR TOTAL 1.482,42
            4 428 2025/13 IR ABONO ANUAL INSS 364,92
            3 215 2025/13 REP. ADIANT. ABONO ANUAL INSS 3.358,81
            4 362 2025/05 TAXA ADMINISTRATIVA - SALDADO 73,79
            4 635 2025/05 FENACEF - SAUDE 4.636,00
            4 729 2025/05 UNEICEF - MENSALIDADE 75,00
            4 776 2025/05 ASSOC. APOSENT./MENSALIDADE - PE 49,00
            4 534 2025/05 CONTRIBUIÇÃO EXTRAORDINARIA 1.449,00
            Bruto
            26.851,93
            Descontos
            13.640,22
            Líquido
            13.211,71
            """;

    private final PdfLineParser parser = new PdfLineParser(new PdfNormalizer());

    @Test
    @DisplayName("fixture texto: extrai 4534 = 1.449,00 em 2025/05")
    void deveExtrair4534DoTextoMaio2025() {
        DocumentType type = new DocumentTypeDetectionServiceImpl().detectType(PAGINA_MAIO_2025).block();
        assertEquals(DocumentType.FUNCEF, type);

        List<PdfLineParser.ParsedLine> parsed = parser.parseLinesFuncef(PAGINA_MAIO_2025, "2025/05");

        Optional<PdfLineParser.ParsedLine> r4534 = parsed.stream()
                .filter(p -> "4534".equals(p.getCodigo()))
                .findFirst();

        assertTrue(r4534.isPresent(), "Rubrica 4534 não encontrada. Parsed=" + parsed);
        assertEquals("2025/05", r4534.get().getReferencia());
        assertEquals("1.449,00", r4534.get().getValorStr());
        assertTrue(r4534.get().getDescricao().toUpperCase().contains("CONTRIBUIÇÃO EXTRAORDINARIA")
                || r4534.get().getDescricao().toUpperCase().contains("CONTRIBUICAO EXTRAORDINARIA"));
    }

    @Test
    @DisplayName("PDF real (se presente): página 2025/05 contém 4534 parseável")
    void deveExtrair4534DoPdfRealMaio2025() throws Exception {
        Assumptions.assumeTrue(Files.isRegularFile(PDF), "PDF Aluísio 2025 não disponível: " + PDF.toAbsolutePath());

        try (PDDocument doc = Loader.loadPDF(PDF.toFile())) {
            PDFTextStripper stripper = new PDFTextStripper();
            boolean found = false;
            for (int page = 1; page <= doc.getNumberOfPages(); page++) {
                stripper.setStartPage(page);
                stripper.setEndPage(page);
                String pageText = stripper.getText(doc);
                if (!pageText.contains("2025/05") || !pageText.contains("4534") && !pageText.contains("4 534")) {
                    continue;
                }
                List<PdfLineParser.ParsedLine> parsed = parser.parseLinesFuncef(pageText, "2025/05");
                Optional<PdfLineParser.ParsedLine> r4534 = parsed.stream()
                        .filter(p -> "4534".equals(p.getCodigo()))
                        .findFirst();
                if (r4534.isPresent()) {
                    found = true;
                    assertEquals("1.449,00", r4534.get().getValorStr());
                    BigDecimal valor = new BigDecimal(
                            r4534.get().getValorStr().replace(".", "").replace(",", "."));
                    assertEquals(0, valor.compareTo(new BigDecimal("1449.00")));
                    break;
                }
            }
            assertTrue(found, "Nenhuma página do PDF extraiu 4534=1449,00 para 2025/05");
        }
    }
}
