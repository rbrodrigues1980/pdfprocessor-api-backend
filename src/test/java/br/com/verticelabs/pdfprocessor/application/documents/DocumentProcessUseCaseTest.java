package br.com.verticelabs.pdfprocessor.application.documents;

import br.com.verticelabs.pdfprocessor.domain.model.*;
import br.com.verticelabs.pdfprocessor.domain.repository.PayrollDocumentRepository;
import br.com.verticelabs.pdfprocessor.domain.repository.PayrollEntryRepository;
import br.com.verticelabs.pdfprocessor.domain.service.*;
import br.com.verticelabs.pdfprocessor.infrastructure.pdf.PdfLineParser;
import br.com.verticelabs.pdfprocessor.infrastructure.pdf.PdfNormalizer;
import br.com.verticelabs.pdfprocessor.infrastructure.pdf.RubricaValidator;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;

import java.io.InputStream;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class DocumentProcessUseCaseTest {

    @Mock
    private PayrollDocumentRepository documentRepository;
    @Mock
    private PayrollEntryRepository entryRepository;
    @Mock
    private GridFsService gridFsService;
    @Mock
    private PdfService pdfService;
    @Mock
    private AiPdfExtractionService aiPdfExtractionService;
    @Mock
    private ExtractionValidationService validationService;
    @Mock
    private CrossValidationService crossValidationService;
    @Mock
    private MonthYearDetectionService monthYearDetectionService;
    @Mock
    private ITextIncomeTaxService iTextIncomeTaxService;
    @Mock
    private PdfLineParser lineParser;
    @Mock
    private PdfNormalizer normalizer;
    @Mock
    private RubricaValidator rubricaValidator;

    @InjectMocks
    private DocumentProcessUseCase useCase;

    private PayrollDocument document;
    private byte[] pdfBytes;

    @BeforeEach
    public void setUp() {
        document = new PayrollDocument();
        document.setId("doc-1");
        document.setTenantId("tenant-1");
        document.setTipo(DocumentType.FUNCEF);
        document.setProcessingLog(new ArrayList<>());
        pdfBytes = new byte[]{1, 2, 3};

        lenient().when(documentRepository.save(any(PayrollDocument.class)))
                .thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));
    }

    @Test
    public void testFallbackWhenParserReturnsZeroEntries() {
        // Arrange: mock PDF text is readable but has no entries
        String pageText = "DEMONSTRATIVO DE PROVENTOS\nCPF: 123.456.789-01\nBruto: R$ 5.000,00\nDescontos: R$ 1.000,00\nLíquido: R$ 4.000,00";
        when(pdfService.extractTextFromPage(any(InputStream.class), eq(1)))
                .thenReturn(Mono.just(pageText));
        when(monthYearDetectionService.detectMonthYear(anyString()))
                .thenReturn(Mono.just(Optional.of("2016/01")));
        when(lineParser.parseLinesFuncef(anyString(), anyString()))
                .thenReturn(Collections.emptyList()); // 0 lines parsed

        // Mock Gemini Enabled & Extraction
        when(aiPdfExtractionService.isEnabled()).thenReturn(true);
        when(aiPdfExtractionService.getPrimaryModelName()).thenReturn("gemini-1.5-flash");
        when(aiPdfExtractionService.extractPayrollData(any(byte[].class), eq(1)))
                .thenReturn(Mono.just("{\"entries\": []}")); // returns empty json but goes to Gemini

        // Act
        DocumentProcessUseCase.PageResult result = useCase.processPageWithMetadata(document, pdfBytes, 1, 1).block();

        // Assert
        verify(aiPdfExtractionService, times(1)).extractPayrollData(any(byte[].class), eq(1));
        Assertions.assertNotNull(result);
    }

    @Test
    public void testFallbackWhenValidationFails() {
        // Arrange: mock PDF text has values but validation score will be < 0.85
        String pageText = "DEMONSTRATIVO DE PROVENTOS\nCPF: 123.456.789-01\nBruto: R$ 5.000,00\nDescontos: R$ 1.000,00\nLíquido: R$ 4.000,00";
        when(pdfService.extractTextFromPage(any(InputStream.class), eq(1)))
                .thenReturn(Mono.just(pageText));
        when(monthYearDetectionService.detectMonthYear(anyString()))
                .thenReturn(Mono.just(Optional.of("2016/01")));

        PdfLineParser.ParsedLine line = new PdfLineParser.ParsedLine("409", "BENEFICIO FUNCEF", "5000.00", "2016/01");
        when(lineParser.parseLinesFuncef(anyString(), anyString()))
                .thenReturn(List.of(line));
        when(normalizer.normalizeValue(anyString())).thenReturn(new BigDecimal("5000.00"));
        when(normalizer.normalizeReference(anyString())).thenReturn("2016/01");
        when(normalizer.normalizeDescription(anyString())).thenReturn("BENEFICIO FUNCEF");

        // Mock Validation Service returning invalid result (score < 0.85)
        ValidationResult validationResult = new ValidationResult(0.5, false, List.of(
                new ValidationIssue("salarioLiquido", "TYPE", "4000.00", "5000.00", "Divergência")
        ), "REJECT");
        when(validationService.validatePayrollExtraction(anyList(), any(BigDecimal.class), any(BigDecimal.class), any(BigDecimal.class), anyString(), any()))
                .thenReturn(validationResult);

        // Mock Gemini Enabled & Extraction
        when(aiPdfExtractionService.isEnabled()).thenReturn(true);
        when(aiPdfExtractionService.getPrimaryModelName()).thenReturn("gemini-1.5-flash");
        when(aiPdfExtractionService.extractPayrollData(any(byte[].class), eq(1)))
                .thenReturn(Mono.just("{\"entries\": []}"));

        // Act
        DocumentProcessUseCase.PageResult result = useCase.processPageWithMetadata(document, pdfBytes, 1, 1).block();

        // Assert
        verify(aiPdfExtractionService, times(1)).extractPayrollData(any(byte[].class), eq(1));
        Assertions.assertNotNull(result);
    }

    @Test
    public void testNoFallbackWhenValidationPasses() {
        // Arrange: mock PDF text has values and validation score is >= 0.85 (valid)
        String pageText = "DEMONSTRATIVO DE PROVENTOS\nCPF: 123.456.789-01\nBruto: R$ 5.000,00\nDescontos: R$ 1.000,00\nLíquido: R$ 4.000,00";
        when(pdfService.extractTextFromPage(any(InputStream.class), eq(1)))
                .thenReturn(Mono.just(pageText));
        when(monthYearDetectionService.detectMonthYear(anyString()))
                .thenReturn(Mono.just(Optional.of("2016/01")));

        PdfLineParser.ParsedLine line = new PdfLineParser.ParsedLine("409", "BENEFICIO FUNCEF", "5000.00", "2016/01");
        when(lineParser.parseLinesFuncef(anyString(), anyString()))
                .thenReturn(List.of(line));
        when(normalizer.normalizeValue(anyString())).thenReturn(new BigDecimal("5000.00"));
        when(normalizer.normalizeReference(anyString())).thenReturn("2016/01");
        when(normalizer.normalizeDescription(anyString())).thenReturn("BENEFICIO FUNCEF");

        // Mock Validation Service returning valid result
        ValidationResult validationResult = new ValidationResult(0.9, true, Collections.emptyList(), "ACCEPT");
        when(validationService.validatePayrollExtraction(anyList(), any(BigDecimal.class), any(BigDecimal.class), any(BigDecimal.class), anyString(), any()))
                .thenReturn(validationResult);

        // Act
        DocumentProcessUseCase.PageResult result = useCase.processPageWithMetadata(document, pdfBytes, 1, 1).block();

        // Assert
        verify(aiPdfExtractionService, never()).extractPayrollData(any(byte[].class), eq(1));
        Assertions.assertNotNull(result);
        Assertions.assertEquals(1, result.getEntries().size());
    }
}
