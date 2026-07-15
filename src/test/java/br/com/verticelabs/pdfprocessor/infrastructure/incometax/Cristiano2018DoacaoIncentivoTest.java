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
 * Regressão: doação cód. 41 (Cultura/FENAE) na DIRPF 2018 de Cristiano Aguiar
 * deve ser extraída mesmo com declaração no modelo Simplificado.
 */
class Cristiano2018DoacaoIncentivoTest {

    private static final Path PDF = Paths.get(
            "temp/PASTADOCUMENTOSDESSIMULADORIRPF - Rogerio Rodrigues",
            "Cristiano Rocha de Aguiar - AEA PE - OK",
            "DeclaraçãodeImpostodeRenda_2018.pdf");

    @Test
    void extraiDoacaoCodigo41Fenae() throws Exception {
        Assumptions.assumeTrue(Files.exists(PDF), "PDF não encontrado: " + PDF.toAbsolutePath());

        ITextIncomeTaxServiceImpl service = new ITextIncomeTaxServiceImpl();
        IncomeTaxInfo info = service.extractIncomeTaxInfo(new FileInputStream(PDF.toFile())).block();
        assertNotNull(info);

        List<DoacaoEfetuada> doacoes = info.getDoacoesEfetuadas();
        assertNotNull(doacoes);
        assertFalse(doacoes.isEmpty(), "Esperava doações efetuadas (cód. 41)");

        DoacaoEfetuada d41 = doacoes.stream()
                .filter(d -> "41".equals(d.getCodigo()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Doação cód. 41 não encontrada: " + doacoes));

        assertEquals(new BigDecimal("1000.00"), d41.getValorDoado());
    }
}
