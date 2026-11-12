package br.com.verticelabs.pdfprocessor.infrastructure.incometax;

import br.com.verticelabs.pdfprocessor.domain.service.IncomeTaxDeclarationService.IncomeTaxInfo;
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
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Regressão Erton Birk Teixeira 2024: Imposto Lei 14.754/2023 (86,59)
 * deve entrar no total do imposto devido (74.886,52 = 74.799,93 + 86,59).
 */
@DisplayName("IR — Imposto Lei 14.754/2023 no total (Erton 2024)")
class ITextIncomeTaxErtonLei14754Test {

    private static final Path ERTON_PDF_2024 = Paths.get(
            "temp/PASTADOCUMENTOS/ERTON BIRK TEIXEIRA",
            "DeclaraçãodeImpostodeRenda_2024.pdf");

    private static final BigDecimal LEI_14754 = new BigDecimal("86.59");
    private static final BigDecimal IMPOSTO_DEVIDO = new BigDecimal("74799.93");
    private static final BigDecimal TOTAL_DEVIDO = new BigDecimal("74886.52");

    private static final String RESUMO_ERTON_LEI_14754 = """
            IMPOSTO DEVIDO IMPOSTO A RESTITUIR
            Base de cálculo do imposto 311.057,87
            Imposto devido 74.799,93
            Dedução de incentivo 0,00
            Imposto devido I 74.799,93
            Imposto devido RRA 0,00
            Imposto Lei 14.754/2023 86,59
            Aliquota efetiva (%) 18,67
            Total do imposto devido 74.886,52
            Total do imposto pago 77.588,16
            IMPOSTO A RESTITUIR 2.701,64
            SALDO DE IMPOSTO A PAGAR 0,00
            """;

    @Test
    @DisplayName("fixture RESUMO: Lei 14.754 = 86,59 e total = 74.886,52")
    void fixtureResumoExtraiLei14754ETotal() throws Exception {
        IncomeTaxInfo info = parseResumo(RESUMO_ERTON_LEI_14754);

        assertEquals(0, LEI_14754.compareTo(nvl(info.getImpostoDevidoLei14754())),
                "Imposto Lei 14.754/2023 era=" + info.getImpostoDevidoLei14754());
        assertEquals(0, TOTAL_DEVIDO.compareTo(nvl(info.getTotalImpostoDevido())),
                "Total do imposto devido era=" + info.getTotalImpostoDevido());
        assertEquals(0, IMPOSTO_DEVIDO.compareTo(nvl(info.getImpostoDevido())),
                "Imposto devido era=" + info.getImpostoDevido());
    }

    @Test
    @DisplayName("PDF Erton 2024: Lei 14.754 = 86,59 e total = 74.886,52")
    void pdfErton2024ExtraiLei14754ETotal() throws Exception {
        Assumptions.assumeTrue(Files.isRegularFile(ERTON_PDF_2024),
                "PDF Erton 2024 não encontrado: " + ERTON_PDF_2024.toAbsolutePath());

        ITextIncomeTaxServiceImpl service = new ITextIncomeTaxServiceImpl();
        IncomeTaxInfo info = service.extractIncomeTaxInfo(
                new FileInputStream(ERTON_PDF_2024.toFile())).block();
        assertNotNull(info);

        assertEquals(0, LEI_14754.compareTo(nvl(info.getImpostoDevidoLei14754())),
                "Imposto Lei 14.754/2023 era=" + info.getImpostoDevidoLei14754());
        assertEquals(0, TOTAL_DEVIDO.compareTo(nvl(info.getTotalImpostoDevido())),
                "Total do imposto devido era=" + info.getTotalImpostoDevido());
    }

    private static IncomeTaxInfo parseResumo(String resumo) throws Exception {
        ITextIncomeTaxServiceImpl service = new ITextIncomeTaxServiceImpl();
        Method parse = ITextIncomeTaxServiceImpl.class.getDeclaredMethod(
                "parseIncomeTaxInfo", String.class, String.class, String.class);
        parse.setAccessible(true);
        return (IncomeTaxInfo) parse.invoke(service, "", resumo, "");
    }

    private static BigDecimal nvl(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
