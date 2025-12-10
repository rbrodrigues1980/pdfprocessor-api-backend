package br.com.verticelabs.pdfprocessor.infrastructure.textextraction;

import br.com.verticelabs.pdfprocessor.domain.service.PdfService;
import br.com.verticelabs.pdfprocessor.domain.service.TextExtractionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.sourceforge.tess4j.Tesseract;
import net.sourceforge.tess4j.TesseractException;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * Implementation of text extraction service using Tesseract.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TextExtractionServiceImpl implements TextExtractionService {

    private final Tesseract tesseract;
    private final PdfService pdfService;

    @Value("${text-extraction.tesseract.dpi:${ocr.tesseract.dpi:300}}")
    private int dpi;

    private static final int MIN_TEXT_LENGTH_FOR_NATIVE_PDF = 100;
    
    // Lock para sincronizar acesso ao Tesseract (não é thread-safe)
    private static final Object TESSERACT_LOCK = new Object();

    @Override
    public Mono<String> extractTextFromImage(byte[] imageBytes) {
        return Mono.fromCallable(() -> {
            try {
                log.debug("Starting text extraction from image ({} bytes)", imageBytes.length);

                BufferedImage image = ImageIO.read(new ByteArrayInputStream(imageBytes));
                if (image == null) {
                    throw new IllegalArgumentException("Invalid image data");
                }

                // Sincronizar acesso ao Tesseract (não é thread-safe)
                String text;
                synchronized (TESSERACT_LOCK) {
                    text = tesseract.doOCR(image);
                }
                log.debug("Text extraction completed. Extracted {} characters", text != null ? text.length() : 0);

                return text != null ? text : "";
            } catch (TesseractException e) {
                log.error("Error during text extraction processing", e);
                throw new RuntimeException("Text extraction failed: " + e.getMessage(), e);
            } catch (IOException e) {
                log.error("Error reading image data", e);
                throw new RuntimeException("Failed to read image: " + e.getMessage(), e);
            }
        }).subscribeOn(Schedulers.boundedElastic());
    }

    @Override
    public Mono<String> extractTextFromPdf(InputStream pdfInputStream) {
        return Mono.fromCallable(() -> {
            log.info("Starting text extraction from PDF");
            byte[] pdfBytes = pdfInputStream.readAllBytes();

            try (PDDocument document = Loader.loadPDF(pdfBytes)) {
                int totalPages = document.getNumberOfPages();
                log.info("PDF has {} pages. Starting text extraction processing...", totalPages);

                PDFRenderer pdfRenderer = new PDFRenderer(document);
                StringBuilder fullText = new StringBuilder();

                for (int page = 0; page < totalPages; page++) {
                    log.debug("Processing page {} of {}", page + 1, totalPages);

                    // Render page to image at specified DPI
                    BufferedImage image = pdfRenderer.renderImageWithDPI(page, dpi);

                    // Convert BufferedImage to bytes
                    ByteArrayOutputStream baos = new ByteArrayOutputStream();
                    ImageIO.write(image, "png", baos);
                    byte[] imageBytes = baos.toByteArray();

                    // Extract text from image (sincronizado porque Tesseract não é thread-safe)
                    String pageText;
                    synchronized (TESSERACT_LOCK) {
                        pageText = tesseract.doOCR(ImageIO.read(new ByteArrayInputStream(imageBytes)));
                    }

                    if (pageText != null && !pageText.isEmpty()) {
                        fullText.append("=== PAGE ").append(page + 1).append(" ===\n");
                        fullText.append(pageText);
                        fullText.append("\n\n");
                    }
                }

                String result = fullText.toString();
                log.info("Text extraction completed. Total characters extracted: {}", result.length());
                return result;

            } catch (IOException | TesseractException e) {
                log.error("Error during PDF text extraction processing", e);
                throw new RuntimeException("Text extraction failed for PDF: " + e.getMessage(), e);
            }
        }).subscribeOn(Schedulers.boundedElastic());
    }

    @Override
    public Mono<Boolean> isPdfImageBased(InputStream pdfInputStream) {
        return pdfService.extractText(pdfInputStream)
                .map(text -> {
                    // Remove whitespace and check if there's meaningful text
                    String cleanText = text.replaceAll("\\s+", "");
                    boolean isImageBased = cleanText.length() < MIN_TEXT_LENGTH_FOR_NATIVE_PDF;

                    log.debug("PDF text detection: {} characters (clean). Image-based: {}",
                            cleanText.length(), isImageBased);

                    return isImageBased;
                })
                .onErrorReturn(true); // If error reading text, assume it's image-based
    }

    @Override
    public Mono<String> extractTextFromPdfPage(InputStream pdfInputStream, int pageNumber) {
        return Mono.fromCallable(() -> {
            log.info("Starting text extraction from PDF page {}", pageNumber);
            byte[] pdfBytes = pdfInputStream.readAllBytes();

            try (PDDocument document = Loader.loadPDF(pdfBytes)) {
                int totalPages = document.getNumberOfPages();

                if (pageNumber < 1 || pageNumber > totalPages) {
                    throw new IllegalArgumentException(
                            String.format("Invalid page number: %d. PDF has %d pages", pageNumber, totalPages));
                }

                PDFRenderer pdfRenderer = new PDFRenderer(document);

                // Page number is 1-indexed for user, but 0-indexed for PDFBox
                int pageIndex = pageNumber - 1;

                log.debug("Rendering page {} at {} DPI", pageNumber, dpi);
                BufferedImage image = pdfRenderer.renderImageWithDPI(pageIndex, dpi);

                // Convert BufferedImage to bytes
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                ImageIO.write(image, "png", baos);
                byte[] imageBytes = baos.toByteArray();

                // Extract text from image (sincronizado porque Tesseract não é thread-safe)
                String pageText;
                synchronized (TESSERACT_LOCK) {
                    pageText = tesseract.doOCR(ImageIO.read(new ByteArrayInputStream(imageBytes)));
                }

                log.info("Text extraction completed for page {}. Extracted {} characters",
                        pageNumber, pageText != null ? pageText.length() : 0);

                return pageText != null ? pageText : "";

            } catch (IOException | TesseractException e) {
                log.error("Error during PDF page text extraction processing", e);
                throw new RuntimeException("Text extraction failed for PDF page: " + e.getMessage(), e);
            }
        }).subscribeOn(Schedulers.boundedElastic());
    }
}

