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
import br.com.verticelabs.pdfprocessor.infrastructure.config.IrTributacaoParametrosUtil;
import br.com.verticelabs.pdfprocessor.infrastructure.excel.ExcelIrpfDeducoesResumoDTO;
import br.com.verticelabs.pdfprocessor.infrastructure.excel.ExcelIrpfDeducoesResumoHelper;
import br.com.verticelabs.pdfprocessor.infrastructure.excel.ExcelIrpfSimulacaoMapper;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.io.FileInputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Ilka AC 2017 — dependentes 2.275,08, instrução no teto 3.561,50 e ECA 546,94.
 */
class Ilka2017DependentesInstrucaoIncentivoTest {

    private static final Path PDF = Paths.get("temp/_ilka/irpf_2017.pdf");
    private static final Path PDF_ORIG = Paths.get(
            "temp/PASTADOCUMENTOS/Ilka Eliane Gonçalves Campelo - AEA PE divergência 2017 Rogério",
            "DeclaraçãodeImpostodeRenda_2017.pdf");

    @Test
    void extraiDependentesInstrucaoEIncentivoEca() throws Exception {
        Path pdf = Files.exists(PDF_ORIG) ? PDF_ORIG : PDF;
        Assumptions.assumeTrue(Files.exists(pdf), "PDF Ilka 2017 não encontrado");

        ITextIncomeTaxServiceImpl service = new ITextIncomeTaxServiceImpl();
        IncomeTaxInfo info = service.extractIncomeTaxInfo(new FileInputStream(pdf.toFile())).block();
        assertNotNull(info);

        assertEquals(0, new BigDecimal("2275.08").compareTo(nvl(info.getDeducoesDependentes())),
                "Linha Dependentes do RESUMO");
        if (info.getTotalDeducaoDependentes() != null) {
            assertEquals(0, new BigDecimal("2275.08").compareTo(info.getTotalDeducaoDependentes()),
                    "Total de dedução com dependentes");
        }
        assertEquals(0, new BigDecimal("3561.50").compareTo(info.getDeducoesInstrucao()));
        assertEquals(0, new BigDecimal("546.94").compareTo(info.getDeducaoIncentivo()));
        assertEquals(0, new BigDecimal("17684.61").compareTo(info.getImpostoDevidoI()));
        assertEquals(0, new BigDecimal("18231.55").compareTo(info.getImpostoDevido()));
        assertFalse(info.getDoacoesEfetuadas() == null || info.getDoacoesEfetuadas().isEmpty(),
                "Doação ECA deve ser extraída");
    }

    @Test
    void regexDependentesIgnoraPelosDependentesDoRendimento() throws Exception {
        Pattern pattern = pattern("DEDUCOES_DEPENDENTES_PATTERN");
        String resumo = """
                RENDIMENTOS TRIBUTÁVEIS
                Recebidos de Pessoa Jurídica pelo titular 121.205,83
                Recebidos de Pessoa Jurídica pelos dependentes 0,00
                TOTAL 121.205,83
                DEDUÇÕES
                Dependentes 2.275,08
                Despesas com instrução 3.561,50
                """;
        assertEquals(new BigDecimal("2275.08"), extract(resumo, pattern));
    }

