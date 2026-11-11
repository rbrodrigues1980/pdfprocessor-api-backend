package br.com.verticelabs.pdfprocessor.infrastructure.incometax;

import br.com.verticelabs.pdfprocessor.application.incometax.IrpfDeclaracaoDataMapper;
import br.com.verticelabs.pdfprocessor.application.tributacao.IrPagamentosDeducaoAggregator;
import br.com.verticelabs.pdfprocessor.application.tributacao.dto.SimuladorIrpfRequest;
import br.com.verticelabs.pdfprocessor.domain.model.IrpfDeclaracaoData;
import br.com.verticelabs.pdfprocessor.domain.service.IncomeTaxDeclarationService.IncomeTaxInfo;
import br.com.verticelabs.pdfprocessor.domain.service.IncomeTaxDeclarationService.IncomeTaxInfo.PagamentoEfetuado;
import br.com.verticelabs.pdfprocessor.infrastructure.excel.ExcelIrpfSimulacaoMapper;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.FileInputStream;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Validação opcional do PDF IR 2024 da Carmen (temp/). Skip se ausente.
 */
class Carmen2024PdfDespesasMedicasTest {

    private static final Path PDF = Path.of(
            "temp/PASTADOCUMENTOS/CARMEN LUCIA DOMINGUES CUNHA/DeclaraçãodeImpostodeRenda_2024.pdf");

    @Test
    @DisplayName("PDF IR 2024: inclui CR Clínica 800 e médicas sem gap vs RESUMO")
    void pdfIncluiCrClinica800() throws Exception {
        Assumptions.assumeTrue(Files.exists(PDF), "PDF IR Carmen 2024 não encontrado");

        IncomeTaxInfo info = new ITextIncomeTaxServiceImpl()
                .extractIncomeTaxInfo(new FileInputStream(PDF.toFile()))
                .block();

        List<PagamentoEfetuado> pagamentos = info.getPagamentosEfetuados();
        assertTrue(pagamentos.stream().anyMatch(p ->
                        "21".equals(p.getCodigo())
                                && p.getNomeBeneficiario() != null
                                && p.getNomeBeneficiario().toUpperCase().contains("CR CLINICA")
                                && new BigDecimal("800.00").compareTo(p.getValorPago()) == 0),
                () -> "faltou CR Clínica 800; got=" + pagamentos.stream()
                        .map(p -> p.getCodigo() + "|" + p.getNomeBeneficiario() + "|" + p.getValorPago())
                        .toList());

        IrpfDeclaracaoData data = new IrpfDeclaracaoDataMapper().fromIncomeTaxInfo(info);
        SimuladorIrpfRequest request = new ExcelIrpfSimulacaoMapper(new IrPagamentosDeducaoAggregator())
                .fromDeclaracao(data, data.getContribuicaoPrevidenciaPrivada(), true);

        assertEquals(0, info.getDeducoesMedicas().compareTo(request.getDespesasMedicas()),
                "simulação médicas deve bater com RESUMO; resumo="
                        + info.getDeducoesMedicas() + " sim=" + request.getDespesasMedicas());
    }
}
