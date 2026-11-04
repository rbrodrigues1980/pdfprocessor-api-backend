package br.com.verticelabs.pdfprocessor.application.incometax;

import br.com.verticelabs.pdfprocessor.domain.model.IrpfDeclaracaoData;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Dados gravados antes das correções do parser (Célia AEA): corrigidos na leitura.
 */
class IrpfDeclaracaoConsistenciaTest {

    @Test
    void simplificadoSemAlugueisNoTotalEBaseCalculadaDoTotalErrado() {
        IrpfDeclaracaoData antigo = simplificado2016()
                .rendimentosTributaveisTotal(v("67963.80"))
                .baseCalculoImposto(v("53147.04"))
                .build();

        IrpfDeclaracaoData data = IrpfDeclaracaoConsistencia.aplicar(antigo);

        assertValor("74083.80", data.getRendimentosTributaveisTotal());
        assertValor("59267.04", data.getBaseCalculoImposto());
        assertValor("6120.00", data.getRendimentosTributaveisTitularPF());
        assertValor("67963.80", antigo.getRendimentosTributaveisTotal());
    }

    @Test
    void simplificadoComDescontoNoTetoSomaAlugueis() {
        IrpfDeclaracaoData antigo = IrpfDeclaracaoData.builder()
                .anoCalendario("2017")
                .tipoTributacao("SIMPLIFICADO")
                .rendimentosTributaveisTitularPJ(v("73939.20"))
                .rendimentosTributaveisTitularPF(v("9940.63"))
                .rendimentosTributaveisTotal(v("73939.20"))
                .descontoSimplificado(v("16754.34"))
                .baseCalculoImposto(v("57184.86"))
                .build();

        IrpfDeclaracaoData data = IrpfDeclaracaoConsistencia.aplicar(antigo);

        assertValor("83879.83", data.getRendimentosTributaveisTotal());
        assertValor("67125.49", data.getBaseCalculoImposto());
    }

    @Test
    void semLinhaPfDerivaAlugueisPelaBaseMaisDesconto() {
        IrpfDeclaracaoData antigo = simplificado2016()
                .rendimentosTributaveisTitularPF(null)
                .rendimentosTributaveisTotal(v("67963.80"))
                .baseCalculoImposto(v("59267.04"))
                .build();

        IrpfDeclaracaoData data = IrpfDeclaracaoConsistencia.aplicar(antigo);

        assertValor("74083.80", data.getRendimentosTributaveisTotal());
        assertValor("59267.04", data.getBaseCalculoImposto());
        assertValor("6120.00", data.getRendimentosTributaveisTitularPF());
    }

    @Test
    void completoImpostoDevidoIComValorDaRestituicao() {
        IrpfDeclaracaoData data = IrpfDeclaracaoConsistencia.aplicar(completo2022()
                .impostoDevidoI(v("2753.67"))
                .build());

        assertValor("14535.81", data.getImpostoDevidoI());
        assertValor("14535.81", data.getImpostoDevido());
        assertValor("2753.67", data.getImpostoRestituir());
    }

    @Test
    void completoImpostoDevidoTambemCopiadoDaRestituicao() {
        IrpfDeclaracaoData data = IrpfDeclaracaoConsistencia.aplicar(completo2022()
                .impostoDevido(v("2753.67"))
                .impostoDevidoI(v("2753.67"))
                .build());

        assertValor("14535.81", data.getImpostoDevidoI());
        assertValor("14535.81", data.getImpostoDevido());
    }

    @Test
    void completoTotalSemAlugueisDeslocaBase() {
        IrpfDeclaracaoData data = IrpfDeclaracaoConsistencia.aplicar(completo2022()
                .rendimentosTributaveisTotal(v("101164.32"))
                .baseCalculoImposto(v("81919.21"))
                .build());

        assertValor("110038.32", data.getRendimentosTributaveisTotal());
        assertValor("90793.21", data.getBaseCalculoImposto());
    }

    @Test
    void restituirESaldoPelaDiferencaEntrePagoEDevido() {
        IrpfDeclaracaoData data = IrpfDeclaracaoConsistencia.aplicar(completo2022()
                .impostoRestituir(v("1000.00"))
                .saldoImpostoPagar(v("100.00"))
                .build());

        assertValor("2753.67", data.getImpostoRestituir());
        assertValor("0.00", data.getSaldoImpostoPagar());
    }

