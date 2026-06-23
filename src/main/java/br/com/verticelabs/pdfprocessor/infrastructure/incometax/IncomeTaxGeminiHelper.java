package br.com.verticelabs.pdfprocessor.infrastructure.incometax;

import br.com.verticelabs.pdfprocessor.domain.service.IncomeTaxDeclarationService.IncomeTaxInfo;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/**
 * Regras compartilhadas para validar e enriquecer extração de IR via Gemini.
 */
public final class IncomeTaxGeminiHelper {

    private IncomeTaxGeminiHelper() {
    }

    /**
     * Dados mínimos para considerar a extração utilizável.
     * Valores {@code 0.00} do template JSON do Gemini NÃO contam como extraídos.
     */
    public static boolean isIncomeTaxInfoSufficient(IncomeTaxInfo info) {
        if (info == null) {
            return false;
        }

        boolean hasIdentifier = (info.getCpf() != null && !info.getCpf().isBlank())
                || (info.getAnoCalendario() != null && !info.getAnoCalendario().isBlank());

        if (!hasIdentifier) {
            return false;
        }

        return isPositive(info.getRendimentosTributaveis())
                || isPositive(info.getBaseCalculoImposto())
                || isPositive(info.getTotalImpostoDevido())
                || isPositive(info.getImpostoDevido())
                || isPositive(info.getSaldoImpostoPagar())
                || isPositive(info.getImpostoRestituir())
                || isPositive(info.getDeducoes())
                || isPositive(info.getImpostoPagoTotal())
                || isPositive(info.getImpostoRetidoFonteTitular())
                || isPositive(info.getDescontoSimplificado());
    }

