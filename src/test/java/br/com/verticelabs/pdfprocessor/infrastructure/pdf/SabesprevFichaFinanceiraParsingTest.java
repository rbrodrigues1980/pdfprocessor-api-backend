package br.com.verticelabs.pdfprocessor.infrastructure.pdf;

import br.com.verticelabs.pdfprocessor.domain.model.DocumentType;
import br.com.verticelabs.pdfprocessor.infrastructure.pdf.PdfLineParser.ParsedLine;
import br.com.verticelabs.pdfprocessor.infrastructure.pdf.SabesprevFichaMetadataExtractor.SabesprevFichaMetadata;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

@DisplayName("Ficha Financeira SABESPREV — detecção e matriz anual")
class SabesprevFichaFinanceiraParsingTest {

    /** Texto no formato que o PDFBox realmente extrai (código + P/D no fim). */
    private static final String FIXTURE_PDFBOX_2021 = """
            FUNDACAO SABESP DE SEGURIDADE SOCIAL - SABESPREV
            FICHA FINANCEIRA DE PAGAMENTOS
            ANO: 2021
            MATRÍCULA:
            000080792
            NOME:
            ANSELMO DA FONSECA
            APOSENTADORIA NORMAL 0,00 0,00 8727,71 3538,26 3538,26 3674,25 3674,25 3674,25 3674,25 3674,25 3674,25 41523,983674,251030 P
            CORR. MONET.
            APOSENTADORIA NORMAL
            0,00 0,00 38,18 0,00 0,00 0,00 0,00 0,00 0,00 0,00 0,00 38,180,002030 P
            CONTRIBUIÇÃO DEFICIT 0,00 0,00 120,45 48,83 48,83 50,70 50,70 50,70 50,70 50,70 50,70 573,0150,707400 D
            CONTRIBUIÇÃO DEFICIT
            ABONO
            0,00 0,00 0,00 0,00 0,00 0,00 0,00 0,00 0,00 0,00 50,70 50,700,007401 D
            DIF CONTRIBUIÇÃO
            DEFICIT PG MENOR
            0,00 0,00 0,00 0,00 0,00 0,00 0,00 0,00 472,62 0,00 0,00 472,620,007402 D
            DEV CONTRIBUIÇÃO
            DEFICIT PG MAIOR
            0,00 0,00 0,00 0,00 0,00 0,00 0,00 0,00 153,41 0,00 0,00 153,410,007404 P
            DEVOLUÇÃO
            CONTRIBUIÇÃO
            RECEBIDA A MAIOR
            0,00 0,00 0,00 0,00 0,00 0,00 0,00 0,00 605,67 0,00 0,00 605,670,009100 P
            DEVOLUÇÃO TX. ADM.
            RECEBIDA A MAIOR
            0,00 0,00 0,00 0,00 0,00 0,00 0,00 0,00 4,09 0,00 0,00 4,090,009102 P
            DESC. CONTRIB REF.
            13 SALARIO RETROATIVA
            0,00 0,00 0,00 0,00 0,00 0,00 0,00 0,00 2,96 0,00 0,00 2,960,009111 D
            TOTAIS 0,00 0,00 8644,91
            """;

    private final DocumentTypeDetectionServiceImpl typeDetection = new DocumentTypeDetectionServiceImpl();
    private final SabesprevFichaFinanceiraParser parser = new SabesprevFichaFinanceiraParser(new PdfNormalizer());

    @Test
    @DisplayName("detectType → SABESPREV_FICHA")
    void detectType_ficha() {
        assertEquals(DocumentType.SABESPREV_FICHA, typeDetection.detectType(FIXTURE_PDFBOX_2021).block());
    }

    @Test
    @DisplayName("Metadados: ano e matrícula")
    void extractMetadata() {
        Optional<SabesprevFichaMetadata> meta = SabesprevFichaMetadataExtractor.extract(FIXTURE_PDFBOX_2021);
        assertTrue(meta.isPresent());
        assertEquals("2021", meta.get().ano());
        assertEquals("000080792", meta.get().matricula());
    }

    @Test
    @DisplayName("7400: MAR–DEZ conforme ficha")
    void parse_7400() {
        List<ParsedLine> r7400 = parser.parse(FIXTURE_PDFBOX_2021, "2021").stream()
                .filter(l -> "7400".equals(l.getCodigo()))
                .toList();
        assertEquals(10, r7400.size());
        assertEquals("2021-03", r7400.get(0).getReferencia());
        assertEquals("120,45", r7400.get(0).getValorStr());
        assertEquals("2021-04", r7400.get(1).getReferencia());
        assertEquals("48,83", r7400.get(1).getValorStr());
        assertEquals("2021-12", r7400.get(9).getReferencia());
        assertEquals("50,70", r7400.get(9).getValorStr());
    }

    @Test
    @DisplayName("Códigos-alvo com valores pontuais")
    void parse_codigosAlvo() {
        List<ParsedLine> lines = parser.parse(FIXTURE_PDFBOX_2021, "2021");
        assertTrue(lines.stream().anyMatch(l ->
                "7401".equals(l.getCodigo()) && "2021-12".equals(l.getReferencia()) && "50,70".equals(l.getValorStr())));
        assertTrue(lines.stream().anyMatch(l ->
                "7402".equals(l.getCodigo()) && "2021-09".equals(l.getReferencia()) && "472,62".equals(l.getValorStr())));
        assertTrue(lines.stream().anyMatch(l ->
                "7404".equals(l.getCodigo()) && "2021-09".equals(l.getReferencia()) && "153,41".equals(l.getValorStr())));
        assertTrue(lines.stream().anyMatch(l ->
                "9100".equals(l.getCodigo()) && "2021-09".equals(l.getReferencia()) && "605,67".equals(l.getValorStr())));
        assertTrue(lines.stream().anyMatch(l ->
                "9102".equals(l.getCodigo()) && "2021-09".equals(l.getReferencia()) && "4,09".equals(l.getValorStr())));
        assertTrue(lines.stream().anyMatch(l ->
                "9111".equals(l.getCodigo()) && "2021-09".equals(l.getReferencia()) && "2,96".equals(l.getValorStr())));
    }

    @Test
    @DisplayName("PDF real Anselmo: extrai 7400 e 9100")
    void extract_pdfReal_quandoDisponivel() throws Exception {
        Path pdf = Path.of(
                "temp/PASTADOCUMENTOS/ANSELMO FONSECA - estudo de layout - novo cliente",
                "FichaFinanceiraSABESPREV.2021.ESTUDOLAYOUT.pdf");
        assumeTrue(Files.isRegularFile(pdf), "PDF de referência não está no workspace");

        PdfServiceImpl pdfService = new PdfServiceImpl();
        String text = pdfService.extractText(Files.newInputStream(pdf)).block();
        assertEquals(DocumentType.SABESPREV_FICHA, typeDetection.detectType(text).block());

        List<ParsedLine> lines = parser.parse(text, "2021");
        assertTrue(lines.stream().anyMatch(l ->
                "7400".equals(l.getCodigo()) && "2021-03".equals(l.getReferencia()) && "120,45".equals(l.getValorStr())));
        assertTrue(lines.stream().anyMatch(l ->
                "9100".equals(l.getCodigo()) && "2021-09".equals(l.getReferencia()) && "605,67".equals(l.getValorStr())));
        assertTrue(lines.stream().noneMatch(l -> "2999".equals(l.getCodigo())));
    }
}
