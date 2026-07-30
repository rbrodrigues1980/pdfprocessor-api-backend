package br.com.verticelabs.pdfprocessor.infrastructure.pdf;

import br.com.verticelabs.pdfprocessor.domain.model.DocumentType;
import br.com.verticelabs.pdfprocessor.domain.model.InformeRendimentosData;
import br.com.verticelabs.pdfprocessor.domain.model.InformeRendimentosData.InformacaoComplementarJudiciaria;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

@DisplayName("Informe de Rendimentos Funcef — detecção e extração")
class InformeRendimentosFuncefExtractorTest {

    private final InformeRendimentosFuncefExtractor extractor = new InformeRendimentosFuncefExtractor();
    private final DocumentTypeDetectionServiceImpl typeDetection = new DocumentTypeDetectionServiceImpl();

    private static final String FIXTURE_TEXT = """
            1. FONTE PAGADORA PESSOA JURÍDICA OU PESSOA FÍSICA
            CNPJ
            Telefone
            00.436.923/0001-90
            0800 706 9000
            Razão Social
            FUNDAÇÃO DOS ECONOMIÁRIOS FEDERAIS
            Endereço
            SCN Q. 02 BLOCO A.
            2. PESSOA FÍSICA BENEFICIÁRIO DOS RENDIMENTOS
            Ano Calendário
            CPF
            NOME COMPLETO
            2018
            162.092.735-72
            PAULO AUGUSTO LOPES DA CRUZ
            NATUREZA DO RENDIMENTO
            Benefícios / Resgates de Entidade de Previdência Privada
            3. RENDIMENTOS TRIBUTÁVEIS, DEDUÇÕES E IMPOSTO
            FUNCEF
            7. INFORMAÇÕES COMPLEMENTARES
            Proc.Jud.
            1007809-57.2017.4.01.3300
             -
            03/06/2018
             -
            10
             - CIVEL BA  - Contr. Extr.:
            12.618,07
              - IRRF :
            3.470,00
              - Contr. Extr.
            13
            º :
            1.904,02
            - IRRF
            13
            º :
            523,61
            8. RESPONSÁVEL PELAS INFORMAÇÕES
            COMPROVANTE DE RENDIMENTOS PAGOS E DE RETENÇÃO DE IMPOSTO DE RENDA NA
            FONTE
            """;

    @Test
    @DisplayName("detectType deve retornar INFORME_RENDIMENTOS")
    void detectType_informeRendimentos() {
        DocumentType type = typeDetection.detectType(FIXTURE_TEXT).block();
        assertEquals(DocumentType.INFORME_RENDIMENTOS, type);
    }

    @Test
    @DisplayName("Não confundir contracheque Funcef com informe")
    void detectType_naoConfundeContracheque() {
        String contracheque = """
                FUNCEF
                Fundação dos Economiários Federais
                DEMONSTRATIVO DE PROVENTOS PREVIDENCIÁRIOS
                Ano Pagamento / Mês
                2018 / 01
                Nº Benefício INSS
                123456
                Tipo de Benefício
                APOSENTADORIA
                """;
        DocumentType type = typeDetection.detectType(contracheque).block();
        assertEquals(DocumentType.FUNCEF, type);
        assertFalse(type == DocumentType.INFORME_RENDIMENTOS);
    }

    @Test
    @DisplayName("Extrai cabeçalho e seção 7 do fixture Paulo 2018")
    void extract_secao7_paulo2018() {
        InformeRendimentosData data = extractor.extract(FIXTURE_TEXT);

        assertEquals("00.436.923/0001-90", data.getCnpjFontePagadora());
        assertNotNull(data.getRazaoSocialFontePagadora());
        assertTrue(data.getRazaoSocialFontePagadora().toUpperCase().contains("ECONOMI"));
        assertEquals("2018", data.getAnoCalendario());
        assertEquals("162.092.735-72", data.getCpfBeneficiario());
        assertEquals("PAULO AUGUSTO LOPES DA CRUZ", data.getNomeBeneficiario());
        assertNotNull(data.getInformacoesComplementaresRaw());
        assertEquals(1, data.getInformacoesComplementares().size());

        InformacaoComplementarJudiciaria info = data.getInformacoesComplementares().get(0);
        assertEquals("1007809-57.2017.4.01.3300", info.getNumeroProcesso());
        assertEquals("03/06/2018", info.getData());
        assertEquals("10", info.getCodigo());
        assertTrue(info.getVaraOuLocal().toUpperCase().contains("CIVEL"));
        assertEquals(new BigDecimal("12618.07"), info.getContrExtr());
        assertEquals(new BigDecimal("3470.00"), info.getIrrf());
        assertEquals(new BigDecimal("1904.02"), info.getContrExtr13());
        assertEquals(new BigDecimal("523.61"), info.getIrrf13());
    }

    @Test
    @DisplayName("Extrai do PDF real Paulo 2018 quando disponível em temp/")
    void extract_pdfReal_quandoDisponivel() throws Exception {
        Path pdf = Path.of(
                "temp/PASTADOCUMENTOS/PAULO AUGUSTO LOPES DA CRUZ - APCEF BA Ajustes PERFUMARIAS/2018/InformesdeRendimentos_CAIXA_2018.pdf");
        assumeTrue(Files.isRegularFile(pdf), "PDF de referência não está no workspace");

        // Usa PdfServiceImpl via iText/PDFBox do projeto — fallback: ler bytes e extrair com pypdf-like via PdfServiceImpl
        br.com.verticelabs.pdfprocessor.infrastructure.pdf.PdfServiceImpl pdfService =
                new br.com.verticelabs.pdfprocessor.infrastructure.pdf.PdfServiceImpl();
        String text = pdfService.extractText(Files.newInputStream(pdf)).block();
        assertNotNull(text);
        assertTrue(text.length() > 100);

        assertEquals(DocumentType.INFORME_RENDIMENTOS, typeDetection.detectType(text).block());

        InformeRendimentosData data = extractor.extract(text);
        assertEquals("2018", data.getAnoCalendario());
        assertEquals("162.092.735-72", data.getCpfBeneficiario());
        assertEquals("PAULO AUGUSTO LOPES DA CRUZ", data.getNomeBeneficiario());
        assertNotNull(data.getCnpjFontePagadora());
        assertNotNull(data.getRazaoSocialFontePagadora());
        assertEquals(1, data.getInformacoesComplementares().size());

        InformacaoComplementarJudiciaria info = data.getInformacoesComplementares().get(0);
        assertEquals("1007809-57.2017.4.01.3300", info.getNumeroProcesso());
        assertEquals("03/06/2018", info.getData());
        assertEquals("10", info.getCodigo());
        assertNotNull(info.getVaraOuLocal());
        assertTrue(info.getVaraOuLocal().toUpperCase().contains("CIVEL"));
        assertEquals(new BigDecimal("12618.07"), info.getContrExtr());
        assertEquals(new BigDecimal("3470.00"), info.getIrrf());
        assertEquals(new BigDecimal("1904.02"), info.getContrExtr13());
        assertEquals(new BigDecimal("523.61"), info.getIrrf13());
    }
}
