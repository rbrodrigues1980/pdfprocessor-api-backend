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
        BigDecimal impostoDevidoII = source.getImpostoDevidoII();
        BigDecimal impostoDevidoRRA = source.getImpostoDevidoRRA();
        BigDecimal totalImpostoDevido = source.getTotalImpostoDevido();
        BigDecimal contribuicaoPrevEmpregadorDomestico = source.getContribuicaoPrevEmpregadorDomestico();

        if (!isPositive(deducaoIncentivo)
                && isPositive(impostoDevido)
                && isPositive(totalImpostoDevido)
                && impostoDevido.compareTo(totalImpostoDevido) > 0) {
            BigDecimal derivado = impostoDevido.subtract(totalImpostoDevido)
                    .setScale(2, RoundingMode.HALF_UP);
            if (derivado.compareTo(TOLERANCE) > 0 && derivado.compareTo(impostoDevido) < 0) {
                deducaoIncentivo = derivado;
            }
        }
        if (!isPositive(impostoDevidoI) && isPositive(impostoDevido) && deducaoIncentivo != null) {
            impostoDevidoI = impostoDevido.subtract(nvl(deducaoIncentivo));
        }
        // Derivar RRA omitido pelo Gemini: Total = (II se houver, senão I) + RRA
        if (!isPositive(impostoDevidoRRA)
                && isPositive(totalImpostoDevido)
                && isPositive(impostoDevidoI)) {
            BigDecimal baseProgressivo = isPositive(impostoDevidoII) ? impostoDevidoII : impostoDevidoI;
            BigDecimal rraDerivado = totalImpostoDevido.subtract(baseProgressivo)
                    .setScale(2, RoundingMode.HALF_UP);
            boolean gapExplicadoPorDomestico = !isPositive(impostoDevidoII)
                    && isPositive(contribuicaoPrevEmpregadorDomestico)
                    && rraDerivado.subtract(contribuicaoPrevEmpregadorDomestico).abs()
                            .compareTo(TOLERANCE) <= 0;
            if (rraDerivado.compareTo(TOLERANCE) > 0 && !gapExplicadoPorDomestico) {
                impostoDevidoRRA = rraDerivado;
            }
        }
        if (!isPositive(impostoDevidoI) && isPositive(totalImpostoDevido) && !isPositive(impostoDevidoRRA)) {
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
                contribuicaoPrevEmpregadorDomestico, impostoDevidoII, impostoDevidoRRA,
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

    /**
     * Substitui a lista de pagamentos efetuados preservando os demais campos.
     */
    public static IncomeTaxInfo withPagamentos(IncomeTaxInfo source, List<IncomeTaxInfo.PagamentoEfetuado> pagamentos) {
        if (source == null) {
            return null;
        }
        return copyWith(
                source,
                pagamentos != null ? pagamentos : List.of(),
                source.getDependentes(),
                source.getTotalDeducaoDependentes());
    }

    /**
     * Mescla lista/total de dependentes: preenche se a fonte ainda estiver vazia/nula.
     */
    public static IncomeTaxInfo withDependentes(
            IncomeTaxInfo source,
            List<IncomeTaxInfo.DependenteInfo> dependentes,
            BigDecimal totalDeducao) {
        if (source == null) {
            return null;
        }
        List<IncomeTaxInfo.DependenteInfo> current = source.getDependentes();
        boolean hasList = current != null && !current.isEmpty();
        List<IncomeTaxInfo.DependenteInfo> mergedList = hasList
                ? current
                : (dependentes != null ? dependentes : List.of());
        BigDecimal mergedTotal = isPositive(source.getTotalDeducaoDependentes())
                ? source.getTotalDeducaoDependentes()
                : totalDeducao;
        // 0.00 do Gemini no RESUMO não conta — a página 1 tem o total real (ex.: Eurípedes 4.550,16)
        boolean resumoDependentesMissing = !isPositive(source.getDeducoesDependentes());
        BigDecimal deducoesDependentes = resumoDependentesMissing && isPositive(mergedTotal)
                ? mergedTotal
                : source.getDeducoesDependentes();

        BigDecimal prevCompl = source.getDeducoesContribPrevCompl();
        if (resumoDependentesMissing && isPositive(deducoesDependentes) && isPositive(prevCompl)) {
            prevCompl = ajustarPrevComplAposPreencherDependentes(source, prevCompl, deducoesDependentes);
        }

        return copyWith(source, source.getPagamentosEfetuados(), mergedList, mergedTotal,
                prevCompl, deducoesDependentes);
    }

    /**
     * Quando o RESUMO omitiu Dependentes, o residual costuma ter sido jogado em Fapi
     * ({@code 4.550,16 + 897,00 = 5.450,16}). Devolve o valor à linha correta.
     */
    private static BigDecimal ajustarPrevComplAposPreencherDependentes(
            IncomeTaxInfo source, BigDecimal prevCompl, BigDecimal dependentes) {
        if (prevCompl.compareTo(dependentes) == 0) {
            BigDecimal implied = impliedPrevCompl(source, dependentes);
            return implied != null ? implied : BigDecimal.ZERO;
        }
        if (prevCompl.compareTo(dependentes) > 0) {
            return prevCompl.subtract(dependentes).setScale(2, RoundingMode.HALF_UP);
        }
        return prevCompl;
    }

    private static BigDecimal impliedPrevCompl(IncomeTaxInfo source, BigDecimal dependentes) {
        if (source.getDeducoes() == null) {
            return null;
        }
        BigDecimal others = sumNonNull(
                source.getDeducoesContribPrevOficial(),
                source.getDeducoesContribPrevRRA(),
                dependentes,
                source.getDeducoesInstrucao(),
                source.getDeducoesMedicas(),
                source.getDeducoesPensaoJudicial(),
                source.getDeducoesPensaoEscritura(),
                source.getDeducoesPensaoRRA(),
                source.getDeducoesLivroCaixa(),
                source.getDescontoSimplificado());
        BigDecimal implied = source.getDeducoes().subtract(others != null ? others : BigDecimal.ZERO)
                .setScale(2, RoundingMode.HALF_UP);
        return implied.compareTo(BigDecimal.ZERO) >= 0 ? implied : null;
    }

    private static IncomeTaxInfo copyWith(
            IncomeTaxInfo source,
            List<IncomeTaxInfo.PagamentoEfetuado> pagamentos,
            List<IncomeTaxInfo.DependenteInfo> dependentes,
            BigDecimal totalDeducaoDependentes) {
        return copyWith(source, pagamentos, dependentes, totalDeducaoDependentes,
                source.getDeducoesContribPrevCompl(), source.getDeducoesDependentes());
    }

    private static IncomeTaxInfo copyWith(
            IncomeTaxInfo source,
            List<IncomeTaxInfo.PagamentoEfetuado> pagamentos,
            List<IncomeTaxInfo.DependenteInfo> dependentes,
            BigDecimal totalDeducaoDependentes,
            BigDecimal prevCompl,
            BigDecimal deducoesDependentes) {
        return new IncomeTaxInfo(
                source.getNome(), source.getCpf(), source.getAnoCalendario(), source.getExercicio(),
                source.getBaseCalculoImposto(), source.getImpostoDevido(), source.getDeducaoIncentivo(),
                source.getImpostoDevidoI(),
                source.getContribuicaoPrevEmpregadorDomestico(), source.getImpostoDevidoII(),
                source.getImpostoDevidoRRA(),
                source.getTotalImpostoDevido(), source.getSaldoImpostoPagar(),
                source.getRendimentosTributaveis(), source.getDeducoes(),
                source.getImpostoRetidoFonteTitular(), source.getImpostoPagoTotal(), source.getImpostoRestituir(),
                source.getDeducoesContribPrevOficial(), source.getDeducoesContribPrevRRA(),
                prevCompl, deducoesDependentes,
                source.getDeducoesInstrucao(), source.getDeducoesMedicas(),
                source.getDeducoesPensaoJudicial(), source.getDeducoesPensaoEscritura(),
                source.getDeducoesPensaoRRA(), source.getDeducoesLivroCaixa(),
                source.getImpostoRetidoFonteDependentes(), source.getCarneLeaoTitular(),
                source.getCarneLeaoDependentes(), source.getImpostoComplementar(),
                source.getImpostoPagoExterior(), source.getImpostoRetidoFonteLei11033(),
                source.getImpostoRetidoRRA(),
                source.getDescontoSimplificado(), source.getAliquotaEfetiva(),
                source.getTipoTributacao(), source.getDataNascimento(), source.getTituloEleitoral(),
                source.getTipoDeclaracao(), source.getDataEntrega(),
                source.getBensAnterior(), source.getBensAtual(),
                source.getDividasAnterior(), source.getDividasAtual(),
                source.getRendimentosIsentos(), source.getRendimentosTributacaoExclusiva(),
                pagamentos, source.getFontesPagadoras(),
                source.getControle(), dependentes, totalDeducaoDependentes,
                source.getAlimentandos(),
                source.getRendimentosTributaveisTitularPJ(), source.getRendimentosTributaveisDependentesPJ(),
                source.getRendimentosTributaveisTitularPF(), source.getRendimentosTributaveisDependentesPF(),
                source.getResultadoAtividadeRural(), source.getRendimentosAcumuladosTitular(),
                source.getRendimentosAcumuladosDependentes(),
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

    private static BigDecimal nvl(BigDecimal value) {
        return value != null ? value : BigDecimal.ZERO;
    }

    /**
     * Residual que coincide com 1–6 × limite anual de dependente (2.275,08).
     * Indica linha "Dependentes" omitida no RESUMO, não Fapi faltando.
     */
    static boolean pareceDeducaoDependentes(BigDecimal valor) {
        if (!isPositive(valor)) {
            return false;
        }
        for (int n = 1; n <= 6; n++) {
            BigDecimal esperado = LIMITE_DEPENDENTE_ANUAL.multiply(BigDecimal.valueOf(n));
            if (valor.subtract(esperado).abs().compareTo(DEPENDENTES_MATCH_DELTA) <= 0) {
                return true;
            }
        }
        return false;
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
    /** Limite anual por dependente vigente por vários anos-calendário (inclui 2019). */
    private static final BigDecimal LIMITE_DEPENDENTE_ANUAL = new BigDecimal("2275.08");
    private static final BigDecimal DEPENDENTES_MATCH_DELTA = new BigDecimal("10.00");

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

        // Total de deduções legível mas soma das linhas diverge → ajusta prev. complementar.
        // Não absorver residual que parece dedução de dependentes omitida (Eurípedes 2019:
        // Fapi 897,00 + Dependentes 4.550,16 virava 5.450,16 em Fapi).
        if (total != null && sumLines != null && total.subtract(sumLines).abs().compareTo(TOLERANCE) > 0
                && isPositive(prevCompl)) {
            BigDecimal residual = total.subtract(sumLines);
            if (isPositive(dependentes) || !pareceDeducaoDependentes(residual)) {
                prevCompl = prevCompl.add(residual);
                sumLines = sumNonNull(sumOthers, prevCompl);
                reconciledFromTotal = true;
            }
        }

        // Rendimentos e base de cálculo legíveis mas total diverge — só se ainda não
        // reconciliou pelo total. Se o TOTAL fecha com as linhas e a diferença para a
        // base extraída é exatamente a dedução de dependentes, a base é que está errada
        // (Miguel 2016) — não reescreve o total.
        if (!reconciledFromTotal && isPositive(rendimentosTributaveis) && base != null
                && base.compareTo(rendimentosTributaveis) < 0) {
            BigDecimal impliedTotal = rendimentosTributaveis.subtract(base);
            boolean baseOmitiuDependentes = total != null
                    && ((isPositive(dependentes)
                    && total.subtract(impliedTotal).subtract(dependentes).abs().compareTo(TOLERANCE) <= 0)
                    || (!isPositive(dependentes) && pareceDeducaoDependentes(total.subtract(impliedTotal))));
            if (!baseOmitiuDependentes
                    && (total == null || impliedTotal.subtract(total).abs().compareTo(TOLERANCE) > 0)) {
                if (sumOthers != null) {
                    BigDecimal impliedPrev = impliedTotal.subtract(sumOthers);
                    BigDecimal deltaPrev = impliedPrev.subtract(nvl(prevCompl));
                    boolean residualPareceDependentes = !isPositive(dependentes)
                            && (pareceDeducaoDependentes(impliedPrev) || pareceDeducaoDependentes(deltaPrev));
                    if (!residualPareceDependentes
                            && impliedPrev.compareTo(BigDecimal.ZERO) > 0
                            && (prevCompl == null || impliedPrev.subtract(prevCompl).abs().compareTo(TOLERANCE) > 0)) {
                        prevCompl = impliedPrev;
                        total = impliedTotal;
                    }
                } else if (total == null) {
                    total = impliedTotal;
                }
            }
        }

        // Base ausente ou inconsistente com rendimentos − total de deduções:
        // a planilha e o RESUMO exigem Base = TOTAL RENDIMENTOS − TOTAL DEDUÇÕES.
        if (isPositive(rendimentosTributaveis) && total != null
                && total.compareTo(rendimentosTributaveis) < 0) {
            BigDecimal derived = rendimentosTributaveis.subtract(total)
                    .setScale(2, RoundingMode.HALF_UP);
            if (derived.compareTo(BigDecimal.ZERO) >= 0
                    && (base == null || derived.subtract(base).abs().compareTo(TOLERANCE) > 0)) {
                base = derived;
            }
        }

        return new DeducoesReconciled(total, prevCompl, base);
    }

    record DeducoesReconciled(BigDecimal deducoesTotal, BigDecimal prevComplementar, BigDecimal baseCalculo) {

        BigDecimal baseCalculoImposto(BigDecimal original) {
            return baseCalculo != null ? baseCalculo : original;
        }
    }
}
