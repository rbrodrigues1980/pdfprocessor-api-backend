package br.com.verticelabs.pdfprocessor.application.textextraction;

import br.com.verticelabs.pdfprocessor.domain.service.TextExtractionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.io.InputStream;

/**
 * Use case for text extraction from images.
 * Orchestrates text extraction-related business logic.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TextExtractionUseCase {

    private final TextExtractionService textExtractionService;

    /**
     * Extract text from a PDF.
     *
     * @param pdfInputStream the PDF input stream
     * @return Mono containing the extracted text
     */
    public Mono<String> extractTextFromPdf(InputStream pdfInputStream) {
        log.info("Executing text extraction use case: extractTextFromPdf");
        return textExtractionService.extractTextFromPdf(pdfInputStream)
                .doOnSuccess(text -> log.info("Text extraction successful. Extracted {} characters", text.length()))
                .doOnError(error -> log.error("Text extraction failed", error));
    }

    /**
     * Extract text from an image.
     *
     * @param imageBytes the image bytes
     * @return Mono containing the extracted text
     */
    public Mono<String> extractTextFromImage(byte[] imageBytes) {
        log.info("Executing text extraction use case: extractTextFromImage");
        return textExtractionService.extractTextFromImage(imageBytes)
                .doOnSuccess(
                        text -> log.info("Image text extraction successful. Extracted {} characters", text.length()))
                .doOnError(error -> log.error("Image text extraction failed", error));
    }

    /**
     * Check if a PDF is image-based.
     *
     * @param pdfInputStream the PDF input stream
     * @return Mono containing true if PDF is image-based, false otherwise
     */
    public Mono<Boolean> isPdfImageBased(InputStream pdfInputStream) {
        log.info("Executing text extraction use case: isPdfImageBased");
        return textExtractionService.isPdfImageBased(pdfInputStream)
                .doOnSuccess(isImageBased -> log.info("PDF image detection: {}", isImageBased))
                .doOnError(error -> log.error("PDF image detection failed", error));
    }

    /**
     * Extract text from a specific page.
     *
     * @param pdfInputStream the PDF input stream
     * @param pageNumber     the page number (1-indexed)
     * @return Mono containing the extracted text from the page
     */
    public Mono<String> extractTextFromPdfPage(InputStream pdfInputStream, int pageNumber) {
        log.info("Executing text extraction use case: extractTextFromPdfPage (page {})", pageNumber);
        return textExtractionService.extractTextFromPdfPage(pdfInputStream, pageNumber)
                .doOnSuccess(text -> log.info("Page text extraction successful. Extracted {} characters", text.length()))
                .doOnError(error -> log.error("Page text extraction failed", error));
    }
}

