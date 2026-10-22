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
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Demonstrativo de Pagamento CAIXA 2016 (Helida): tipo e rubrica separados
 * ({@code 2 0002} → 2002), valor sem ponto de milhar e desconto com sinal.
 */
@DisplayName("CAIXA demonstrativo Helida 2016 — parser e detecção")
class CaixaDemonstrativoHelida2016ParsingTest {

    private static final Path HELIDA_PDF = Path.of(
            "temp/PASTADOCUMENTOS/HelidaMariaGomesdaCostaContraCheques_2016(layout).pdf");

    private final PdfLineParser parser = new PdfLineParser(new PdfNormalizer());
    private final DocumentTypeDetectionServiceImpl typeDetection = new DocumentTypeDetectionServiceImpl();
    private final MonthYearDetectionServiceImpl monthYearDetection = new MonthYearDetectionServiceImpl();

    private static final String PAGINA_2016_02 = """
            DEMONSTRATIVO DE PAGAMENTO
            Mês/Ano de Pagamento
            fevereiro/2016
            Tipo / Rubrica Discriminação da Rubrica Moeda Valor
            2 0002 SALARIO PADRAO R$ 7101,00
            2 0007 ADICIONAL TEMPO DE SERVICO R$ 2201,31
            2 0049 VP-GRAT SEM/ ADIC TEMPO SER R$ 366,88
            2 0068 ADIANT GRATIFICACAO NATAL R$ 6385,99
            2 0116 ADICIONAL INCORPORACAO R$ 3102,79
            4 0313 INSS CONTRIBUICAO R$ -570,88
            4 0325 IMPOSTO DE RENDA R$ -2099,59
            4 0335 CEF - CONSIGNACOES R$ -740,13
            4 0346 FUNCEF - NOVO PLANO R$ -1404,91
            4 0369 FUNCEF - GRAT NATAL NOVO PL R$ -702,45
            4 0410 SINDICATO MENSALIDADE R$ -121,74
            4 0456 SAUDE CAIXA - ADIANTAMENTO R$ -130,00
            4 0460 SAUDE CAIXA - MENSALIDADE R$ -255,44
            4 0960 MENSALIDADE DEPENDENTE INDI R$ -220,00
            Salário Bruto Valor Descontos Salário Líquido
            19157,97 -6245,14 12912,83
            """;

    @Test
    @DisplayName("fevereiro/2016: CAIXA, código 2002 e desconto 4313 sem sinal")
    void paginaFevereiro2016() {
        assertEquals(DocumentType.CAIXA, typeDetection.detectType(PAGINA_2016_02).block());
        assertEquals("2016-02", monthYearDetection.detectMonthYear(PAGINA_2016_02).block().orElseThrow());

        List<PdfLineParser.ParsedLine> parsed = parser.parseLines(PAGINA_2016_02, DocumentType.CAIXA);
        assertEquals(14, parsed.size());

        PdfLineParser.ParsedLine salario = byCodigo(parsed, "2002").get(0);
        assertEquals("SALARIO PADRAO", salario.getDescricao());
        assertEquals("7101,00", salario.getValorStr());
        assertNull(salario.getReferencia());

        PdfLineParser.ParsedLine inss = byCodigo(parsed, "4313").get(0);
        assertEquals("INSS CONTRIBUICAO", inss.getDescricao());
        assertEquals("570,88", inss.getValorStr());

        assertTrue(parsed.stream().noneMatch(l -> l.getDescricao() != null && l.getDescricao().contains("R$")));
        assertTrue(parsed.stream().noneMatch(l -> l.getValorStr() != null && l.getValorStr().startsWith("-")));
        assertTrue(parsed.stream().noneMatch(l -> "20002".equals(l.getCodigo()) || "40313".equals(l.getCodigo())));
    }

    @Test
    @DisplayName("PDF Helida 2016: página de fevereiro traz 2002 e 4313")
    void pdfFevereiro2016() throws Exception {
        Assumptions.assumeTrue(Files.isRegularFile(HELIDA_PDF),
                "PDF Helida 2016 não encontrado: " + HELIDA_PDF.toAbsolutePath());

        try (PDDocument doc = Loader.loadPDF(HELIDA_PDF.toFile())) {
            PDFTextStripper stripper = new PDFTextStripper();
            String fevereiro = null;
            for (int p = 1; p <= doc.getNumberOfPages(); p++) {
                stripper.setStartPage(p);
                stripper.setEndPage(p);
                String text = stripper.getText(doc);
                if (text.toLowerCase().contains("fevereiro/2016")
                        && text.toUpperCase().contains("DEMONSTRATIVO DE PAGAMENTO")) {
                    fevereiro = text;
                    break;
                }
            }
            Assumptions.assumeTrue(fevereiro != null, "Página fevereiro/2016 não encontrada");

            assertEquals(DocumentType.CAIXA, typeDetection.detectType(fevereiro).block());
            assertEquals("2016-02", monthYearDetection.detectMonthYear(fevereiro).block().orElseThrow());

            List<PdfLineParser.ParsedLine> parsed = parser.parseLines(fevereiro, DocumentType.CAIXA);
            long linhasRubrica = fevereiro.lines().map(String::trim)
                    .filter(l -> l.matches("\\d\\s+0\\d{3}\\s+.*")).count();
            assertEquals(linhasRubrica, parsed.size(), "extraídas=" + parsed.size());

            assertEquals("7101,00", byCodigo(parsed, "2002").get(0).getValorStr());
            assertEquals("570,88", byCodigo(parsed, "4313").get(0).getValorStr());
            assertTrue(parsed.stream().noneMatch(l -> l.getValorStr() != null && l.getValorStr().startsWith("-")));
        }
    }

    private static List<PdfLineParser.ParsedLine> byCodigo(List<PdfLineParser.ParsedLine> parsed, String codigo) {
        return parsed.stream().filter(l -> codigo.equals(l.getCodigo())).toList();
    }
}