    /**
     * Preenche campos ausentes com valores derivados das regras fiscais do RESUMO.
     * Comum em PDFs digitalizados onde o Gemini retorna totais mas omite linhas individuais.
     */
    public static IncomeTaxInfo enrich(IncomeTaxInfo source) {
        if (source == null) {
            return null;
        }

        BigDecimal rendimentosTributaveis = source.getRendimentosTributaveis();
        BigDecimal rendTitularPJ = source.getRendimentosTributaveisTitularPJ();
        BigDecimal rendDepPJ = source.getRendimentosTributaveisDependentesPJ();
        BigDecimal rendTitularPF = source.getRendimentosTributaveisTitularPF();
        BigDecimal rendDepPF = source.getRendimentosTributaveisDependentesPF();
        BigDecimal rendAcumTitular = source.getRendimentosAcumuladosTitular();
        BigDecimal rendAcumDep = source.getRendimentosAcumuladosDependentes();
        BigDecimal resultadoRural = source.getResultadoAtividadeRural();

        BigDecimal deducoesContribPrevOficial = source.getDeducoesContribPrevOficial();
        BigDecimal deducoesContribPrevRRA = source.getDeducoesContribPrevRRA();
        BigDecimal deducoesContribPrevCompl = source.getDeducoesContribPrevCompl();
        BigDecimal deducoesDependentes = source.getDeducoesDependentes();
        BigDecimal deducoesInstrucao = source.getDeducoesInstrucao();
        BigDecimal deducoesMedicas = source.getDeducoesMedicas();
        BigDecimal deducoesPensaoJudicial = source.getDeducoesPensaoJudicial();
        BigDecimal deducoesPensaoEscritura = source.getDeducoesPensaoEscritura();
        BigDecimal deducoesPensaoRRA = source.getDeducoesPensaoRRA();
        BigDecimal deducoesLivroCaixa = source.getDeducoesLivroCaixa();
        BigDecimal descontoSimplificado = source.getDescontoSimplificado();
        BigDecimal baseCalculoImposto = source.getBaseCalculoImposto();

        DeducoesReconciled reconciled = reconcileDeducoes(
                rendimentosTributaveis, baseCalculoImposto, source.getDeducoes(),
                deducoesContribPrevOficial, deducoesContribPrevRRA, deducoesContribPrevCompl,
                deducoesDependentes, deducoesInstrucao, deducoesMedicas,
                deducoesPensaoJudicial, deducoesPensaoEscritura, deducoesPensaoRRA,
                deducoesLivroCaixa, descontoSimplificado);
        BigDecimal deducoesTotal = reconciled.deducoesTotal();
        deducoesContribPrevCompl = reconciled.prevComplementar();
        baseCalculoImposto = reconciled.baseCalculoImposto(baseCalculoImposto);
        if (rendTitularPJ == null && isPositive(rendimentosTributaveis) && allNullOrZero(
                rendDepPJ, rendTitularPF, rendDepPF, rendAcumTitular, rendAcumDep, resultadoRural)) {
            rendTitularPJ = rendimentosTributaveis;
        }

        if (deducoesTotal == null) {
            deducoesTotal = sumNonNull(
                    deducoesContribPrevOficial, deducoesContribPrevRRA, deducoesContribPrevCompl,
                    deducoesDependentes, deducoesInstrucao, deducoesMedicas,
                    deducoesPensaoJudicial, deducoesPensaoEscritura, deducoesPensaoRRA,
                    deducoesLivroCaixa, descontoSimplificado);
        }

        BigDecimal impostoDevido = source.getImpostoDevido();
        BigDecimal deducaoIncentivo = source.getDeducaoIncentivo();
        BigDecimal impostoDevidoI = source.getImpostoDevidoI();
        BigDecimal impostoDevidoRRA = source.getImpostoDevidoRRA();
        BigDecimal totalImpostoDevido = source.getTotalImpostoDevido();

        if (impostoDevidoI == null && isPositive(impostoDevido) && deducaoIncentivo != null) {
            impostoDevidoI = impostoDevido.subtract(deducaoIncentivo);
        }
        if (impostoDevidoI == null && isPositive(totalImpostoDevido) && !isPositive(impostoDevidoRRA)) {
            impostoDevidoI = totalImpostoDevido;
        }
        if (impostoDevido == null && isPositive(impostoDevidoI) && deducaoIncentivo != null) {
            impostoDevido = impostoDevidoI.add(deducaoIncentivo);
        }
        if (totalImpostoDevido == null && isPositive(impostoDevidoI)) {
            totalImpostoDevido = isPositive(impostoDevidoRRA)
                    ? impostoDevidoI.add(impostoDevidoRRA)
                    : impostoDevidoI;
        }

        BigDecimal impostoPagoTotal = source.getImpostoPagoTotal();
        if (impostoPagoTotal == null) {
            impostoPagoTotal = sumNonNull(
                    source.getImpostoRetidoFonteTitular(),
                    source.getImpostoRetidoFonteDependentes(),
                    source.getCarneLeaoTitular(),
                    source.getCarneLeaoDependentes(),
                    source.getImpostoComplementar(),
                    source.getImpostoPagoExterior(),
                    source.getImpostoRetidoFonteLei11033(),
                    source.getImpostoRetidoRRA());
        }

        BigDecimal aliquotaEfetiva = source.getAliquotaEfetiva();
        if (aliquotaEfetiva == null && isPositive(rendimentosTributaveis) && isPositive(totalImpostoDevido)) {
            aliquotaEfetiva = totalImpostoDevido
                    .multiply(BigDecimal.valueOf(100))
                    .divide(rendimentosTributaveis, 2, RoundingMode.HALF_UP);
        }

        String tipoTributacao = source.getTipoTributacao();
        if (tipoTributacao == null || tipoTributacao.isBlank()) {
            if (isPositive(source.getDescontoSimplificado())) {
                tipoTributacao = "SIMPLIFICADO";
            } else if (isPositive(deducoesContribPrevOficial)
                    || isPositive(deducoesContribPrevCompl)
                    || isPositive(deducoesMedicas)
                    || isPositive(deducoesDependentes)) {
                tipoTributacao = "COMPLETO";
            }
        }

        return new IncomeTaxInfo(
                source.getNome(), source.getCpf(), source.getAnoCalendario(), source.getExercicio(),
                baseCalculoImposto, impostoDevido, deducaoIncentivo, impostoDevidoI,
                source.getContribuicaoPrevEmpregadorDomestico(), source.getImpostoDevidoII(), impostoDevidoRRA,
                totalImpostoDevido, source.getSaldoImpostoPagar(),
                rendimentosTributaveis, deducoesTotal,
                source.getImpostoRetidoFonteTitular(), impostoPagoTotal, source.getImpostoRestituir(),
                deducoesContribPrevOficial, deducoesContribPrevRRA,
                deducoesContribPrevCompl, deducoesDependentes,
                deducoesInstrucao, deducoesMedicas,
                deducoesPensaoJudicial, deducoesPensaoEscritura,
                deducoesPensaoRRA, deducoesLivroCaixa,
                source.getImpostoRetidoFonteDependentes(), source.getCarneLeaoTitular(),
                source.getCarneLeaoDependentes(), source.getImpostoComplementar(),
                source.getImpostoPagoExterior(), source.getImpostoRetidoFonteLei11033(),
                source.getImpostoRetidoRRA(),
                descontoSimplificado, aliquotaEfetiva,
                tipoTributacao, source.getDataNascimento(), source.getTituloEleitoral(),
                source.getTipoDeclaracao(), source.getDataEntrega(),
                source.getBensAnterior(), source.getBensAtual(),
                source.getDividasAnterior(), source.getDividasAtual(),
                source.getRendimentosIsentos(), source.getRendimentosTributacaoExclusiva(),
                source.getPagamentosEfetuados(), source.getFontesPagadoras(),
                source.getControle(), source.getDependentes(), source.getTotalDeducaoDependentes(),
                source.getAlimentandos(),
                rendTitularPJ, rendDepPJ, rendTitularPF, rendDepPF,
                resultadoRural, rendAcumTitular, rendAcumDep,
                source.getImpostoPagoGanhosCapital(), source.getImpostoDevidoGanhosCapital(),
                source.getImpostoDevidoGanhosCapitalMoedaEstrangeira(),
                source.getImpostoPagoGanhosCapitalMoedaEstrangeira(),
                source.getImpostoPagoRendaVariavel(),
                source.getImpostoDevidoGanhosLiquidosRendaVariavel(),
                source.getImpostoAPagarGanhosCapitalMoedaEstrangeira(),
                source.getRendimentosTributaveisExigSuspensa(), source.getDepositosJudiciais(),
                source.getImpostoDiferidoGanhosCapital(), source.getDoacoesPartidosPoliticos(),
                source.getDoacoesEfetuadas());
    }

