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

    private static final String FIXTURE_BENEFICIO_JAN_2016 = """
            SABESPREV - Fundação Sabesp de Seguridade Social
            DEMONSTRATIVO DE PAGAMENTO - BENEFÍCIO BÁSICO
            REFERÊNCIA
            01/2016
            ESTELA DOS SANTOS RODRIGUES PERES
            CPF: 731.278.258-20 Dep.:0
            PATROCINADORA:
            COMPANHIA DE SANEAMENTO BASICO DO ESTADO DE SAO PAULO
            COD DESCRIÇÃO Competência Proventos Descontos
            VERBAS
            1040 APOSENTADORIA ANTECIPADA 01/01/2016 9.807,70
            9105 CUSTEIO ADM BENEFICIO 01/01/2016 -39,56
            TOTAIS
            Proventos Descontos Líquido
            9.807,70 -4.716,00 5.091,70
            """;

    @Test
    @DisplayName("Antares 13º: 4412 unit+venc+qtde colados → valor = vencimentos 30.596,70")
    void antares_4412_usaVencimentos() {
        String page = """
                DEMONSTRATIVO DE PAGAMENTO - DÉCIMO TERCEIRO SALÁRIO
                COMPANHIA DE SANEAMENTO BÁSICO DO ESTADO DE SÃO PAULO - SABESP
                4412 13 SAL.INTEGRAL 2.549,72 30.596,7012,00
                4414 SU.AP.S/13SALAR. 1.065,93
                4415 INSS-13 SALARIO 570,88
                """;

        List<PdfLineParser.ParsedLine> lines = parser.parseLines(page, DocumentType.SABESP);

        PdfLineParser.ParsedLine r4412 = lines.stream()
                .filter(l -> "4412".equals(l.getCodigo()))
                .findFirst()
                .orElseThrow();
        assertEquals("30.596,70", r4412.getValorStr());
        assertTrue(r4412.getDescricao().toUpperCase().contains("SAL.INTEGRAL"));

        assertTrue(lines.stream().anyMatch(l ->
                "4414".equals(l.getCodigo()) && "1.065,93".equals(l.getValorStr())));
    }

    @Test
    @DisplayName("Antares PDFBox: várias rubricas coladas na mesma linha")
    void antares_rubricasColadasPdfBox() {
        String page = """
                DEMONSTRATIVO DE PAGAMENTO - DÉCIMO TERCEIRO SALÁRIO
                4412 13 SAL.INTEGRAL 2.549,72 30.596,7012,004414 SU.AP.S/13SALAR. 1.065,934415 INSS-13 SALARIO 570,884416 IRRF 13 SALARIO 6.758,054417 DESC ANTEC.13SAL 15.298,35
                """;

        List<PdfLineParser.ParsedLine> lines = parser.parseLines(page, DocumentType.SABESP);
        assertTrue(lines.stream().anyMatch(l ->
                "4412".equals(l.getCodigo()) && "30.596,70".equals(l.getValorStr())));
        assertTrue(lines.stream().anyMatch(l ->
                "4414".equals(l.getCodigo()) && "1.065,93".equals(l.getValorStr())));
        assertTrue(lines.stream().anyMatch(l ->
                "4417".equals(l.getCodigo()) && "15.298,35".equals(l.getValorStr())));
    }

    @Test
    @DisplayName("PDF real Antonio 2016: extrai 4412 = 30.596,70")
    void extract_pdfRealAntonio2016_quandoDisponivel() throws Exception {
        Path pdf = Path.of(
                "temp/PASTADOCUMENTOS/ANTONIO JAVIER GARCIA PEREZ",
                "ContraChequesSABESPREV2016.pdf");
        assumeTrue(Files.isRegularFile(pdf), "PDF Antonio 2016 não está no workspace");

        PdfServiceImpl pdfService = new PdfServiceImpl();
        String text = pdfService.extractText(Files.newInputStream(pdf)).block();
        assumeTrue(text != null && text.contains("4412"), "PDFBox não extraiu linha 4412");
        List<PdfLineParser.ParsedLine> lines = parser.parseLines(text, DocumentType.SABESP);
        assertTrue(lines.stream().anyMatch(l ->
                "4412".equals(l.getCodigo()) && "30.596,70".equals(l.getValorStr())),
                "Esperado 4412=30.596,70; amostra: " + lines.stream()
                        .filter(l -> "4412".equals(l.getCodigo()))
                        .map(l -> l.getCodigo() + "=" + l.getValorStr())
                        .toList());
    }

    @Test
    @DisplayName("Benefício sem data na linha (Antonio jan–abr/2021): 7400 negativo → positivo")
    void beneficioSemCompetenciaNaLinha_7400desconto() {
        String page = """
                SABESPREV - Fundação Sabesp de Seguridade Social
                DEMONSTRATIVO DE PAGAMENTO
                REFERÊNCIA
                01/2021
                ANTONIO JAVIER GARCIA PEREZ
                COD DESCRIÇÃO Parâmetros Proventos Descontos
                VERBAS
                1030 APOSENTADORIA NORMAL 16.943,00
                7400 CONTRIBUIÇÃO DEFICIT -1.401,71
                9607 ASSOC. APOS. E PENS. DA SABESP -68,30
                9001 IMPOSTO DE RENDA -2.880,90
                """;

        assertEquals("2021-01", monthYear.detectMonthYear(page).block().orElseThrow());

        List<PdfLineParser.ParsedLine> lines = parser.parseLines(page, DocumentType.SABESP);
        assertTrue(lines.stream().anyMatch(l ->
                "1030".equals(l.getCodigo()) && "16.943,00".equals(l.getValorStr())));
        assertTrue(lines.stream().anyMatch(l ->
                "7400".equals(l.getCodigo()) && "1.401,71".equals(l.getValorStr())),
                "7400 com sinal deve ser capturado como 1.401,71");
        assertTrue(lines.stream().anyMatch(l ->
                "9607".equals(l.getCodigo()) && "68,30".equals(l.getValorStr())));
    }

    @Test
    @DisplayName("PDF real Antonio 2021 p.1: 7400 = 1.401,71")
    void extract_pdfRealAntonio2021jan_quandoDisponivel() throws Exception {
        Path pdf = Path.of(
                "temp/PASTADOCUMENTOS/ANTONIO JAVIER GARCIA PEREZ",
                "ContraChequesSABESPREV2021.pdf");
        assumeTrue(Files.isRegularFile(pdf), "PDF Antonio 2021 não está no workspace");

        PdfServiceImpl pdfService = new PdfServiceImpl();
        String page1 = pdfService.extractTextFromPage(Files.newInputStream(pdf), 1).block();
        assertEquals("2021-01", monthYear.detectMonthYear(page1).block().orElseThrow());
        List<PdfLineParser.ParsedLine> lines = parser.parseLines(page1, DocumentType.SABESP);
        assertTrue(lines.stream().anyMatch(l ->
                "7400".equals(l.getCodigo()) && "1.401,71".equals(l.getValorStr())));
    }

    @Test
    @DisplayName("Benefício SABESPREV: tipo SABESP, mês da REFERÊNCIA e desconto positivo")
    void beneficioSabesprev_janeiro2016() {
        assertEquals(DocumentType.SABESP, typeDetection.detectType(FIXTURE_BENEFICIO_JAN_2016).block());
        assertEquals("2016-01", monthYear.detectMonthYear(FIXTURE_BENEFICIO_JAN_2016).block().orElseThrow());

        List<PdfLineParser.ParsedLine> lines = parser.parseLines(FIXTURE_BENEFICIO_JAN_2016, DocumentType.SABESP);

        PdfLineParser.ParsedLine provento = lines.stream()
                .filter(l -> "1040".equals(l.getCodigo()))
                .findFirst()
                .orElseThrow();
        assertEquals("9.807,70", provento.getValorStr());
        assertEquals("01/2016", provento.getReferencia());
        assertTrue(provento.getDescricao().toUpperCase().contains("APOSENTADORIA"));
        assertTrue(!provento.getDescricao().contains("01/01/2016"));

        PdfLineParser.ParsedLine desconto = lines.stream()
                .filter(l -> "9105".equals(l.getCodigo()))
                .findFirst()
                .orElseThrow();
        assertEquals("39,56", desconto.getValorStr());
        assertEquals("01/2016", desconto.getReferencia());
        assertTrue(!desconto.getDescricao().contains("01/01/2016"));
    }
}
