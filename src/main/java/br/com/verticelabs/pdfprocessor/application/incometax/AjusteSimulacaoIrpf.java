package br.com.verticelabs.pdfprocessor.application.incometax;

import br.com.verticelabs.pdfprocessor.domain.model.IrpfDeclaracaoData;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Ajustes manuais que valem só na simulação com aproveitamento das contribuições extras.
 * Não altera o objeto da declaração entregue.
 */
public final class AjusteSimulacaoIrpf {

    public static final String ROTULO_IMPOSTO_DEPOSITO =
            "Imposto Recolhido Através de Depósito Judicial Processo Cível";

    private static final RoundingMode RM = RoundingMode.HALF_UP;

    private AjusteSimulacaoIrpf() {
    }

    /**
     * Cópia para o motor e para o bloco 2. Sem ajuste de rendimentos, devolve a própria declaração.
     */
    public static IrpfDeclaracaoData paraSimulacao(IrpfDeclaracaoData data) {
        if (data == null || data.getAjusteSimulacaoRendimentosPjTitular() == null) {
            return data;
        }
        BigDecimal manual = data.getAjusteSimulacaoRendimentosPjTitular();
        BigDecimal pjDeclarado = nvl(data.getRendimentosTributaveisTitularPJ());
        BigDecimal total = nvl(data.getRendimentosTributaveisTotal()).subtract(pjDeclarado).add(manual);
        return data.toBuilder()
                .rendimentosTributaveisTitularPJ(manual)
                .rendimentosTributaveisTotal(total.setScale(2, RM))
                .build();
    }

    /** {@code null} quando o lançamento não foi informado. */
    public static BigDecimal impostoDepositoJudicialCivel(IrpfDeclaracaoData data) {
        if (data == null) {
            return null;
        }
        return data.getAjusteSimulacaoImpostoDepositoJudicialCivel();
    }

    public static BigDecimal somarImpostoDepositoJudicialCivel(BigDecimal totalPago, IrpfDeclaracaoData data) {
        BigDecimal extra = impostoDepositoJudicialCivel(data);
        if (extra == null) {
            return nvl(totalPago);
        }
        return nvl(totalPago).add(extra);
    }

    /** Mantém os lançamentos manuais quando a extração substitui o {@code irpfData}. */
    public static void preservar(IrpfDeclaracaoData anterior, IrpfDeclaracaoData novo) {
        if (anterior == null || novo == null) {
            return;
        }
        novo.setAjusteSimulacaoRendimentosPjTitular(anterior.getAjusteSimulacaoRendimentosPjTitular());
        novo.setAjusteSimulacaoImpostoDepositoJudicialCivel(anterior.getAjusteSimulacaoImpostoDepositoJudicialCivel());
        novo.setAjusteSimulacaoPrevOficialPublica(anterior.getAjusteSimulacaoPrevOficialPublica());
        novo.setAjusteSimulacaoPrevOficialRra(anterior.getAjusteSimulacaoPrevOficialRra());
        novo.setAjusteSimulacaoPrevComplementar(anterior.getAjusteSimulacaoPrevComplementar());
    }

    private static BigDecimal nvl(BigDecimal valor) {
        return valor != null ? valor : BigDecimal.ZERO;
    }
}
