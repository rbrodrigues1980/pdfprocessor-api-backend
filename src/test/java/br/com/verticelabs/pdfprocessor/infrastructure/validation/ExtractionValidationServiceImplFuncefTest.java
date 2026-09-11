package br.com.verticelabs.pdfprocessor.infrastructure.validation;

import br.com.verticelabs.pdfprocessor.domain.model.PayrollEntry;
import br.com.verticelabs.pdfprocessor.domain.model.ValidationResult;
import br.com.verticelabs.pdfprocessor.domain.service.CpfValidationService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Regressão: FUNCEF traz descontos com valor positivo — validação por faixa de código.
 */
@DisplayName("Validação FUNCEF — proventos/descontos por código")
class ExtractionValidationServiceImplFuncefTest {

    private final ExtractionValidationServiceImpl service;

    ExtractionValidationServiceImplFuncefTest() {
        CpfValidationService cpfValidationService = mock(CpfValidationService.class);
        when(cpfValidationService.isValid(anyString())).thenReturn(true);
        this.service = new ExtractionValidationServiceImpl(cpfValidationService);
    }

    @Test
    @DisplayName("deve classificar 2xxx como provento e 4xxx como desconto quando todos positivos")
    void deveUsarClassificacaoPorCodigoQuandoValoresPositivos() {
        List<PayrollEntry> entries = List.of(
                entry("2187", "6717.63"),
                entry("2033", "13416.67"),
                entry("4534", "1449.00"),
                entry("4327", "1786.36"),
                entry("4362", "73.79"));

        assertTrue(ExtractionValidationServiceImpl.shouldUseCodeBasedProventoDesconto(entries));
        assertTrue(ExtractionValidationServiceImpl.isProventoEntry(entries.get(0), true));
        assertTrue(ExtractionValidationServiceImpl.isDescontoEntry(entries.get(2), true));
        assertFalse(ExtractionValidationServiceImpl.isProventoEntry(entries.get(2), true));
    }

    @Test
    @DisplayName("maio/2025 Aluísio: regex FUNCEF com descontos positivos deve passar validação")
    void validacaoFuncefMaio2025DevePassarComDescontosPositivos() {
        // Totais aproximados do demonstrativo 2025/05 (proventos 2xxx + descontos 4xxx)
        List<PayrollEntry> entries = List.of(
                entry("2187", "6717.63"),
                entry("2033", "13416.67"),
                entry("2222", "6717.63"),
                entry("4328", "364.92"),
                entry("4327", "1786.36"),
                entry("4698", "1482.42"),
                entry("4428", "364.92"),
                entry("3215", "3358.81"),
                entry("4362", "73.79"),
                entry("4635", "4636.00"),
                entry("4729", "75.00"),
                entry("4776", "49.00"),
                entry("4534", "1449.00"));

        BigDecimal bruto = new BigDecimal("26851.93");
        BigDecimal descontos = new BigDecimal("13640.22");
        BigDecimal liquido = new BigDecimal("13211.71");

        ValidationResult result = service.validatePayrollExtraction(
                entries, bruto, descontos, liquido, "03018822404", null);

        assertTrue(result.isValid(),
                "Esperado válido; score=" + result.confidenceScore() + " issues=" + result.issues());
        assertTrue(result.confidenceScore() >= 0.85,
                "Score insuficiente: " + result.confidenceScore());
    }

    @Test
    @DisplayName("modelo com sinal (CAIXA): descontos negativos continuam por sinal")
    void modeloComSinalNaoUsaFaixaDeCodigo() {
        List<PayrollEntry> entries = List.of(
                entry("1001", "5000.00"),
                entry("2001", "-1000.00"));

        assertFalse(ExtractionValidationServiceImpl.shouldUseCodeBasedProventoDesconto(entries));

        ValidationResult result = service.validatePayrollExtraction(
                entries,
                new BigDecimal("5000.00"),
                new BigDecimal("1000.00"),
                new BigDecimal("4000.00"),
                "03018822404",
                null);

        assertTrue(result.isValid());
    }

    @Test
    @DisplayName("sem classificação por código, todos positivos falham SOMA_PROVENTOS (bug antigo)")
    void semClassificacaoPorCodigoSomaTodosComoProvento() {
        List<PayrollEntry> onlyPositivesWrongModel = List.of(
                entry("2187", "1000.00"),
                entry("4534", "500.00"));

        // Força o caminho antigo: se não houver 2xxx+4xxx juntos... wait, this HAS both.
        // Use codes that don't trigger code-based (all 5xxx) to show sign-only path.
        List<PayrollEntry> entries = List.of(
                entry("5187", "1000.00"),
                entry("5534", "500.00"));

        assertFalse(ExtractionValidationServiceImpl.shouldUseCodeBasedProventoDesconto(entries));

        ValidationResult result = service.validatePayrollExtraction(
                entries,
                new BigDecimal("1000.00"),
                new BigDecimal("500.00"),
                new BigDecimal("500.00"),
                null,
                null);

        // Soma "proventos" = 1500 ≠ bruto 1000 → score baixo
        assertFalse(result.isValid());
    }

    private static PayrollEntry entry(String codigo, String valor) {
        return PayrollEntry.builder()
                .rubricaCodigo(codigo)
                .valor(new BigDecimal(valor))
                .build();
    }
}
