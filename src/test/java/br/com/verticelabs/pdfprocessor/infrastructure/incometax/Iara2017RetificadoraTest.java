package br.com.verticelabs.pdfprocessor.infrastructure.incometax;

import br.com.verticelabs.pdfprocessor.domain.service.IncomeTaxDeclarationService.IncomeTaxInfo;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.FileInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Iara Nobre Dias AC 2017 — PDF concatena Retificadora (início) + Original (fim).
 * Deve prevalecer a Retificadora via {@link IrpfEntregaVigenteSelector}.
 */
@DisplayName("IR — Iara 2017 Retificadora vigente")
class Iara2017RetificadoraTest {

    private static final Path PDF_2017 = Paths.get(
            "temp/PASTADOCUMENTOS/IARA NOBRE DIAS",
            "DeclaraçãodeImpostodeRenda_2017.pdf");

    @Test
    @DisplayName("PDF 2017: usa Retificadora, não a Original do fim do arquivo")
    void pdf2017UsaRetificadora() throws Exception {
        Assumptions.assumeTrue(Files.isRegularFile(PDF_2017),
                "PDF Iara 2017 ausente: " + PDF_2017.toAbsolutePath());

        ITextIncomeTaxServiceImpl service = new ITextIncomeTaxServiceImpl();
        IncomeTaxInfo info = service.extractIncomeTaxInfo(new FileInputStream(PDF_2017.toFile())).block();
        assertNotNull(info);
        assertTrue(IrpfEntregaVigenteSelector.isRetificadora(info.getTipoDeclaracao()),
                "tipo era=" + info.getTipoDeclaracao());
    }
}
