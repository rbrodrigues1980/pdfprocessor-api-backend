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
import br.com.verticelabs.pdfprocessor.infrastructure.excel.ExcelIrpfDeducoesResumoDTO;
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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Maria de Fátima AC 2024 — PDF concatena original (médicas 33.816,04) e retificadora (21.319,04).
 * Deve prevalecer a última entrega: só os dois planos de saúde cód. 26.
 */
class Fatima2024DespesasMedicasTest {

    private static final Path PDF = Paths.get("temp/_fatima/irpf_2024.pdf");
    private static final BigDecimal MEDICAS_RETIFICADORA = new BigDecimal("21319.04");

    @Test
    void recortarUltimaDeclaracaoDescartaEntregaAnterior() {
        String texto = """
                === PAGINA 1 ===
                IDENTIFICAÇÃO DO CONTRIBUINTE
                PAGAMENTOS EFETUADOS
                11 EVERARDO 235.714.464-53 15.000,00 5.103,00
                RESUMO
                Despesas médicas 33.816,04
                DADOS E IDENTIFICAÇÃO DO IMÓVEL EXPLORADO - BRASIL
                === PAGINA 13 ===
                IDENTIFICAÇÃO DO CONTRIBUINTE
                PAGAMENTOS EFETUADOS
                26 CAIXA ECONOMICA FEDERAL 00.360.305/0001-04 7.136,84 0,00
                26 HAPVIDA 63.554.067/0001-98 14.182,20 0,00
                RESUMO
                Despesas médicas 21.319,04
                """;

        String vigente = ITextIncomeTaxServiceImpl.recortarUltimaDeclaracao(texto);
        assertTrue(vigente.contains("21.319,04"));
        assertFalse(vigente.contains("33.816,04"));
        assertFalse(vigente.contains("EVERARDO"));
        assertTrue(vigente.contains("HAPVIDA"));
    }

    @Test
    void pdfUsaRetificadoraMedicas21319() throws Exception {
        Assumptions.assumeTrue(Files.exists(PDF), "PDF Fátima 2024 não encontrado");

        ITextIncomeTaxServiceImpl service = new ITextIncomeTaxServiceImpl();
        IncomeTaxInfo info = service.extractIncomeTaxInfo(new FileInputStream(PDF.toFile())).block();
        assertEquals(0, MEDICAS_RETIFICADORA.compareTo(info.getDeducoesMedicas()),
                "RESUMO vigente deve ser 21.319,04; era=" + info.getDeducoesMedicas());

        List<PagamentoEfetuado> pagamentos = info.getPagamentosEfetuados();
        BigDecimal medicasPagamentos = pagamentos.stream()
                .filter(p -> p.getCodigo() != null && List.of("10", "11", "21", "26").contains(p.getCodigo().trim()))
                .map(p -> {
                    BigDecimal pago = p.getValorPago() != null ? p.getValorPago() : BigDecimal.ZERO;
                    BigDecimal parc = p.getParcNaoDedutivel() != null ? p.getParcNaoDedutivel() : BigDecimal.ZERO;
                    return pago.subtract(parc);
                })
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        assertEquals(0, MEDICAS_RETIFICADORA.compareTo(medicasPagamentos.setScale(2)),
                "Pagamentos de saúde da retificadora; era=" + medicasPagamentos);
        assertTrue(pagamentos.stream().noneMatch(p -> "11".equals(p.getCodigo())),
                "Não deve trazer o dentista da entrega original");
    }

    @Test
    void excelEspelhoESimulacaoUsam21319() throws Exception {
        Assumptions.assumeTrue(Files.exists(PDF), "PDF Fátima 2024 não encontrado");

        ITextIncomeTaxServiceImpl service = new ITextIncomeTaxServiceImpl();
        IncomeTaxInfo info = service.extractIncomeTaxInfo(new FileInputStream(PDF.toFile())).block();
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
                .limiteInssDomestico(IrTributacaoParametrosUtil.limiteInssDomestico(2024))
                .build();

        SimuladorIrpfRequest request = excelMapper.fromDeclaracao(data, data.getContribuicaoPrevidenciaPrivada(), true);
        ExcelIrpfDeducoesResumoDTO espelho = helper.montarConformeDeclaracao(data);
        ExcelIrpfDeducoesResumoDTO simulacao = helper.montar(
                data, request, data.getContribuicaoPrevidenciaPrivada(), params);

        assertEquals(0, MEDICAS_RETIFICADORA.compareTo(espelho.getDespesasMedicas()));
        assertEquals(0, MEDICAS_RETIFICADORA.compareTo(simulacao.getDespesasMedicas()));
        assertEquals(0, MEDICAS_RETIFICADORA.compareTo(request.getDespesasMedicas()));
    }
}
