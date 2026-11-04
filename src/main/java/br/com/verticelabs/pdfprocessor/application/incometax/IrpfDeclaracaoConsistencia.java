package br.com.verticelabs.pdfprocessor.application.incometax;

import br.com.verticelabs.pdfprocessor.domain.model.IrpfDeclaracaoData;
import br.com.verticelabs.pdfprocessor.infrastructure.incometax.IncomeTaxRendimentosReconciler;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Confere as identidades aritméticas do RESUMO da declaração e corrige campos que a extração
 * gravou errados (dados antigos no Mongo são corrigidos na leitura, sem reprocessar):
 * <ul>
 * <li>TOTAL de rendimentos tributáveis = soma das linhas (PJ, PF/aluguéis, RRA, rural) e
 * = base de cálculo + desconto simplificado (ou + total de deduções no Completo);</li>
 * <li>Imposto devido I = Imposto devido − Dedução de incentivo = Total devido − RRA (+ INSS doméstico);</li>
 * <li>Imposto a restituir / saldo a pagar = diferença entre total pago e total devido.</li>
 * </ul>
 * Idempotente: aplicar de novo sobre o resultado não altera nada.
 */
@Slf4j
public final class IrpfDeclaracaoConsistencia {

    private static final BigDecimal TOLERANCIA = new BigDecimal("0.02");

    private IrpfDeclaracaoConsistencia() {
    }

    public static IrpfDeclaracaoData aplicar(IrpfDeclaracaoData original) {
        if (original == null) {
            return null;
        }
        IrpfDeclaracaoData data = original.toBuilder().build();
        corrigirRendimentosEBase(data);
        corrigirImpostoDevidoI(data);
        corrigirResultado(data);
        return data;
    }

    private static void corrigirRendimentosEBase(IrpfDeclaracaoData data) {
        BigDecimal atual = data.getRendimentosTributaveisTotal();
        BigDecimal reconciliado = IncomeTaxRendimentosReconciler.reconciliarTotal(
                atual,
                data.getRendimentosTributaveisTitularPJ(),
                data.getRendimentosTributaveisDependentesPJ(),
                data.getRendimentosTributaveisTitularPF(),
                data.getRendimentosTributaveisDependentesPF(),
                data.getRendimentosAcumuladosTitular(),
                data.getRendimentosAcumuladosDependentes(),
                data.getResultadoAtividadeRural());
        BigDecimal abatimento = abatimentoDaBase(data);
        BigDecimal pelaBase = data.getBaseCalculoImposto() != null && abatimento != null
                ? escala(data.getBaseCalculoImposto().add(abatimento))
                : null;

        BigDecimal total = maior(reconciliado, pelaBase);
        if (total != null && (atual == null || total.subtract(atual).compareTo(TOLERANCIA) > 0)) {
            log.warn("⚠️ IRPF {} {}: TOTAL de rendimentos tributáveis {} → {} (soma das linhas / base + abatimento)",
                    data.getCpfTitular(), data.getAnoCalendario(), atual, total);
            data.setRendimentosTributaveisTotal(total);
            derivarPessoaFisicaTitular(data, total);
            if (atual != null && !proximo(total, pelaBase)) {
                deslocarBase(data, total.subtract(atual));
            }
        }

        total = data.getRendimentosTributaveisTotal();
        if (isSimplificado(data) && total != null && abatimento != null && abatimento.compareTo(total) < 0) {
            BigDecimal base = escala(total.subtract(abatimento));
            BigDecimal baseAtual = data.getBaseCalculoImposto();
            if (baseAtual == null || base.subtract(baseAtual).compareTo(TOLERANCIA) > 0) {
                log.warn("⚠️ IRPF {} {}: base de cálculo {} → {} (rendimentos − desconto simplificado)",
                        data.getCpfTitular(), data.getAnoCalendario(), baseAtual, base);
                data.setBaseCalculoImposto(base);
            }
        }
    }

    /**
     * Base gravada a partir do TOTAL errado (rendimentos − deduções): acompanha a correção do TOTAL.
     */
    private static void deslocarBase(IrpfDeclaracaoData data, BigDecimal delta) {
        BigDecimal baseAtual = data.getBaseCalculoImposto();
        if (baseAtual == null) {
            return;
        }
        BigDecimal base = escala(baseAtual.add(delta));
        log.warn("⚠️ IRPF {} {}: base de cálculo {} → {} (acompanha o TOTAL de rendimentos)",
                data.getCpfTitular(), data.getAnoCalendario(), baseAtual, base);
        data.setBaseCalculoImposto(base);
    }

    /**
     * Aluguéis (PF titular) ausentes nos dados antigos: diferença entre o TOTAL corrigido e as demais linhas.
     */
    private static void derivarPessoaFisicaTitular(IrpfDeclaracaoData data, BigDecimal total) {
        if (positivo(data.getRendimentosTributaveisTitularPF())) {
            return;
        }
        BigDecimal outras = IncomeTaxRendimentosReconciler.somaLinhas(
                data.getRendimentosTributaveisTitularPJ(),
                data.getRendimentosTributaveisDependentesPJ(),
                null,
                data.getRendimentosTributaveisDependentesPF(),
                data.getRendimentosAcumuladosTitular(),
                data.getRendimentosAcumuladosDependentes(),
                data.getResultadoAtividadeRural());
        if (!positivo(outras)) {
            return;
        }
        BigDecimal pf = escala(total.subtract(outras));
        if (pf.compareTo(TOLERANCIA) > 0) {
            log.warn("⚠️ IRPF {} {}: rendimentos PF titular derivados = {}",
                    data.getCpfTitular(), data.getAnoCalendario(), pf);
            data.setRendimentosTributaveisTitularPF(pf);
        }
    }

