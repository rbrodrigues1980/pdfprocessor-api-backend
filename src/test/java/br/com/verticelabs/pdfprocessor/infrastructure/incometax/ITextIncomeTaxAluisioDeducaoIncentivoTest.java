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
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Regressão Aluísio 2025: sem doações ECA/Idoso e dedução de incentivo = 0,00 no PDF.
 * Não inventar 20.245,32 a partir de Imposto a restituir capturado como Imposto devido I.
 */
@DisplayName("IR — dedução de incentivo fantasma (Aluísio 2025)")
class ITextIncomeTaxAluisioDeducaoIncentivoTest {

    private static final Path ALUISIO_PDF_2025 = Paths.get(
            "temp/PASTADOCUMENTOS/ALUISIO JOSE DE PAIVA MARTINS - AEAP PE/2025",
            "DeclaraçãodeImpostodeRenda_2025.pdf");

    /**
     * Texto típico do RESUMO onde o bug antigo cruzava "IMPOSTO DEVIDO" + "I" de
     * "IMPOSTO A RESTITUIR" e inventava dedução = 31841,08 − 11595,76.
     */
    private static final String RESUMO_SEM_INCENTIVO = """
            IMPOSTO DEVIDO
            IMPOSTO A RESTITUIR
            11.595,76
            Base de cálculo do imposto
            155.254,06
            Imposto devido
            31.841,08
            Dedução de incentivo
            0,00
            Imposto devido I 31.841,08
            Imposto devido RRA
            0,00
            Alíquota efetiva (%)
            14,55
            Total do imposto devido 31.841,08
            """;

    @Test
    @DisplayName("fixture RESUMO: não inventa dedução a partir de Imposto a restituir")
    void fixtureResumoNaoInventaDeducaoViaRestituir() throws Exception {
        ITextIncomeTaxServiceImpl service = new ITextIncomeTaxServiceImpl();

        Method corrigir = ITextIncomeTaxServiceImpl.class.getDeclaredMethod(
                "corrigirCamposImpostoDevidoResumo",
                String.class, String.class,
                BigDecimal.class, BigDecimal.class, BigDecimal.class, BigDecimal.class,
                BigDecimal.class, BigDecimal.class, BigDecimal.class, BigDecimal.class,
                BigDecimal.class, BigDecimal.class, BigDecimal.class, BigDecimal.class);
        corrigir.setAccessible(true);

        // Simula o falso positivo antigo: I = restituir
        Object result = corrigir.invoke(
                service,
                RESUMO_SEM_INCENTIVO,
                "COMPLETO",
                new BigDecimal("155254.06"),
                new BigDecimal("31841.08"),
                BigDecimal.ZERO,
                new BigDecimal("11595.76"), // I errado (restituir)
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                new BigDecimal("31841.08"), // total = devido
                new BigDecimal("200000.00"),
                new BigDecimal("40000.00"),
                null);

        Method deducaoGetter = result.getClass().getDeclaredMethod("deducaoIncentivo");
        Method iGetter = result.getClass().getDeclaredMethod("impostoDevidoI");
        BigDecimal deducao = (BigDecimal) deducaoGetter.invoke(result);
        BigDecimal impostoI = (BigDecimal) iGetter.invoke(result);

        assertEquals(0, BigDecimal.ZERO.compareTo(deducao == null ? BigDecimal.ZERO : deducao),
                "Não deve inventar dedução; era=" + deducao);
        assertEquals(0, new BigDecimal("31841.08").compareTo(impostoI),
                "Imposto devido I deve alinhar ao total; era=" + impostoI);
    }

    @Test
    @DisplayName("PDF Aluísio 2025: deducaoIncentivo=0 e I≠restituir")
    void pdfAluisio2025SemDeducaoIncentivo() throws Exception {
        Assumptions.assumeTrue(Files.isRegularFile(ALUISIO_PDF_2025),
                "PDF Aluísio 2025 não encontrado: " + ALUISIO_PDF_2025.toAbsolutePath());

        ITextIncomeTaxServiceImpl service = new ITextIncomeTaxServiceImpl();
        IncomeTaxInfo info = service.extractIncomeTaxInfo(
                new FileInputStream(ALUISIO_PDF_2025.toFile())).block();
        assertNotNull(info);

        assertEquals(0, new BigDecimal("31841.08").compareTo(info.getImpostoDevido()),
                "Imposto devido");
        assertEquals(0, new BigDecimal("31841.08").compareTo(info.getTotalImpostoDevido()),
                "Total do imposto devido");

        BigDecimal deducao = info.getDeducaoIncentivo() == null
                ? BigDecimal.ZERO
                : info.getDeducaoIncentivo();
        assertEquals(0, BigDecimal.ZERO.compareTo(deducao),
                "Dedução de incentivo deve ser 0; era=" + deducao);

        assertNotNull(info.getImpostoDevidoI());
        assertEquals(0, new BigDecimal("31841.08").compareTo(info.getImpostoDevidoI()),
                "Imposto devido I não pode ser o restituir 11595.76; era=" + info.getImpostoDevidoI());

        assertTrue(new BigDecimal("20245.32").compareTo(deducao) != 0);
    }
}