    @Test
    void simulacaoAplicaTetoInstrucaoEMantemIncentivoComDoacao99() {
        IrpfDeclaracaoData data = IrpfDeclaracaoData.builder()
                .anoCalendario("2017")
                .tipoTributacao("COMPLETO")
                .cpfTitular("341.535.784-87")
                .rendimentosTributaveisTotal(new BigDecimal("121205.83"))
                .contribuicaoPrevidenciaSocial(new BigDecimal("1435.73"))
                .contribuicaoPrevidenciaPrivada(new BigDecimal("2796.68"))
                .deducaoDependentes(new BigDecimal("2275.08"))
                .despesasInstrucao(new BigDecimal("3561.50"))
                .despesasMedicas(new BigDecimal("6904.57"))
                .deducaoIncentivo(new BigDecimal("546.94"))
                .dependentes(List.of(
                        IrpfDeclaracaoData.PessoaRelacionada.builder()
                                .nome("BRENDA AUGUSTA CAMPELO DE MENEZES")
                                .cpf("068.531.414-66")
                                .build()))
                .pagamentosEfetuados(List.of(
                        IrpfDeclaracaoData.PagamentoEfetuadoIrpf.builder()
                                .codigo("01")
                                .nomeBeneficiario("SOCIEDADE CAPIBARIBE")
                                .cpfCnpj("41.229.501/0001-21")
                                .valorPago(new BigDecimal("480.00"))
                                .parcNaoDedutivel(BigDecimal.ZERO)
                                .build(),
                        IrpfDeclaracaoData.PagamentoEfetuadoIrpf.builder()
                                .codigo("01")
                                .nomeBeneficiario("SOCIEDADE CAPIBARIBE")
                                .cpfCnpj("41.229.501/0001-21")
                                .valorPago(new BigDecimal("9724.66"))
                                .parcNaoDedutivel(BigDecimal.ZERO)
                                .build()))
                .doacoesEfetuadas(List.of(
                        IrpfDeclaracaoData.DoacaoEfetuadaIrpf.builder()
                                .codigo("99")
                                .nomeBeneficiario("FUNDACAO TERRA")
                                .cpfCnpj("12.658.530/0001-00")
                                .valorDoado(new BigDecimal("546.46"))
                                .build()))
                .build();

        IrSimuladorMotorService motor = new IrSimuladorMotorService(
                new IrCalculoProgressivoService(), new IrDoacoesDeducaoCalculator());
        ExcelIrpfSimulacaoMapper excelMapper = new ExcelIrpfSimulacaoMapper(new IrPagamentosDeducaoAggregator());
        ExcelIrpfDeducoesResumoHelper helper = new ExcelIrpfDeducoesResumoHelper(
                motor, new IrPagamentosDeducaoAggregator());
        IrParametrosAnuais params = IrParametrosAnuais.builder()
                .deducaoDependente(new BigDecimal("2275.08"))
                .limiteInstrucao(new BigDecimal("3561.50"))
                .limiteDescontoSimplificado(new BigDecimal("16754.34"))
                .limiteInssDomestico(IrTributacaoParametrosUtil.limiteInssDomestico(2017))
                .build();

        SimuladorIrpfRequest request = excelMapper.fromDeclaracao(data, data.getContribuicaoPrevidenciaPrivada(), true);
        ExcelIrpfDeducoesResumoDTO simulacao = helper.montar(
                data, request, data.getContribuicaoPrevidenciaPrivada(), params);

        assertEquals(0, new BigDecimal("2275.08").compareTo(simulacao.getDependentes()));
        assertEquals(0, new BigDecimal("3561.50").compareTo(simulacao.getDespesasInstrucao()),
                "Simulação deve aplicar teto de instrução, não o total pago 10.204,66");
        assertEquals(0, new BigDecimal("546.94").compareTo(request.getDeducaoIncentivo()));
        assertNull(excelMapper.fromDeclaracao(
                IrpfDeclaracaoData.builder()
                        .anoCalendario("2017")
                        .deducaoDependentes(BigDecimal.ZERO)
                        .dependentes(List.of(IrpfDeclaracaoData.PessoaRelacionada.builder().nome("B").build()))
                        .build(),
                BigDecimal.ZERO).getDeducaoDependentesDeclarada());
    }

    @Test
    void simulacaoRespeitaTetoInstrucaoEIncentivo() throws Exception {
        Path pdf = Files.exists(PDF_ORIG) ? PDF_ORIG : PDF;
        Assumptions.assumeTrue(Files.exists(pdf), "PDF Ilka 2017 não encontrado");

        ITextIncomeTaxServiceImpl service = new ITextIncomeTaxServiceImpl();
        IncomeTaxInfo info = service.extractIncomeTaxInfo(new FileInputStream(pdf.toFile())).block();
        IrpfDeclaracaoData data = new IrpfDeclaracaoDataMapper().fromIncomeTaxInfo(info);

        IrSimuladorMotorService motor = new IrSimuladorMotorService(
                new IrCalculoProgressivoService(), new IrDoacoesDeducaoCalculator());
        ExcelIrpfSimulacaoMapper excelMapper = new ExcelIrpfSimulacaoMapper(new IrPagamentosDeducaoAggregator());
        ExcelIrpfDeducoesResumoHelper helper = new ExcelIrpfDeducoesResumoHelper(
                motor, new IrPagamentosDeducaoAggregator());

        IrParametrosAnuais params = IrParametrosAnuais.builder()
                .deducaoDependente(new BigDecimal("2275.08"))
                .limiteInstrucao(new BigDecimal("3561.50"))
                .limiteDescontoSimplificado(new BigDecimal("16754.34"))
                .limiteInssDomestico(IrTributacaoParametrosUtil.limiteInssDomestico(2017))
                .build();

        SimuladorIrpfRequest request = excelMapper.fromDeclaracao(data, data.getContribuicaoPrevidenciaPrivada(), true);
        ExcelIrpfDeducoesResumoDTO espelho = helper.montarConformeDeclaracao(data);
        ExcelIrpfDeducoesResumoDTO simulacao = helper.montar(data, request, data.getContribuicaoPrevidenciaPrivada(), params);

        assertEquals(0, new BigDecimal("2275.08").compareTo(espelho.getDependentes()));
        assertEquals(0, new BigDecimal("3561.50").compareTo(espelho.getDespesasInstrucao()));
        assertEquals(0, new BigDecimal("2275.08").compareTo(simulacao.getDependentes()));
        assertEquals(0, new BigDecimal("3561.50").compareTo(simulacao.getDespesasInstrucao()),
                "Simulação deve aplicar teto de instrução, não o total pago 10.204,66");
        assertEquals(0, new BigDecimal("546.94").compareTo(request.getDeducaoIncentivo()));
    }

    private static BigDecimal nvl(BigDecimal v) {
        return v != null ? v : BigDecimal.ZERO;
    }

    private static Pattern pattern(String fieldName) throws Exception {
        Field field = ITextIncomeTaxServiceImpl.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        return (Pattern) field.get(null);
    }

    private static BigDecimal extract(String text, Pattern regex) throws Exception {
        Method method = ITextIncomeTaxServiceImpl.class.getDeclaredMethod(
                "extractValorMonetario", String.class, Pattern.class);
        method.setAccessible(true);
        return (BigDecimal) method.invoke(new ITextIncomeTaxServiceImpl(), text, regex);
    }
}
