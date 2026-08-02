package br.com.verticelabs.pdfprocessor.infrastructure.pdf;

import br.com.verticelabs.pdfprocessor.domain.model.DocumentType;
import br.com.verticelabs.pdfprocessor.infrastructure.pdf.SabespPayslipMetadataExtractor.SabespMetadata;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

@DisplayName("Contracheque SABESP — detecção e extração")
class SabespPayslipParsingTest {

    private static final String FIXTURE_JAN_2020 = """
            DEMONSTRATIVO DE PAGAMENTO
            COMPANHIA DE SANEAMENTO BÁSICO DO ESTADO DE SÃO PAULO - SABESP
            CNPJ 43.776.517/0319-05 0200.0319 4560000000 TGA
            PERÍODO MATRÍC NOME DO EMPREGADO CART. PROF. SF IR DOC
            01/2020 00008079 ANSELMO DA FONSECA 00052717/00240 00 01 01/02
            CARGO SALÁRIO BASE REMUNERAÇÃO BASE
            TECNÓLOGO 13         10.805,51      10.805,51
            CONTA DESCRIÇÃO QTDE. VALOR UNIT. VENCIMENTOS DESCONTOS
            1000 Remuneracao Basica 30,00 360,18 10.805,51
            1171 Adic. de Tempo de Serviço 2.485,27
            /314 Contr. INSS Remuneração 11,00 671,11
            /401 Tributo IRRF 27,50 2.088,14
            /B02 Adiantamento pago 3.781,93
            3041 Desc. IRRF Depósito Judic 27,50 268,16
            3301 Contr Déf. Pl BD BS IR JD 975,13
            3302 Ret Déf Pl BD 13° BSIR JD 81,26
            3332 Vale Refeição 270,00
            3347 Supl.Apos.Sabesprev 700,39
            3349 Supl.Apos.s/13* Sabesprev 58,36
            3355 Contr.Exced.Cons.Médica 15,48
            3371 Cooper. de credito Cecres 200,00
            BANCO AGÊNCIA /
            CONTA CORRENTE
            DATA DE CRÉDITO TOTAIS                13.290,78                10.079,22
            001 5806-8 / 9257-6 31.01.2020 LÍQUIDO                 3.211,56
            """;

    private final DocumentTypeDetectionServiceImpl typeDetection = new DocumentTypeDetectionServiceImpl();
    private final PdfLineParser parser = new PdfLineParser(new PdfNormalizer());
    private final MonthYearDetectionServiceImpl monthYear = new MonthYearDetectionServiceImpl();

    @Test
    @DisplayName("detectType → SABESP")
    void detectType_sabesp() {
        DocumentType type = typeDetection.detectType(FIXTURE_JAN_2020).block();
        assertEquals(DocumentType.SABESP, type);
    }

    @Test
    @DisplayName("Não confundir com CAIXA só por DEMONSTRATIVO DE PAGAMENTO")
    void detectType_naoConfundeCaixa() {
        String caixa = """
                DEMONSTRATIVO DE PAGAMENTO
                CAIXA ECONÔMICA FEDERAL
                Mês/Ano de Pagamento JANEIRO / 2020
                Agência 2789
                Sigla GIREC
                """;
        DocumentType type = typeDetection.detectType(caixa).block();
        assertEquals(DocumentType.CAIXA, type);
    }

    @Test
    @DisplayName("Metadados: período, matrícula e nome (sem CPF no layout de referência)")
    void extractMetadata() {
        Optional<SabespMetadata> meta = SabespPayslipMetadataExtractor.extract(FIXTURE_JAN_2020);
        assertTrue(meta.isPresent());
        assertEquals("2020-01", meta.get().periodoYm());
        assertEquals("00008079", meta.get().matricula());
        assertEquals("ANSELMO DA FONSECA", meta.get().nome());
        assertEquals(null, meta.get().cpf());
    }

    @Test
    @DisplayName("Metadados: CPF opcional quando presente no texto")
    void extractMetadata_comCpf() {
        String text = FIXTURE_JAN_2020 + "\nCPF 162.092.735-72\n";
        Optional<SabespMetadata> meta = SabespPayslipMetadataExtractor.extract(text);
        assertTrue(meta.isPresent());
        assertEquals("16209273572", meta.get().cpf());
    }

    @Test
    @DisplayName("MonthYearDetection usa PERÍODO SABESP")
    void detectMonthYear() {
        Optional<String> ym = monthYear.detectMonthYear(FIXTURE_JAN_2020).block();
        assertTrue(ym.isPresent());
        assertEquals("2020-01", ym.get());
    }

    @Test
    @DisplayName("Parser genérico: qualquer código 3–4 dígitos; inclui 3347/3349")
    void parseLines_genericoIncluiSabesprev() {
        List<PdfLineParser.ParsedLine> lines = parser.parseLines(FIXTURE_JAN_2020, DocumentType.SABESP);
        assertTrue(lines.size() > 2, "deve extrair várias linhas; filtro final é o Mongo");

        PdfLineParser.ParsedLine r3347 = lines.stream()
                .filter(l -> "3347".equals(l.getCodigo()))
                .findFirst()
                .orElseThrow();
        assertEquals("700,39", r3347.getValorStr());
        assertTrue(r3347.getDescricao().toUpperCase().contains("SABESPREV"));

        PdfLineParser.ParsedLine r3349 = lines.stream()
                .filter(l -> "3349".equals(l.getCodigo()))
                .findFirst()
                .orElseThrow();
        assertEquals("58,36", r3349.getValorStr());

        assertTrue(lines.stream().anyMatch(l -> "3332".equals(l.getCodigo())));
    }

    @Test
    @DisplayName("PDF real Anselmo quando disponível em temp/")
    void extract_pdfReal_quandoDisponivel() throws Exception {
        Path pdf = Path.of(
                "temp/PASTADOCUMENTOS/ANSELMO FONSECA - estudo de layout - novo cliente",
                "CONTRACHEQUESABESP.2020.ESTUDOLAYOUT.pdf");
        assumeTrue(Files.isRegularFile(pdf), "PDF de referência não está no workspace");

        PdfServiceImpl pdfService = new PdfServiceImpl();
        String text = pdfService.extractText(Files.newInputStream(pdf)).block();
        assertEquals(DocumentType.SABESP, typeDetection.detectType(text).block());

        String page1 = pdfService.extractTextFromPage(Files.newInputStream(pdf), 1).block();
        List<PdfLineParser.ParsedLine> lines = parser.parseLines(page1, DocumentType.SABESP);
        assertTrue(lines.stream().anyMatch(l ->
                "3347".equals(l.getCodigo()) && "700,39".equals(l.getValorStr())));
        assertTrue(lines.stream().anyMatch(l ->
                "3349".equals(l.getCodigo()) && "58,36".equals(l.getValorStr())));
        assertEquals("2020-01", monthYear.detectMonthYear(page1).block().orElseThrow());
        assertEquals(null, SabespPayslipMetadataExtractor.extract(page1).orElseThrow().cpf());
    }
}
