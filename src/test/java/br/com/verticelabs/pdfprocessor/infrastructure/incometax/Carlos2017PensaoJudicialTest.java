package br.com.verticelabs.pdfprocessor.infrastructure.incometax;

import br.com.verticelabs.pdfprocessor.application.incometax.IrpfDeclaracaoDataMapper;
import br.com.verticelabs.pdfprocessor.application.tributacao.IrCalculoProgressivoService;
import br.com.verticelabs.pdfprocessor.application.tributacao.IrDoacoesDeducaoCalculator;
import br.com.verticelabs.pdfprocessor.application.tributacao.IrPagamentosDeducaoAggregator;
import br.com.verticelabs.pdfprocessor.application.tributacao.IrSimuladorMotorService;
import br.com.verticelabs.pdfprocessor.application.tributacao.dto.SimuladorIrpfRequest;
import br.com.verticelabs.pdfprocessor.domain.model.IrpfDeclaracaoData;
import br.com.verticelabs.pdfprocessor.domain.service.IncomeTaxDeclarationService.IncomeTaxInfo;
import br.com.verticelabs.pdfprocessor.domain.service.IncomeTaxDeclarationService.IncomeTaxInfo.PagamentoEfetuado;
import br.com.verticelabs.pdfprocessor.infrastructure.excel.ExcelIrpfDeducoesResumoHelper;
import br.com.verticelabs.pdfprocessor.infrastructure.excel.ExcelIrpfSimulacaoMapper;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.io.FileInputStream;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Carlos Alberto: CPF quebrado e valor com espaço na seção de pagamentos
 * faziam a pensão do Alessandro sumir da simulação (2017 e 2018).
 */
class Carlos2017PensaoJudicialTest {

    private static final Path PDF_2017 = Paths.get(
            "temp/PASTADOCUMENTOS/Carlos Alberto de Melo Silva - AEA PE/DeclaraçãodeImpostodeRenda_2017.pdf");
    private static final Path PDF_2018 = Paths.get(
            "temp/PASTADOCUMENTOS/Carlos Alberto de Melo Silva - AEA PE/DeclaraçãodeImpostodeRenda_2018.pdf");

    @Test
    void normalizaCpfQuebradoEValorComEspaco() {
        assertEquals(
                "30 ALESSANDRO BARBOSA DE MELO SILVA 741.486.484-72 3.966,93 0,00",
                ITextIncomeTaxServiceImpl.normalizarSecaoPagamentos(
                        "30 ALESSANDRO BARBOSA DE MELO SILVA 7 41 .486 .484-72 3.966,93 0,00"));
        assertEquals(
                "30 ALESSANDRO BARBOSA DE MELO SILVA 741.486.484-72 3.453,11 0,00",
                ITextIncomeTaxServiceImpl.normalizarSecaoPagamentos(
                        "30 ALESSANDRO BARBOSA DE MELO SILVA 7 41 .486 .484-72 3.453, 11 0,00"));
        String nair = "30 NAIR BARBOSA DE MELO 136.598.044-87 17.178,73 0,00";
        assertEquals(nair, ITextIncomeTaxServiceImpl.normalizarSecaoPagamentos(nair));
        String cnpj = "26 SUL AMERICA 01.685.053/0001-56 10.970,94 0,00";
        assertEquals(cnpj, ITextIncomeTaxServiceImpl.normalizarSecaoPagamentos(cnpj));
    }

    @Test
    void pdf2017SomaAsDuasPensoesJudiciais() throws Exception {
        assertPensaoSimulacao(
                PDF_2017,
                new BigDecimal("3966.93"),
                new BigDecimal("17178.73"),
                new BigDecimal("21145.66"));
    }

    @Test
    void pdf2018SomaAsDuasPensoesJudiciais() throws Exception {
        assertPensaoSimulacao(
                PDF_2018,
                new BigDecimal("3453.11"),
                new BigDecimal("15746.65"),
                new BigDecimal("19199.76"));
    }

    private static void assertPensaoSimulacao(
            Path pdf, BigDecimal alessandro, BigDecimal nair, BigDecimal pensao) throws Exception {
        Assumptions.assumeTrue(Files.isRegularFile(pdf), "PDF não encontrado: " + pdf.toAbsolutePath());

        ITextIncomeTaxServiceImpl service = new ITextIncomeTaxServiceImpl();
        IncomeTaxInfo info = service.extractIncomeTaxInfo(new FileInputStream(pdf.toFile())).block();

        List<PagamentoEfetuado> pensoes = info.getPagamentosEfetuados().stream()
                .filter(p -> "30".equals(p.getCodigo()))
                .toList();
        assertEquals(2, pensoes.size(), () -> "códigos 30=" + descrever(info.getPagamentosEfetuados()));
        assertTrue(pensoes.stream().anyMatch(p ->
                alessandro.compareTo(p.getValorPago()) == 0
                        && "741.486.484-72".equals(p.getCpfCnpj())));
        assertTrue(pensoes.stream().anyMatch(p ->
                nair.compareTo(p.getValorPago()) == 0
                        && "136.598.044-87".equals(p.getCpfCnpj())));

        BigDecimal soma = pensoes.stream()
                .map(PagamentoEfetuado::getValorPago)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        assertEquals(0, pensao.compareTo(soma));

        IrpfDeclaracaoData data = new IrpfDeclaracaoDataMapper().fromIncomeTaxInfo(info);
        IrPagamentosDeducaoAggregator aggregator = new IrPagamentosDeducaoAggregator();
        ExcelIrpfSimulacaoMapper mapper = new ExcelIrpfSimulacaoMapper(aggregator);
        ExcelIrpfDeducoesResumoHelper helper = new ExcelIrpfDeducoesResumoHelper(
                new IrSimuladorMotorService(new IrCalculoProgressivoService(), new IrDoacoesDeducaoCalculator()),
                aggregator);
        SimuladorIrpfRequest request = mapper.fromDeclaracao(data, BigDecimal.ZERO, false);

        assertEquals(0, pensao.compareTo(helper.montar(data, request, BigDecimal.ZERO, null).getPensaoJudicial()));
        assertEquals(0, pensao.compareTo(request.getPensaoAlimenticia()));
    }

    private static String descrever(List<PagamentoEfetuado> pagamentos) {
        return pagamentos.stream()
                .map(p -> p.getCodigo() + " " + p.getCpfCnpj() + " " + p.getValorPago())
                .toList()
                .toString();
    }
}
