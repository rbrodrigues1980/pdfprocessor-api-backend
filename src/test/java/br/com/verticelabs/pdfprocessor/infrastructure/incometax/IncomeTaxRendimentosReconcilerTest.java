package br.com.verticelabs.pdfprocessor.infrastructure.incometax;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("IR — reconciliar total de rendimentos (PF/aluguéis)")
class IncomeTaxRendimentosReconcilerTest {

    @Test
    @DisplayName("Célia AC 2016: total = PJ+PF quando total veio só como PJ")
    void celia2016TotalIgualPjMaisPf() {
        BigDecimal total = IncomeTaxRendimentosReconciler.reconciliarTotal(
                new BigDecimal("67963.80"),
                new BigDecimal("67963.80"),
                null,
                new BigDecimal("6120.00"),
                null, null, null, null);

        assertEquals(0, new BigDecimal("74083.80").compareTo(total));
    }

    @Test
    @DisplayName("não altera total quando já confere com a soma")
    void preservaTotalConsistente() {
        BigDecimal total = IncomeTaxRendimentosReconciler.reconciliarTotal(
                new BigDecimal("74083.80"),
                new BigDecimal("67963.80"),
                null,
                new BigDecimal("6120.00"),
                null, null, null, null);

        assertEquals(0, new BigDecimal("74083.80").compareTo(total));
    }

    @Test
    @DisplayName("não reduz total quando as linhas estão incompletas")
    void naoReduzTotalComLinhasIncompletas() {
        BigDecimal total = IncomeTaxRendimentosReconciler.reconciliarTotal(
                new BigDecimal("74083.80"),
                new BigDecimal("67963.80"),
                null, null, null, null, null, null);

        assertEquals(0, new BigDecimal("74083.80").compareTo(total));
    }

    @Test
    void detectaCopiaDeImpostoARestituir() {
        assertTrue(IncomeTaxRendimentosReconciler.valorCopiadoDoImpostoARestituir(
                new BigDecimal("3746.44"),
                new BigDecimal("3746.44"),
                new BigDecimal("29586.27")));
        assertFalse(IncomeTaxRendimentosReconciler.valorCopiadoDoImpostoARestituir(
                new BigDecimal("29586.27"),
                new BigDecimal("3746.44"),
                new BigDecimal("29586.27")));
    }
}