    @Test
    void totalDevidoIgualRestituirNaoRecalculaResultado() {
        IrpfDeclaracaoData data = IrpfDeclaracaoConsistencia.aplicar(completo2022()
                .totalImpostoDevido(v("2753.67"))
                .build());

        assertValor("2753.67", data.getImpostoRestituir());
    }

    @Test
    void impostoDevidoIComInssDomesticoNaoEAlterado() {
        IrpfDeclaracaoData original = IrpfDeclaracaoData.builder()
                .anoCalendario("2016")
                .tipoTributacao("COMPLETO")
                .impostoDevido(v("10000.00"))
                .deducaoIncentivo(v("300.00"))
                .impostoDevidoI(v("9700.00"))
                .contribuicaoPatronalPrevidenciaSocial(v("1093.77"))
                .impostoDevidoII(v("8606.23"))
                .totalImpostoDevido(v("8606.23"))
                .impostoPagoTotal(v("9000.00"))
                .impostoRestituir(v("393.77"))
                .saldoImpostoPagar(v("0.00"))
                .build();

        IrpfDeclaracaoData data = IrpfDeclaracaoConsistencia.aplicar(original);

        assertEquals(original, data);
    }

    @Test
    void declaracaoCorretaEIdempotente() {
        IrpfDeclaracaoData correto = completo2022().build();
        assertEquals(correto, IrpfDeclaracaoConsistencia.aplicar(correto));

        IrpfDeclaracaoData corrigido = IrpfDeclaracaoConsistencia.aplicar(simplificado2016()
                .rendimentosTributaveisTotal(v("67963.80"))
                .baseCalculoImposto(v("53147.04"))
                .build());
        assertEquals(corrigido, IrpfDeclaracaoConsistencia.aplicar(corrigido));
    }

    @Test
    void nuloPermaneceNulo() {
        assertNull(IrpfDeclaracaoConsistencia.aplicar(null));
    }

    private static IrpfDeclaracaoData.IrpfDeclaracaoDataBuilder simplificado2016() {
        return IrpfDeclaracaoData.builder()
                .anoCalendario("2016")
                .tipoTributacao("SIMPLIFICADO")
                .rendimentosTributaveisTitularPJ(v("67963.80"))
                .rendimentosTributaveisTitularPF(v("6120.00"))
                .rendimentosTributaveisTotal(v("74083.80"))
                .descontoSimplificado(v("14816.76"))
                .baseCalculoImposto(v("59267.04"))
                .impostoDevido(v("5866.11"))
                .impostoDevidoI(v("5866.11"))
                .totalImpostoDevido(v("5866.11"))
                .impostoPagoTotal(v("7981.24"))
                .impostoRestituir(v("2115.13"))
                .saldoImpostoPagar(v("0.00"));
    }

    private static IrpfDeclaracaoData.IrpfDeclaracaoDataBuilder completo2022() {
        return IrpfDeclaracaoData.builder()
                .anoCalendario("2022")
                .tipoTributacao("COMPLETO")
                .rendimentosTributaveisTitularPJ(v("101164.32"))
                .rendimentosTributaveisTitularPF(v("8874.00"))
                .rendimentosTributaveisTotal(v("110038.32"))
                .deducoesTotal(v("19245.11"))
                .baseCalculoImposto(v("90793.21"))
                .impostoDevido(v("14535.81"))
                .deducaoIncentivo(v("0.00"))
                .impostoDevidoI(v("14535.81"))
                .impostoSobreRRA(v("0.00"))
                .totalImpostoDevido(v("14535.81"))
                .impostoPagoTotal(v("17289.48"))
                .impostoRestituir(v("2753.67"))
                .saldoImpostoPagar(v("0.00"));
    }

    private static BigDecimal v(String s) {
        return new BigDecimal(s);
    }

    private static void assertValor(String esperado, BigDecimal atual) {
        assertEquals(0, new BigDecimal(esperado).compareTo(atual),
                () -> "esperado " + esperado + ", obtido " + atual);
    }
}