    /** Desconto simplificado ou total de deduções legais. */
    private static BigDecimal abatimentoDaBase(IrpfDeclaracaoData data) {
        if (isSimplificado(data)) {
            return positivo(data.getDescontoSimplificado()) ? data.getDescontoSimplificado() : null;
        }
        return positivo(data.getDeducoesTotal()) ? data.getDeducoesTotal() : null;
    }

    private static void corrigirImpostoDevidoI(IrpfDeclaracaoData data) {
        BigDecimal atual = data.getImpostoDevidoI();
        BigDecimal pelaDeducao = data.getImpostoDevido() != null
                ? escala(data.getImpostoDevido().subtract(nvl(data.getDeducaoIncentivo())).max(BigDecimal.ZERO))
                : null;
        BigDecimal peloTotal = data.getTotalImpostoDevido() != null
                ? escala(data.getTotalImpostoDevido()
                        .subtract(nvl(data.getImpostoSobreRRA()))
                        .add(nvl(data.getContribuicaoPatronalPrevidenciaSocial()))
                        .max(BigDecimal.ZERO))
                : null;
        boolean copiadoDoRestituir = positivo(data.getImpostoRestituir())
                && proximo(atual, data.getImpostoRestituir())
                && peloTotal != null
                && !proximo(peloTotal, data.getImpostoRestituir());
        if (!copiadoDoRestituir && atual != null
                && (proximo(atual, pelaDeducao) || proximo(atual, peloTotal))) {
            return;
        }

        BigDecimal esperado;
        if (copiadoDoRestituir) {
            esperado = peloTotal;
        } else if (pelaDeducao != null && proximo(pelaDeducao, peloTotal)) {
            esperado = pelaDeducao;
        } else if (atual == null) {
            esperado = pelaDeducao != null ? pelaDeducao : peloTotal;
        } else {
            esperado = null;
        }
        if (esperado == null) {
            return;
        }
        log.warn("⚠️ IRPF {} {}: Imposto devido I {} → {}{}",
                data.getCpfTitular(), data.getAnoCalendario(), atual, esperado,
                copiadoDoRestituir ? " (era o Imposto a restituir)" : "");
        data.setImpostoDevidoI(esperado);

        if (peloTotal != null && proximo(esperado, peloTotal) && !proximo(esperado, pelaDeducao)) {
            BigDecimal devido = escala(esperado.add(nvl(data.getDeducaoIncentivo())));
            log.warn("⚠️ IRPF {} {}: Imposto devido {} → {} (Imposto devido I + incentivo)",
                    data.getCpfTitular(), data.getAnoCalendario(), data.getImpostoDevido(), devido);
            data.setImpostoDevido(devido);
        }
    }

    private static void corrigirResultado(IrpfDeclaracaoData data) {
        BigDecimal pago = data.getImpostoPagoTotal();
        BigDecimal devido = data.getTotalImpostoDevido();
        if (pago == null || devido == null) {
            return;
        }
        if (positivo(data.getImpostoRestituir()) && proximo(devido, data.getImpostoRestituir())) {
            return;
        }
        BigDecimal restituir = escala(pago.subtract(devido).max(BigDecimal.ZERO));
        BigDecimal saldo = escala(devido.subtract(pago).max(BigDecimal.ZERO));
        if (data.getImpostoRestituir() != null && !proximo(data.getImpostoRestituir(), restituir)) {
            log.warn("⚠️ IRPF {} {}: Imposto a restituir {} → {} (pago − total devido)",
                    data.getCpfTitular(), data.getAnoCalendario(), data.getImpostoRestituir(), restituir);
            data.setImpostoRestituir(restituir);
        }
        if (data.getSaldoImpostoPagar() != null && !proximo(data.getSaldoImpostoPagar(), saldo)) {
            log.warn("⚠️ IRPF {} {}: Saldo a pagar {} → {} (total devido − pago)",
                    data.getCpfTitular(), data.getAnoCalendario(), data.getSaldoImpostoPagar(), saldo);
            data.setSaldoImpostoPagar(saldo);
        }
    }

    private static boolean isSimplificado(IrpfDeclaracaoData data) {
        String tipo = data.getTipoTributacao();
        if (tipo != null && !tipo.isBlank()) {
            return tipo.toUpperCase().contains("SIMPLIFICADO");
        }
        return positivo(data.getDescontoSimplificado());
    }

    private static BigDecimal maior(BigDecimal a, BigDecimal b) {
        if (a == null) {
            return b;
        }
        if (b == null) {
            return a;
        }
        return a.max(b);
    }

    private static boolean proximo(BigDecimal a, BigDecimal b) {
        return IncomeTaxRendimentosReconciler.withinTolerance(a, b);
    }

    private static boolean positivo(BigDecimal v) {
        return v != null && v.compareTo(BigDecimal.ZERO) > 0;
    }

    private static BigDecimal nvl(BigDecimal v) {
        return v != null ? v : BigDecimal.ZERO;
    }

    private static BigDecimal escala(BigDecimal v) {
        return v.setScale(2, RoundingMode.HALF_UP);
    }
}
