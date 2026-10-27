package br.com.verticelabs.pdfprocessor.infrastructure.excel;

import br.com.verticelabs.pdfprocessor.application.tributacao.IrCalculoProgressivoService;
import br.com.verticelabs.pdfprocessor.application.tributacao.IrDoacoesDeducaoCalculator;
import br.com.verticelabs.pdfprocessor.application.tributacao.IrPagamentosDeducaoAggregator;
import br.com.verticelabs.pdfprocessor.application.tributacao.IrSimuladorMotorService;
import br.com.verticelabs.pdfprocessor.application.tributacao.dto.SimuladorIrpfRequest;
import br.com.verticelabs.pdfprocessor.domain.model.IrpfDeclaracaoData;
import br.com.verticelabs.pdfprocessor.domain.model.IrpfDeclaracaoData.PagamentoEfetuadoIrpf;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Código 33 (escritura) não pode repetir na linha de pensão judicial da simulação.
 */
class ExcelIrpfPensaoEscrituraTest {

    private ExcelIrpfDeducoesResumoHelper helper;
    private ExcelIrpfSimulacaoMapper mapper;

    @BeforeEach
    void setUp() {
        IrPagamentosDeducaoAggregator aggregator = new IrPagamentosDeducaoAggregator();
        helper = new ExcelIrpfDeducoesResumoHelper(
                new IrSimuladorMotorService(new IrCalculoProgressivoService(), new IrDoacoesDeducaoCalculator()),
                aggregator);
        mapper = new ExcelIrpfSimulacaoMapper(aggregator);
    }

    @Test
    void codigo33NaoDuplicaNaLinhaJudicial() {
        IrpfDeclaracaoData data = IrpfDeclaracaoData.builder()
                .anoCalendario("2016")
                .pensaoAlimenticiaJudicial(BigDecimal.ZERO)
                .pensaoAlimenticiaEscrituraPublica(new BigDecimal("55886.36"))
                .pagamentosEfetuados(List.of(
                        pagamento("10", "600.00"),
                        pagamento("26", "249.11"),
                        pagamento("33", "55886.36")))
                .build();

        SimuladorIrpfRequest request = mapper.fromDeclaracao(data, BigDecimal.ZERO, false);
        ExcelIrpfDeducoesResumoDTO deducoes = helper.montar(data, request, BigDecimal.ZERO, null);

        assertEquals(0, new BigDecimal("849.11").compareTo(deducoes.getDespesasMedicas()));
        assertEquals(0, BigDecimal.ZERO.compareTo(deducoes.getPensaoJudicial()));
        assertEquals(0, new BigDecimal("55886.36").compareTo(deducoes.getPensaoEscritura()));
        assertEquals(0, new BigDecimal("55886.36").compareTo(request.getPensaoAlimenticia()));
        assertEquals(0, new BigDecimal("56735.47").compareTo(deducoes.getTotalDeducoes()));
    }

    @Test
    void codigo30e33FicamEmLinhasDiferentes() {
        IrpfDeclaracaoData data = IrpfDeclaracaoData.builder()
                .anoCalendario("2016")
                .pensaoAlimenticiaJudicial(BigDecimal.ZERO)
                .pensaoAlimenticiaEscrituraPublica(new BigDecimal("1000.00"))
                .pagamentosEfetuados(List.of(
                        pagamento("30", "5000.00"),
                        pagamento("33", "1000.00")))
                .build();

        SimuladorIrpfRequest request = mapper.fromDeclaracao(data, BigDecimal.ZERO, false);
        ExcelIrpfDeducoesResumoDTO deducoes = helper.montar(data, request, BigDecimal.ZERO, null);

        assertEquals(0, new BigDecimal("5000.00").compareTo(deducoes.getPensaoJudicial()));
        assertEquals(0, new BigDecimal("1000.00").compareTo(deducoes.getPensaoEscritura()));
        assertEquals(0, new BigDecimal("6000.00").compareTo(request.getPensaoAlimenticia()));
        assertEquals(0, new BigDecimal("6000.00").compareTo(deducoes.getTotalDeducoes()));
    }

    private static PagamentoEfetuadoIrpf pagamento(String codigo, String valor) {
        return PagamentoEfetuadoIrpf.builder()
                .codigo(codigo)
                .valorPago(new BigDecimal(valor))
                .build();
    }
}
