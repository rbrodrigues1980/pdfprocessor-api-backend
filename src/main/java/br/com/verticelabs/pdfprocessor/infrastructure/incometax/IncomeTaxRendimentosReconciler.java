package br.com.verticelabs.pdfprocessor.infrastructure.incometax;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Corrige TOTAL de rendimentos tributáveis quando a extração captura só a 1ª linha (PJ)
 * e deixa PF/aluguéis (e demais linhas) de fora — típico em declaração com desconto simplificado
 * (Célia AC 2016: PJ 67.963,80 + PF 6.120,00 = 74.083,80).
 */
public final class IncomeTaxRendimentosReconciler {

    static final BigDecimal TOLERANCE = new BigDecimal("0.02");

    private IncomeTaxRendimentosReconciler() {
    }

    public static BigDecimal somaLinhas(
            BigDecimal titularPJ,
            BigDecimal dependentesPJ,
            BigDecimal titularPF,
            BigDecimal dependentesPF,
            BigDecimal acumuladosTitular,
            BigDecimal acumuladosDependentes,
            BigDecimal atividadeRural) {
        BigDecimal sum = BigDecimal.ZERO;
        boolean any = false;
        for (BigDecimal v : new BigDecimal[]{
                titularPJ, dependentesPJ, titularPF, dependentesPF,
                acumuladosTitular, acumuladosDependentes, atividadeRural}) {
            if (v != null) {
                sum = sum.add(v);
                any = true;
            }
        }
        return any ? sum.setScale(2, RoundingMode.HALF_UP) : BigDecimal.ZERO;
    }

    /**
     * Se a soma das linhas for positiva e o total estiver ausente, igual a uma única
     * sublinha ou menor que a soma, devolve a soma. Caso contrário devolve o total.
     */
    public static BigDecimal reconciliarTotal(
            BigDecimal total,
            BigDecimal titularPJ,
            BigDecimal dependentesPJ,
            BigDecimal titularPF,
            BigDecimal dependentesPF,
            BigDecimal acumuladosTitular,
            BigDecimal acumuladosDependentes,
            BigDecimal atividadeRural) {
        BigDecimal soma = somaLinhas(
                titularPJ, dependentesPJ, titularPF, dependentesPF,
                acumuladosTitular, acumuladosDependentes, atividadeRural);
        if (soma.compareTo(BigDecimal.ZERO) <= 0) {
            return total;
        }
        if (total == null) {
            return soma;
        }
        if (withinTolerance(total, soma)) {
            return total;
        }
        // Total capturou só uma das linhas (geralmente PJ titular).
        boolean totalIgualUmaLinha = false;
        for (BigDecimal linha : new BigDecimal[]{
                titularPJ, dependentesPJ, titularPF, dependentesPF,
                acumuladosTitular, acumuladosDependentes, atividadeRural}) {
            if (isPositive(linha) && withinTolerance(total, linha)) {
                totalIgualUmaLinha = true;
                break;
            }
        }
        if (totalIgualUmaLinha || total.compareTo(soma) < 0) {
            return soma;
        }
        return total;
    }

    public static boolean withinTolerance(BigDecimal a, BigDecimal b) {
        if (a == null || b == null) {
            return false;
        }
        return a.subtract(b).abs().compareTo(TOLERANCE) <= 0;
    }

    /**
     * Imposto devido / Imposto devido I colado com o valor de Imposto a restituir
     * (layout de duas colunas no RESUMO — Eurípedes 2018).
     */
    public static boolean valorCopiadoDoImpostoARestituir(
            BigDecimal valor,
            BigDecimal impostoRestituir,
            BigDecimal totalImpostoDevido) {
        if (valor == null || impostoRestituir == null || totalImpostoDevido == null) {
            return false;
        }
        if (impostoRestituir.compareTo(BigDecimal.ZERO) <= 0) {
            return false;
        }
        if (withinTolerance(impostoRestituir, totalImpostoDevido)) {
            return false;
        }
        return withinTolerance(valor, impostoRestituir);
    }

    private static boolean isPositive(BigDecimal value) {
        return value != null && value.compareTo(BigDecimal.ZERO) > 0;
    }
}
