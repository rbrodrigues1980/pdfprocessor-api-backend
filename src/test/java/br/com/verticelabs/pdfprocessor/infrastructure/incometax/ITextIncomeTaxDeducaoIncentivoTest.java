package br.com.verticelabs.pdfprocessor.infrastructure.incometax;

import br.com.verticelabs.pdfprocessor.domain.service.IncomeTaxDeclarationService.IncomeTaxInfo;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.io.FileInputStream;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Regressão: em layouts de RESUMO com colunas embaralhadas (ex.: ADRIANA AC 2021),
 * a "Dedução de incentivo" (doações ECA/Idoso que abatem o total do imposto devido)
 * deve ser derivada corretamente a partir de imposto devido − total do imposto devido.
 */
class ITextIncomeTaxDeducaoIncentivoTest {

    private static final Path ADRIANA_PDF_2021 = Paths.get(
            "temp/PASTADOCUMENTOSDESSIMULADORIRPF - Rogerio Rodrigues",
            "ADRIANA MARIA QUEIROZ FREITAS",
            "DeclaraçãodeImpostodeRenda_CAIXA_2021.pdf");

    @Test
    void derivaDeducaoIncentivoEImpostoDevidoI() throws Exception {
        Assumptions.assumeTrue(Files.exists(ADRIANA_PDF_2021),
                "PDF 2021 da Adriana não encontrado: " + ADRIANA_PDF_2021.toAbsolutePath());

        ITextIncomeTaxServiceImpl service = new ITextIncomeTaxServiceImpl();
        IncomeTaxInfo info = service.extractIncomeTaxInfo(
                new FileInputStream(ADRIANA_PDF_2021.toFile())).block();
        assertNotNull(info);

        assertEquals(new BigDecimal("67343.04"), info.getImpostoDevido());
        assertEquals(new BigDecimal("63302.46"), info.getTotalImpostoDevido());
        assertEquals(new BigDecimal("4040.58"), info.getDeducaoIncentivo());
        assertEquals(new BigDecimal("63302.46"), info.getImpostoDevidoI());
    }
}
