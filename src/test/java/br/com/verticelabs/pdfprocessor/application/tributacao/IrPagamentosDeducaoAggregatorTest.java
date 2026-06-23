package br.com.verticelabs.pdfprocessor.application.tributacao;

import br.com.verticelabs.pdfprocessor.application.tributacao.dto.DeducoesPagamentosDTO;
import br.com.verticelabs.pdfprocessor.application.tributacao.dto.DoacoesBrutasDTO;
import br.com.verticelabs.pdfprocessor.domain.model.IrpfDeclaracaoData;
import br.com.verticelabs.pdfprocessor.domain.model.IrpfDeclaracaoData.DoacaoEfetuadaIrpf;
import br.com.verticelabs.pdfprocessor.domain.model.IrpfDeclaracaoData.PagamentoEfetuadoIrpf;
import br.com.verticelabs.pdfprocessor.domain.model.IrpfDeclaracaoData.PessoaRelacionada;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class IrPagamentosDeducaoAggregatorTest {

    private IrPagamentosDeducaoAggregator aggregator;

    @BeforeEach
    void setUp() {
        aggregator = new IrPagamentosDeducaoAggregator();
    }

    @Test
    void valorDedutivel_subtraiParcNaoDedutivel() {
        PagamentoEfetuadoIrpf p = PagamentoEfetuadoIrpf.builder()
                .codigo("26")
                .valorPago(new BigDecimal("1000.00"))
                .parcNaoDedutivel(new BigDecimal("200.00"))
                .build();
        assertEquals(new BigDecimal("800.00"), aggregator.valorDedutivel(p));
    }

    @Test
    void agregaSaudeCodigo26() {
        IrpfDeclaracaoData data = IrpfDeclaracaoData.builder().build();
        List<PagamentoEfetuadoIrpf> pagamentos = List.of(
                pagamento("26", "500.00", null),
                pagamento("10", "300.00", null));

        DeducoesPagamentosDTO agg = aggregator.aggregate(pagamentos, data);

        assertTrue(agg.isFonteGranular());
        assertEquals(new BigDecimal("800.00"), agg.getDespesasMedicas());
    }

    @Test
    void agregaCodigo50InssDomestico() {
        IrpfDeclaracaoData data = IrpfDeclaracaoData.builder().build();
        List<PagamentoEfetuadoIrpf> pagamentos = List.of(pagamento("50", "1092.00", null));

        DeducoesPagamentosDTO agg = aggregator.aggregate(pagamentos, data);

        assertEquals(new BigDecimal("1092.00"), agg.getPrevidenciaEmpregadoDomestico());
    }

    @Test
    void agregaPgblCodigos36e37() {
        IrpfDeclaracaoData data = IrpfDeclaracaoData.builder().build();
        List<PagamentoEfetuadoIrpf> pagamentos = List.of(
                pagamento("36", "10000.00", null),
                pagamento("37", "2000.00", null));

        DeducoesPagamentosDTO agg = aggregator.aggregate(pagamentos, data);

        assertEquals(new BigDecimal("12000.00"), agg.getPrevidenciaPrivada());
    }

    @Test
    void agregaInstrucaoPorCpfDependente() {
        IrpfDeclaracaoData data = IrpfDeclaracaoData.builder()
                .cpfTitular("11111111111")
                .dependentes(List.of(PessoaRelacionada.builder().cpf("22222222222").build()))
                .build();

        List<PagamentoEfetuadoIrpf> pagamentos = List.of(
                PagamentoEfetuadoIrpf.builder()
                        .codigo("01")
                        .cpfCnpj("222.222.222-22")
                        .valorPago(new BigDecimal("4000.00"))
                        .build(),
                PagamentoEfetuadoIrpf.builder()
                        .codigo("01")
                        .cpfCnpj("111.111.111-11")
                        .valorPago(new BigDecimal("2000.00"))
                        .build());

        DeducoesPagamentosDTO agg = aggregator.aggregate(pagamentos, data);

        assertEquals(new BigDecimal("2000.00"), agg.getDespesasInstrucaoTitular());
        assertEquals(1, agg.getDespesasInstrucaoDependentes().size());
        assertEquals(new BigDecimal("4000.00"), agg.getDespesasInstrucaoDependentes().get(0));
    }

    @Test
    void agregaPensaoCodigos30e33() {
        IrpfDeclaracaoData data = IrpfDeclaracaoData.builder().build();
        List<PagamentoEfetuadoIrpf> pagamentos = List.of(
                pagamento("30", "5000.00", null),
                pagamento("33", "1000.00", null));

        DeducoesPagamentosDTO agg = aggregator.aggregate(pagamentos, data);

        assertEquals(new BigDecimal("6000.00"), agg.getPensaoAlimenticia());
    }

    @Test
    void agregaDoacoesPorCodigo() {
        List<DoacaoEfetuadaIrpf> doacoes = List.of(
                doacao("40", "1000.00"),
                doacao("41", "500.00"),
                doacao("44", "300.00"),
                doacao("45", "200.00"));

        DoacoesBrutasDTO brutas = aggregator.aggregateDoacoes(doacoes);

        assertEquals(new BigDecimal("1500.00"), brutas.getDeducaoIncentivoBruta());
        assertEquals(new BigDecimal("300.00"), brutas.getDedPrononBruta());
        assertEquals(new BigDecimal("200.00"), brutas.getDedPronasBruta());
    }

    @Test
    void listaVazia_naoEhGranular() {
        DeducoesPagamentosDTO agg = aggregator.aggregate(List.of(), IrpfDeclaracaoData.builder().build());
        assertFalse(agg.isFonteGranular());
    }

    private PagamentoEfetuadoIrpf pagamento(String codigo, String valor, String parc) {
        return PagamentoEfetuadoIrpf.builder()
                .codigo(codigo)
                .valorPago(new BigDecimal(valor))
                .parcNaoDedutivel(parc != null ? new BigDecimal(parc) : null)
                .build();
    }

    private DoacaoEfetuadaIrpf doacao(String codigo, String valor) {
        return DoacaoEfetuadaIrpf.builder()
                .codigo(codigo)
                .valorDoado(new BigDecimal(valor))
                .build();
    }
}
