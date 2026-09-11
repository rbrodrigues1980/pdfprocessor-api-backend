package br.com.verticelabs.pdfprocessor.application.documents;

import br.com.verticelabs.pdfprocessor.domain.model.PayrollDocument;
import br.com.verticelabs.pdfprocessor.domain.model.PayrollEntry;
import br.com.verticelabs.pdfprocessor.domain.model.ValidationIssue;
import br.com.verticelabs.pdfprocessor.domain.model.ValidationResult;
import br.com.verticelabs.pdfprocessor.domain.service.ExtractionValidationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;

/**
 * Garante que Gemini vazio/pior não descarta regex válido (causa de meses zerados).
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Fallback Gemini — preservar regex")
class KeepRegexFallbackTest {

    @Mock
    private ExtractionValidationService validationService;

    @InjectMocks
    private DocumentProcessUseCase useCase;

    private PayrollDocument document;
    private Method chooseMethod;

    @BeforeEach
    void setUp() throws Exception {
        document = new PayrollDocument();
        document.setId("doc-1");
        document.setProcessingLog(new ArrayList<>());

        chooseMethod = DocumentProcessUseCase.class.getDeclaredMethod(
                "chooseBetterOrKeepRegex",
                DocumentProcessUseCase.PageResult.class,
                double.class,
                DocumentProcessUseCase.PageResult.class,
                int.class,
                BigDecimal.class,
                BigDecimal.class,
                BigDecimal.class,
                String.class,
                PayrollDocument.class);
        chooseMethod.setAccessible(true);
    }

    @Test
    @DisplayName("Gemini vazio mantém resultado do regex")
    void geminiVazioMantemRegex() throws Exception {
        DocumentProcessUseCase.PageResult regex = new DocumentProcessUseCase.PageResult(
                List.of(PayrollEntry.builder().rubricaCodigo("4534").valor(new BigDecimal("1449.00")).build()),
                5);
        DocumentProcessUseCase.PageResult geminiEmpty =
                new DocumentProcessUseCase.PageResult(new ArrayList<>(), 5);

        Object result = chooseMethod.invoke(
                useCase,
                regex,
                0.55,
                geminiEmpty,
                5,
                new BigDecimal("26851.93"),
                new BigDecimal("13640.22"),
                new BigDecimal("13211.71"),
                "03018822404",
                document);

        assertSame(regex, result);
    }

    @Test
    @DisplayName("Gemini com score pior que regex mantém regex")
    void geminiPiorMantemRegex() throws Exception {
        when(validationService.validatePayrollExtraction(
                anyList(), any(), any(), any(), any(), isNull()))
                .thenReturn(ValidationResult.fromScore(0.40, List.of(
                        new ValidationIssue("x", "Y", "a", "b", "pior"))));

        DocumentProcessUseCase.PageResult regex = new DocumentProcessUseCase.PageResult(
                List.of(PayrollEntry.builder().rubricaCodigo("4534").valor(new BigDecimal("1449.00")).build()),
                5);
        DocumentProcessUseCase.PageResult gemini = new DocumentProcessUseCase.PageResult(
                List.of(PayrollEntry.builder().rubricaCodigo("4534").valor(new BigDecimal("100.00")).build()),
                5);

        Object result = chooseMethod.invoke(
                useCase,
                regex,
                0.55,
                gemini,
                5,
                new BigDecimal("26851.93"),
                new BigDecimal("13640.22"),
                new BigDecimal("13211.71"),
                "03018822404",
                document);

        assertSame(regex, result);
    }

    @Test
    @DisplayName("PageResult preserva pageNumber real")
    void pageResultPreservaPageNumber() {
        DocumentProcessUseCase.PageResult result =
                new DocumentProcessUseCase.PageResult(Collections.emptyList(), 7);
        assertEquals(7, result.getPageNumber());
        assertTrue(result.getEntries().isEmpty());
    }
}
