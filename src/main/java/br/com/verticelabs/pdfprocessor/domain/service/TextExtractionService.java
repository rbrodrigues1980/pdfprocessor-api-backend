package br.com.verticelabs.pdfprocessor.domain.service;

import reactor.core.publisher.Mono;

import java.io.InputStream;

/**
 * Service interface for text extraction from images.
 * Used to extract text from image-based PDFs and images.
 */
public interface TextExtractionService {

    /**
     * Extract text from an image.
     *
     * @param imageBytes the image bytes
     * @return Mono containing the extracted text
     */
    Mono<String> extractTextFromImage(byte[] imageBytes);

    /**
     * Extract text from a PDF.
     * Converts PDF pages to images and extracts text.
     *
     * @param pdfInputStream the PDF input stream
     * @return Mono containing the extracted text from all pages
     */
    Mono<String> extractTextFromPdf(InputStream pdfInputStream);

    /**
     * Check if a PDF is image-based (requires image text extraction).
     * A PDF is considered image-based if it has very little or no extractable text.
     *
     * @param pdfInputStream the PDF input stream
     * @return Mono containing true if PDF is image-based, false otherwise
     */
    Mono<Boolean> isPdfImageBased(InputStream pdfInputStream);

    /**
     * Extract text from a specific page of a PDF.
     *
     * @param pdfInputStream the PDF input stream
     * @param pageNumber     the page number (1-indexed)
     * @return Mono containing the extracted text from the specified page
     */
    Mono<String> extractTextFromPdfPage(InputStream pdfInputStream, int pageNumber);
}