    private static boolean isPositive(BigDecimal value) {
        return value != null && value.compareTo(BigDecimal.ZERO) > 0;
    }

    private static boolean allNullOrZero(BigDecimal... values) {
        for (BigDecimal value : values) {
            if (isPositive(value)) {
                return false;
            }
        }
        return true;
    }

    private static BigDecimal sumNonNull(BigDecimal... values) {
        List<BigDecimal> present = new ArrayList<>();
        for (BigDecimal value : values) {
            if (value != null) {
                present.add(value);
            }
        }
        if (present.isEmpty()) {
            return null;
        }
        BigDecimal sum = BigDecimal.ZERO;
        for (BigDecimal value : present) {
            sum = sum.add(value);
        }
        return sum.compareTo(BigDecimal.ZERO) == 0 ? null : sum;
    }

    private static final BigDecimal TOLERANCE = new BigDecimal("0.02");

    /**
     * Corrige inconsistências comuns na extração Gemini de deduções:
     * total ≠ soma das linhas, ou rendimentos − base ≠ total declarado.
     */
    static DeducoesReconciled reconcileDeducoes(
            BigDecimal rendimentosTributaveis,
            BigDecimal baseCalculoImposto,
            BigDecimal deducoesTotal,
            BigDecimal prevOficial,
            BigDecimal prevRra,
            BigDecimal prevComplementar,
            BigDecimal dependentes,
            BigDecimal instrucao,
            BigDecimal medicas,
            BigDecimal pensaoJudicial,
            BigDecimal pensaoEscritura,
            BigDecimal pensaoRra,
            BigDecimal livroCaixa,
            BigDecimal descontoSimplificado) {

        BigDecimal total = deducoesTotal;
        BigDecimal prevCompl = prevComplementar;
        BigDecimal base = baseCalculoImposto;

        BigDecimal sumOthers = sumNonNull(
                prevOficial, prevRra, dependentes, instrucao, medicas,
                pensaoJudicial, pensaoEscritura, pensaoRra, livroCaixa, descontoSimplificado);
        BigDecimal sumLines = sumNonNull(sumOthers, prevCompl);

        boolean reconciledFromTotal = false;

        // Total de deduções legível mas soma das linhas diverge → ajusta prev. complementar
        if (total != null && sumLines != null && total.subtract(sumLines).abs().compareTo(TOLERANCE) > 0
                && isPositive(prevCompl)) {
            prevCompl = prevCompl.add(total.subtract(sumLines));
            sumLines = sumNonNull(sumOthers, prevCompl);
            reconciledFromTotal = true;
        }

        // Rendimentos e base de cálculo legíveis mas total diverge — só se ainda não reconciliou pelo total
        if (!reconciledFromTotal && isPositive(rendimentosTributaveis) && base != null
                && base.compareTo(rendimentosTributaveis) < 0) {
            BigDecimal impliedTotal = rendimentosTributaveis.subtract(base);
            if (total == null || impliedTotal.subtract(total).abs().compareTo(TOLERANCE) > 0) {
                if (sumOthers != null) {
                    BigDecimal impliedPrev = impliedTotal.subtract(sumOthers);
                    if (impliedPrev.compareTo(BigDecimal.ZERO) > 0
                            && (prevCompl == null || impliedPrev.subtract(prevCompl).abs().compareTo(TOLERANCE) > 0)) {
                        prevCompl = impliedPrev;
                        total = impliedTotal;
                    }
                } else if (total == null) {
                    total = impliedTotal;
                }
            }
        }

        // Base ausente ou inconsistente após reconciliação pelo total de deduções
        if (isPositive(rendimentosTributaveis) && total != null
                && (base == null || (reconciledFromTotal && rendimentosTributaveis.subtract(total).subtract(base).abs().compareTo(TOLERANCE) > 0))) {
            base = rendimentosTributaveis.subtract(total);
        }

        return new DeducoesReconciled(total, prevCompl, base);
    }

    record DeducoesReconciled(BigDecimal deducoesTotal, BigDecimal prevComplementar, BigDecimal baseCalculo) {

        BigDecimal baseCalculoImposto(BigDecimal original) {
            return baseCalculo != null ? baseCalculo : original;
        }
    }
}
