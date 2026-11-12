package br.com.verticelabs.pdfprocessor.infrastructure.incometax;

import br.com.verticelabs.pdfprocessor.application.tributacao.IrDoacoesDeducaoCalculator;
import br.com.verticelabs.pdfprocessor.domain.model.IrParametrosAnuais;
import br.com.verticelabs.pdfprocessor.domain.service.IncomeTaxDeclarationService.IncomeTaxInfo;
import br.com.verticelabs.pdfprocessor.infrastructure.config.IrTributacaoParametrosUtil;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.FileInputStream;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Iracema Silva de Castro — regressões:
 * - AC 2016: teto INSS doméstico = 1.093,77
 * - AC 2020: PDF concatena Retificadora (início) + Original (fim) → usar Retificadora
 */
@DisplayName("IR — Iracema 2016 doméstico e 2020 Retificadora")
class Iracema2016DomesticoE2020RetificadoraTest {

    private static final Path PASTA = Paths.get(
            "temp/PASTADOCUMENTOS/IRACEMA SILVA DE CASTRO");
    private static final Path PDF_2016 = PASTA.resolve("DeclaraçãodeImpostodeRenda_2016.pdf");
    private static final Path PDF_2020 = PASTA.resolve("DeclaraçãodeImpostodeRenda_2020.pdf");

    private static final BigDecimal DOMESTICO_2016 = new BigDecimal("1093.77");
    private static final BigDecimal TOTAL_RETIFICADORA_2020 = new BigDecimal("46075.29");

    @Test
    @DisplayName("teto INSS doméstico 2016 = 1.093,77")
    void tetoDomestico2016() {
        assertEquals(0, DOMESTICO_2016.compareTo(IrTributacaoParametrosUtil.limiteInssDomestico(2016)));

        BigDecimal efetivo = IrDoacoesDeducaoCalculator.calcularInssDomesticoEfetivo(
                DOMESTICO_2016,
                2016,
                IrParametrosAnuais.builder().limiteInssDomestico(DOMESTICO_2016).build());
        assertEquals(0, DOMESTICO_2016.compareTo(efetivo));
    }

    @Test
    @DisplayName("PDF 2016: doméstico extraído = 1.093,77 e crédito efetivo = teto")
    void pdf2016DomesticoECredito() throws Exception {
        Assumptions.assumeTrue(Files.isRegularFile(PDF_2016), "PDF Iracema 2016 ausente");

        ITextIncomeTaxServiceImpl service = new ITextIncomeTaxServiceImpl();
        IncomeTaxInfo info = service.extractIncomeTaxInfo(new FileInputStream(PDF_2016.toFile())).block();
        assertNotNull(info);
        assertEquals(0, DOMESTICO_2016.compareTo(info.getContribuicaoPrevEmpregadorDomestico()),
                "doméstico era=" + info.getContribuicaoPrevEmpregadorDomestico());

        BigDecimal efetivo = IrDoacoesDeducaoCalculator.calcularInssDomesticoEfetivo(
                info.getContribuicaoPrevEmpregadorDomestico(),
                2016,
                IrParametrosAnuais.builder()
                        .limiteInssDomestico(IrTributacaoParametrosUtil.limiteInssDomestico(2016))
                        .build());
        assertEquals(0, DOMESTICO_2016.compareTo(efetivo), "crédito efetivo era=" + efetivo);
    }

    @Test
    @DisplayName("PDF 2020: usa Retificadora (total 46.075,29), não a Original do fim")
    void pdf2020UsaRetificadora() throws Exception {
        Assumptions.assumeTrue(Files.isRegularFile(PDF_2020), "PDF Iracema 2020 ausente");

        ITextIncomeTaxServiceImpl service = new ITextIncomeTaxServiceImpl();
        IncomeTaxInfo info = service.extractIncomeTaxInfo(new FileInputStream(PDF_2020.toFile())).block();
        assertNotNull(info);

        assertTrue(IrpfEntregaVigenteSelector.isRetificadora(info.getTipoDeclaracao()),
                "tipo era=" + info.getTipoDeclaracao());
        assertEquals(0, TOTAL_RETIFICADORA_2020.compareTo(info.getTotalImpostoDevido()),
                "total era=" + info.getTotalImpostoDevido());
    }

    @Test
    @DisplayName("Fatima-like sem tipo: último bloco (compatibilidade)")
    void semTipoMantemUltimoBloco() {
        String texto = """
                === PAGINA 1 ===
                IDENTIFICAÇÃO DO CONTRIBUINTE
                PAGAMENTOS EFETUADOS
                11 EVERARDO 235.714.464-53 15.000,00 5.103,00
                RESUMO
                Despesas médicas 33.816,04
                === PAGINA 13 ===
                IDENTIFICAÇÃO DO CONTRIBUINTE
                PAGAMENTOS EFETUADOS
                26 HAPVIDA 63.554.067/0001-98 14.182,20 0,00
                RESUMO
                Despesas médicas 21.319,04
                """;
        String vigente = ITextIncomeTaxServiceImpl.recortarUltimaDeclaracao(texto);
        assertTrue(vigente.contains("21.319,04"));
        assertTrue(!vigente.contains("33.816,04"));
        assertTrue(vigente.contains("HAPVIDA"));
    }
}
