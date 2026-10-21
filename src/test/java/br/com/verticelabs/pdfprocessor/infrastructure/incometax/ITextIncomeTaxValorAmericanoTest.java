package br.com.verticelabs.pdfprocessor.infrastructure.incometax;

import br.com.verticelabs.pdfprocessor.domain.service.IncomeTaxDeclarationService.IncomeTaxInfo;
import br.com.verticelabs.pdfprocessor.domain.service.IncomeTaxDeclarationService.IncomeTaxInfo.FontePagadora;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.FileInputStream;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Declarações com centavos em ponto ({@code 186,721.08}) passam a ser lidas
 * no formato que os regex já conhecem ({@code 186.721,08}).
 */
@DisplayName("IR — valor no formato americano")
class ITextIncomeTaxValorAmericanoTest {

    private static final Path GEOVANI_PDF_2023 = Paths.get(
            "temp/PASTADOCUMENTOS/Geovani Tadeu Costa - AEA MG",
            "DeclaraçãodeImpostodeRenda_AEA_2023.pdf");

    private static final String FONTE_FUNCEF = """
            RENDIMENTOS TRIBUTÁVEIS RECEBIDOS DE PESSOA JURÍDICA PELO TITULAR
            FUNDACAO DOS ECONOMIARIOS
            FEDERAIS FUNCEF
            186,721.08 0.00 39,931.20 13,202.97 2,096.52
            CNPJ/CPF: 00.436.923/0001-90
            RENDIMENTOS TRIBUTÁVEIS RECEBIDOS DE PESSOA JURÍDICA PELOS DEPENDENTES
            """;

    @Test
    @DisplayName("troca centavos em ponto e preserva CPF, CNPJ, recibo, lei e valor brasileiro")
    void normalizaTokenAmericanoSemAlterarIdentificadores() {
        String original = "CPF 217.687.816-72 CNPJ 00.436.923/0001-90 "
                + "recibo 40.76.24.19.01-64 Lei 11.033/2004 "
                + "br 186.721,08 us 186,721.08 zero 0.00 aliquota 19.50 "
                + "colado 14,036.6639,931.201,046.88";

        String normalizado = ITextIncomeTaxServiceImpl.normalizarValoresAmericanos(original);

        assertTrue(normalizado.contains("217.687.816-72"));
        assertTrue(normalizado.contains("00.436.923/0001-90"));
        assertTrue(normalizado.contains("40.76.24.19.01-64"));
        assertTrue(normalizado.contains("11.033/2004"));
        assertTrue(normalizado.contains("186.721,08"));
        assertFalse(normalizado.contains("186,721.08"));
        assertTrue(normalizado.contains("0,00"));
        assertTrue(normalizado.contains("19,50"));
        assertTrue(normalizado.contains("14.036,6639.931,201.046,88"));
    }

    @Test
    @DisplayName("fonte FUNCEF: rendimento 186.721,08, previdência 0 e IRRF 39.931,20")
    void fixtureFonteFuncefLeValorAmericano() throws Exception {
        IncomeTaxInfo info = parse("", "", FONTE_FUNCEF);

        assertNotNull(info.getFontesPagadoras());
        assertEquals(1, info.getFontesPagadoras().size());
        FontePagadora funcef = info.getFontesPagadoras().get(0);
        assertEquals("00.436.923/0001-90", funcef.getCnpjCpf());
        assertEquals(0, new BigDecimal("186721.08").compareTo(funcef.getRendimentoTributavel()));
        assertEquals(0, BigDecimal.ZERO.compareTo(funcef.getContribuicaoPrevOficial()));
        assertEquals(0, new BigDecimal("39931.20").compareTo(funcef.getImpostoRetidoFonte()));
    }

    @Test
    @DisplayName("PDF Geovani 2023: FUNCEF e imposto devido no formato americano")
    void pdfGeovani2023LeValorAmericano() throws Exception {
        Assumptions.assumeTrue(Files.isRegularFile(GEOVANI_PDF_2023),
                "PDF Geovani 2023 não encontrado: " + GEOVANI_PDF_2023.toAbsolutePath());

        ITextIncomeTaxServiceImpl service = new ITextIncomeTaxServiceImpl();
        IncomeTaxInfo info = service.extractIncomeTaxInfo(
                new FileInputStream(GEOVANI_PDF_2023.toFile())).block();
        assertNotNull(info);

        FontePagadora funcef = info.getFontesPagadoras().stream()
                .filter(f -> f.getCnpjCpf() != null && f.getCnpjCpf().contains("00.436.923"))
                .findFirst()
                .orElse(null);
        assertNotNull(funcef, "Fonte FUNCEF não encontrada");
        assertEquals(0, new BigDecimal("186721.08").compareTo(funcef.getRendimentoTributavel()),
                "Rendimento FUNCEF era=" + funcef.getRendimentoTributavel());
        assertEquals(0, BigDecimal.ZERO.compareTo(nvl(funcef.getContribuicaoPrevOficial())),
                "Previdência FUNCEF era=" + funcef.getContribuicaoPrevOficial());
        assertEquals(0, new BigDecimal("39931.20").compareTo(funcef.getImpostoRetidoFonte()),
                "IRRF FUNCEF era=" + funcef.getImpostoRetidoFonte());
        assertEquals(0, new BigDecimal("39186.29").compareTo(nvl(info.getImpostoDevido())),
                "Imposto devido era=" + info.getImpostoDevido());
    }

    private static IncomeTaxInfo parse(String primeira, String resumo, String todas) throws Exception {
        ITextIncomeTaxServiceImpl service = new ITextIncomeTaxServiceImpl();
        Method method = ITextIncomeTaxServiceImpl.class.getDeclaredMethod(
                "parseIncomeTaxInfo", String.class, String.class, String.class);
        method.setAccessible(true);
        return (IncomeTaxInfo) method.invoke(service, primeira, resumo, todas);
    }

    private static BigDecimal nvl(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
