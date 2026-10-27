package br.com.verticelabs.pdfprocessor.infrastructure.incometax;

import br.com.verticelabs.pdfprocessor.application.incometax.IrpfDeclaracaoDataMapper;
import br.com.verticelabs.pdfprocessor.application.tributacao.IrCalculoProgressivoService;
import br.com.verticelabs.pdfprocessor.application.tributacao.IrDoacoesDeducaoCalculator;
import br.com.verticelabs.pdfprocessor.application.tributacao.IrPagamentosDeducaoAggregator;
import br.com.verticelabs.pdfprocessor.application.tributacao.IrSimuladorMotorService;
import br.com.verticelabs.pdfprocessor.application.tributacao.dto.SimuladorIrpfRequest;
import br.com.verticelabs.pdfprocessor.domain.model.IrParametrosAnuais;
import br.com.verticelabs.pdfprocessor.domain.model.IrpfDeclaracaoData;
import br.com.verticelabs.pdfprocessor.domain.service.IncomeTaxDeclarationService.IncomeTaxInfo;
import br.com.verticelabs.pdfprocessor.domain.service.IncomeTaxDeclarationService.IncomeTaxInfo.PagamentoEfetuado;
import br.com.verticelabs.pdfprocessor.infrastructure.config.IrTributacaoParametrosUtil;
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
 * Alessandra AC 2016: duas escolas (cód. 01) sob Dependente: Bruna e Debora.
 * Sem o cabeçalho, o CNPJ ia para o titular e o teto ficava 3.561,50.
 */
class Alessandra2016InstrucaoDependentesTest {

    private static final Path PDF = Paths.get(
            "temp/PASTADOCUMENTOS/Alessandra Christine Barros dos Santos - APCEF PE/DeclaraçãodeImpostodeRenda_2016RET1.pdf");
    private static final BigDecimal INSTRUCAO = new BigDecimal("7123.00");
    private static final BigDecimal LIMITE = new BigDecimal("3561.50");

    @Test
    void contextoDependenteNaoSomeComCabecalhoRepetido() {
        String section = """
                PAGAMENTOS EFETUADOS
                Titular
                70 IKA 00.821.913/0001-79 10.450,00 0,00
                Dependente: BRUNA SANTOS DANTAS GOMES
                01 FACULDADE MARISTA 10.847.382/0024-33 9.109,80 0,00
                Dependente: DEBORA SANTOS DANTAS GOMES
                PAGAMENTOS EFETUADOS
                01 COLEGIO UNICO 10.435.695/0001-98 12.460,80 0,00
                """;
        int posBruna = section.indexOf("01 FACULDADE");
        int posDebora = section.indexOf("01 COLEGIO");
        assertEquals("BRUNA SANTOS DANTAS GOMES",
                ITextIncomeTaxServiceImpl.resolverNomePessoaVinculada(section, posBruna));
        assertEquals("DEBORA SANTOS DANTAS GOMES",
                ITextIncomeTaxServiceImpl.resolverNomePessoaVinculada(section, posDebora));
    }

    @Test
    void pdf2016SimulacaoInstrucao7123() throws Exception {
        Assumptions.assumeTrue(Files.isRegularFile(PDF), "PDF Alessandra 2016 não encontrado: " + PDF.toAbsolutePath());

        ITextIncomeTaxServiceImpl service = new ITextIncomeTaxServiceImpl();
        IncomeTaxInfo info = service.extractIncomeTaxInfo(new FileInputStream(PDF.toFile())).block();

        List<PagamentoEfetuado> instrucao = info.getPagamentosEfetuados().stream()
                .filter(p -> "01".equals(p.getCodigo()))
                .toList();
        assertEquals(2, instrucao.size(), () -> "códigos 01=" + instrucao);
        assertTrue(instrucao.stream().anyMatch(p ->
                "BRUNA SANTOS DANTAS GOMES".equalsIgnoreCase(p.getNomePessoaVinculada())
                        && new BigDecimal("9109.80").compareTo(p.getValorPago()) == 0));
        assertTrue(instrucao.stream().anyMatch(p ->
                "DEBORA SANTOS DANTAS GOMES".equalsIgnoreCase(p.getNomePessoaVinculada())
                        && new BigDecimal("12460.80").compareTo(p.getValorPago()) == 0));

        assertEquals(0, INSTRUCAO.compareTo(info.getDeducoesInstrucao()));

        IrpfDeclaracaoData data = new IrpfDeclaracaoDataMapper().fromIncomeTaxInfo(info);
        IrPagamentosDeducaoAggregator aggregator = new IrPagamentosDeducaoAggregator();
        ExcelIrpfSimulacaoMapper mapper = new ExcelIrpfSimulacaoMapper(aggregator);
        ExcelIrpfDeducoesResumoHelper helper = new ExcelIrpfDeducoesResumoHelper(
                new IrSimuladorMotorService(new IrCalculoProgressivoService(), new IrDoacoesDeducaoCalculator()),
                aggregator);
        IrParametrosAnuais params = IrParametrosAnuais.builder()
                .deducaoDependente(new BigDecimal("2275.08"))
                .limiteInstrucao(LIMITE)
                .limiteDescontoSimplificado(new BigDecimal("16754.34"))
                .limiteInssDomestico(IrTributacaoParametrosUtil.limiteInssDomestico(2016))
                .build();

        SimuladorIrpfRequest request = mapper.fromDeclaracao(data, BigDecimal.ZERO, false);
        assertEquals(2, request.getDespesasInstrucaoDependentes().size());
        assertEquals(0, BigDecimal.ZERO.compareTo(nvl(request.getDespesasInstrucaoTitular())));

        var simulacao = helper.montar(data, request, BigDecimal.ZERO, params);
        assertEquals(0, INSTRUCAO.compareTo(simulacao.getDespesasInstrucao()),
                "simulação instrução=" + simulacao.getDespesasInstrucao());
    }

    private static BigDecimal nvl(BigDecimal v) {
        return v != null ? v : BigDecimal.ZERO;
    }
}
