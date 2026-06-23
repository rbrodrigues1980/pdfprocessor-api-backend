package br.com.verticelabs.pdfprocessor.infrastructure.incometax;

import br.com.verticelabs.pdfprocessor.application.incometax.IrpfDeclaracaoDataMapper;
import br.com.verticelabs.pdfprocessor.domain.model.IrpfDeclaracaoData;
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
 * Marcia Regina APCEF SC 2017/AC 2016 — crédito INSS empregador doméstico no RESUMO IRPF.
 */
class MarciaRegina2017InssDomesticoTest {

    private static final Path MARCIA_PDF = Paths.get(
            "temp/PASTADOCUMENTOSDESSIMULADORIRPF - Rogerio Rodrigues",
            "Marcia Regina Guttervill Cubas - APCEF SC",
            "2017",
            "DeclaraçãodeImpostodeRenda_CAIXA_2017.pdf");

    @Test
    void extraiContribuicaoPrevEmpregadorDomesticoDoResumo() throws Exception {
        Assumptions.assumeTrue(Files.exists(MARCIA_PDF),
                "PDF da Marcia Regina não encontrado: " + MARCIA_PDF.toAbsolutePath());

        ITextIncomeTaxServiceImpl service = new ITextIncomeTaxServiceImpl();
        var info = service.extractIncomeTaxInfo(new FileInputStream(MARCIA_PDF.toFile())).block();
        assertNotNull(info);

        assertEquals(new BigDecimal("1093.77"), info.getContribuicaoPrevEmpregadorDomestico());
        assertEquals(new BigDecimal("24693.02"), info.getImpostoDevidoII());

        IrpfDeclaracaoData data = new IrpfDeclaracaoDataMapper().fromIncomeTaxInfo(info);
        assertEquals(new BigDecimal("1093.77"), data.getContribuicaoPatronalPrevidenciaSocial());
        assertEquals(new BigDecimal("24693.02"), data.getImpostoDevidoII());
    }
}
