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
 * Regressão Eurípedes 2018 (exercício 2019): as colunas do RESUMO saem intercaladas
 * e o Imposto a restituir (3.746,44) cola em Imposto devido e em Imposto devido I.
 * O total e o Imposto devido II permanecem 29.586,27.
 */
@DisplayName("IR — Imposto a restituir não vira Imposto devido I (Eurípedes 2018)")
class ITextIncomeTaxEuripedesImpostoDevidoITest {

    private static final Path EURIPEDES_PDF_2018 = Paths.get(
            "temp/PASTADOCUMENTOS/Eurípedes Messias Rodrigues - AEA MG",
            "DeclaraçãodeImpostodeRenda_2018.pdf");

    private static final BigDecimal RESTITUIR = new BigDecimal("3746.44");
    private static final BigDecimal IMPOSTO_DEVIDO = new BigDecimal("29586.27");

    /**
     * Texto do RESUMO em que o valor da coluna restituir aparece logo após
     * "Imposto devido" e imediatamente antes de "Imposto devido I".
     */
    private static final String RESUMO_RESTITUIR_NA_COLUNA_DEVIDO = """
            IMPOSTO DEVIDO IMPOSTO A RESTITUIR
            3.746,44
            Base de cálculo do imposto
            145.522,16
            Imposto devido
            3.746,44
            Imposto devido I
            Dedução de incentivo
            0,00
            Imposto devido II 29.586,27
            Total do imposto devido 29.586,27
            """;

    @Test
    @DisplayName("fixture RESUMO: I e Imposto devido alinhados ao total, restituir preservado")
    void fixtureResumoNaoCopiaRestituirParaImpostoDevidoI() throws Exception {
        IncomeTaxInfo info = parseResumo(RESUMO_RESTITUIR_NA_COLUNA_DEVIDO);

        assertEquals(0, IMPOSTO_DEVIDO.compareTo(info.getImpostoDevido()),
                "Imposto devido não pode ser o restituir; era=" + info.getImpostoDevido());
        assertEquals(0, IMPOSTO_DEVIDO.compareTo(info.getImpostoDevidoI()),
                "Imposto devido I não pode ser o restituir; era=" + info.getImpostoDevidoI());
        assertEquals(0, BigDecimal.ZERO.compareTo(nvl(info.getDeducaoIncentivo())),
                "Dedução de incentivo deve ser 0; era=" + info.getDeducaoIncentivo());
        assertEquals(0, RESTITUIR.compareTo(info.getImpostoRestituir()),
                "Imposto a restituir deve permanecer 3.746,44; era=" + info.getImpostoRestituir());
    }

    @Test
    @DisplayName("PDF Eurípedes 2018: Imposto devido I = 29.586,27 e restituir = 3.746,44")
    void pdfEuripedes2018NaoCopiaRestituirParaImpostoDevidoI() throws Exception {
        Assumptions.assumeTrue(Files.isRegularFile(EURIPEDES_PDF_2018),
                "PDF Eurípedes 2018 não encontrado: " + EURIPEDES_PDF_2018.toAbsolutePath());

        ITextIncomeTaxServiceImpl service = new ITextIncomeTaxServiceImpl();
        IncomeTaxInfo info = service.extractIncomeTaxInfo(
                new FileInputStream(EURIPEDES_PDF_2018.toFile())).block();
        assertNotNull(info);

        assertEquals(0, IMPOSTO_DEVIDO.compareTo(info.getTotalImpostoDevido()),
                "Total do imposto devido");
        assertEquals(0, IMPOSTO_DEVIDO.compareTo(info.getImpostoDevido()),
                "Imposto devido era=" + info.getImpostoDevido());
        assertNotNull(info.getImpostoDevidoI());
        assertEquals(0, IMPOSTO_DEVIDO.compareTo(info.getImpostoDevidoI()),
                "Imposto devido I não pode ser o restituir 3746.44; era=" + info.getImpostoDevidoI());
        assertEquals(0, BigDecimal.ZERO.compareTo(nvl(info.getDeducaoIncentivo())),
                "Dedução de incentivo deve ser 0; era=" + info.getDeducaoIncentivo());
        assertEquals(0, RESTITUIR.compareTo(nvl(info.getImpostoRestituir())),
                "Imposto a restituir era=" + info.getImpostoRestituir());
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
