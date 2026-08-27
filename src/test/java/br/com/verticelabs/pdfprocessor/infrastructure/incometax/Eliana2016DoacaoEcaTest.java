package br.com.verticelabs.pdfprocessor.infrastructure.incometax;

import br.com.verticelabs.pdfprocessor.domain.service.IncomeTaxDeclarationService.IncomeTaxInfo;
import br.com.verticelabs.pdfprocessor.domain.service.IncomeTaxDeclarationService.IncomeTaxInfo.DoacaoEfetuada;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.io.FileInputStream;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Regressão: doação ECA direta na declaração (Eliana Pessoa AC 2016) deve ser extraída
 * e refletida em deducaoIncentivo para a simulação IRPF.
 */
class Eliana2016DoacaoEcaTest {

    private static final Path PDF = Paths.get(
            "temp/PASTADOCUMENTOS/ELIANA PESSOA - APCEF DF divergência dedução de incentivo ano 2016",
            "DeclaraçãodeImpostodeRenda_2016.pdf");

    @Test
    void extraiDoacaoEcaDiretaNaDeclaracao() throws Exception {
        Assumptions.assumeTrue(Files.exists(PDF), "PDF não encontrado: " + PDF.toAbsolutePath());

        ITextIncomeTaxServiceImpl service = new ITextIncomeTaxServiceImpl();
        IncomeTaxInfo info = service.extractIncomeTaxInfo(new FileInputStream(PDF.toFile())).block();
        assertNotNull(info);

        List<DoacaoEfetuada> doacoes = info.getDoacoesEfetuadas();
        assertNotNull(doacoes);
        assertFalse(doacoes.isEmpty(), "Esperava doação ECA direta (cód. 40)");

        DoacaoEfetuada eca = doacoes.stream()
                .filter(d -> "40".equals(d.getCodigo()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Doação cód. 40 não encontrada: " + doacoes));

        assertEquals("15.558.339/0001-85", eca.getCpfCnpj());
        assertEquals(new BigDecimal("1198.00"), eca.getValorDoado());
        assertEquals(new BigDecimal("1198.00"), info.getDeducaoIncentivo());
    }
}
