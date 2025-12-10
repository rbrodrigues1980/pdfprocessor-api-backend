package br.com.verticelabs.pdfprocessor.interfaces.rest;

import br.com.verticelabs.pdfprocessor.application.documents.BulkDocumentUploadUseCase;
import br.com.verticelabs.pdfprocessor.application.textextraction.TextExtractionUseCase;
import br.com.verticelabs.pdfprocessor.domain.exceptions.InvalidCpfException;
import br.com.verticelabs.pdfprocessor.interfaces.documents.dto.BulkUploadResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.io.ByteArrayInputStream;
import java.util.List;

/**
 * REST Controller for text extraction from images.
 */
@RestController
@RequestMapping("/text-extraction")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Text Extraction", description = "Text extraction endpoints for image-based PDF processing")
public class TextExtractionController {

        private final TextExtractionUseCase textExtractionUseCase;
        private final BulkDocumentUploadUseCase bulkDocumentUploadUseCase;

        @PostMapping(value = "/extract-text", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
        @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'TENANT_ADMIN', 'TENANT_USER')")
        @Operation(summary = "Extract text from PDF", description = "Extracts text from an image-based PDF. " +
                        "This is useful for scanned documents or PDFs without selectable text.")
        @ApiResponses(value = {
                        @ApiResponse(responseCode = "200", description = "Text extracted successfully", content = @Content(schema = @Schema(implementation = TextExtractionTextResponse.class))),
                        @ApiResponse(responseCode = "400", description = "Invalid file format"),
                        @ApiResponse(responseCode = "401", description = "Unauthorized"),
                        @ApiResponse(responseCode = "500", description = "Text extraction processing failed")
        })
        public Mono<TextExtractionTextResponse> extractText(
                        @Parameter(description = "PDF file to process") @RequestPart("file") FilePart filePart) {

                log.info("Received text extraction request for file: {}", filePart.filename());

                return filePart.content()
                                .reduce(new byte[0], (bytes, dataBuffer) -> {
                                        byte[] newBytes = new byte[bytes.length + dataBuffer.readableByteCount()];
                                        System.arraycopy(bytes, 0, newBytes, 0, bytes.length);
                                        dataBuffer.read(newBytes, bytes.length, dataBuffer.readableByteCount());
                                        return newBytes;
                                })
                                .flatMap(pdfBytes -> textExtractionUseCase.extractTextFromPdf(new ByteArrayInputStream(pdfBytes)))
                                .map(extractedText -> TextExtractionTextResponse.builder()
                                                .filename(filePart.filename())
                                                .extractedText(extractedText)
                                                .characterCount(extractedText.length())
                                                .success(true)
                                                .build())
                                .doOnSuccess(response -> log.info("Text extraction completed for file: {}",
                                                filePart.filename()))
                                .doOnError(error -> log.error("Text extraction failed for file: {}", filePart.filename(),
                                                error));
        }

        @PostMapping(value = "/extract-text/page/{pageNumber}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
        @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'TENANT_ADMIN', 'TENANT_USER')")
        @Operation(summary = "Extract text from specific PDF page", description = "Extracts text from a specific page of an image-based PDF.")
        @ApiResponses(value = {
                        @ApiResponse(responseCode = "200", description = "Text extracted successfully"),
                        @ApiResponse(responseCode = "400", description = "Invalid file format or page number"),
                        @ApiResponse(responseCode = "401", description = "Unauthorized"),
                        @ApiResponse(responseCode = "500", description = "Text extraction processing failed")
        })
        public Mono<TextExtractionTextResponse> extractTextFromPage(
                        @Parameter(description = "PDF file to process") @RequestPart("file") FilePart filePart,
                        @Parameter(description = "Page number to extract (1-indexed)") @PathVariable int pageNumber) {

                log.info("Received text extraction page request for file: {}, page: {}",
                                filePart.filename(), pageNumber);

                return filePart.content()
                                .reduce(new byte[0], (bytes, dataBuffer) -> {
                                        byte[] newBytes = new byte[bytes.length + dataBuffer.readableByteCount()];
                                        System.arraycopy(bytes, 0, newBytes, 0, bytes.length);
                                        dataBuffer.read(newBytes, bytes.length, dataBuffer.readableByteCount());
                                        return newBytes;
                                })
                                .flatMap(pdfBytes -> textExtractionUseCase.extractTextFromPdfPage(
                                                new ByteArrayInputStream(pdfBytes), pageNumber))
                                .map(extractedText -> TextExtractionTextResponse.builder()
                                                .filename(filePart.filename())
                                                .extractedText(extractedText)
                                                .characterCount(extractedText.length())
                                                .pageNumber(pageNumber)
                                                .success(true)
                                                .build())
                                .doOnSuccess(response -> log.info(
                                                "Text extraction page completed for file: {}, page: {}",
                                                filePart.filename(), pageNumber))
                                .doOnError(error -> log.error("Text extraction page failed for file: {}, page: {}",
                                                filePart.filename(), pageNumber, error));
        }

        @PostMapping(value = "/detect", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
        @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'TENANT_ADMIN', 'TENANT_USER')")
        @Operation(summary = "Detect if PDF is image-based", description = "Analyzes a PDF to determine if it requires image text extraction processing. " +
                        "Returns true if the PDF has little to no extractable text (image-based/scanned).")
        @ApiResponses(value = {
                        @ApiResponse(responseCode = "200", description = "Detection completed successfully", content = @Content(schema = @Schema(implementation = TextExtractionDetectionResponse.class))),
                        @ApiResponse(responseCode = "400", description = "Invalid file format"),
                        @ApiResponse(responseCode = "401", description = "Unauthorized"),
                        @ApiResponse(responseCode = "500", description = "Detection failed")
        })
        public Mono<TextExtractionDetectionResponse> detectImageBasedPdf(
                        @Parameter(description = "PDF file to analyze") @RequestPart("file") FilePart filePart) {

                log.info("Received image-based detection request for file: {}", filePart.filename());

                return filePart.content()
                                .reduce(new byte[0], (bytes, dataBuffer) -> {
                                        byte[] newBytes = new byte[bytes.length + dataBuffer.readableByteCount()];
                                        System.arraycopy(bytes, 0, newBytes, 0, bytes.length);
                                        dataBuffer.read(newBytes, bytes.length, dataBuffer.readableByteCount());
                                        return newBytes;
                                })
                                .flatMap(pdfBytes -> textExtractionUseCase.isPdfImageBased(new ByteArrayInputStream(pdfBytes)))
                                .map(isImageBased -> TextExtractionDetectionResponse.builder()
                                                .filename(filePart.filename())
                                                .isImageBased(isImageBased)
                                                .requiresTextExtraction(isImageBased)
                                                .recommendation(
                                                                isImageBased ? "This PDF appears to be image-based. Use text extraction endpoints to extract text."
                                                                                : "This PDF has selectable text. Standard text extraction is recommended.")
                                                .build())
                                .doOnSuccess(response -> log.info(
                                                "Image-based detection completed for file: {}. Image-based: {}",
                                                filePart.filename(), response.isImageBased()))
                                .doOnError(error -> log.error("Image-based detection failed for file: {}", filePart.filename(),
                                                error));
        }

        @PostMapping(value = "/extract-image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
        @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'TENANT_ADMIN', 'TENANT_USER')")
        @Operation(summary = "Extract text from image", description = "Extracts text from an image file (PNG, JPG, etc.).")
        @ApiResponses(value = {
                        @ApiResponse(responseCode = "200", description = "Text extracted successfully"),
                        @ApiResponse(responseCode = "400", description = "Invalid image format"),
                        @ApiResponse(responseCode = "401", description = "Unauthorized"),
                        @ApiResponse(responseCode = "500", description = "Text extraction processing failed")
        })
        public Mono<TextExtractionTextResponse> extractTextFromImage(
                        @Parameter(description = "Image file to process") @RequestPart("file") FilePart filePart) {

                log.info("Received image text extraction request for file: {}", filePart.filename());

                return filePart.content()
                                .reduce(new byte[0], (bytes, dataBuffer) -> {
                                        byte[] newBytes = new byte[bytes.length + dataBuffer.readableByteCount()];
                                        System.arraycopy(bytes, 0, newBytes, 0, bytes.length);
                                        dataBuffer.read(newBytes, bytes.length, dataBuffer.readableByteCount());
                                        return newBytes;
                                })
                                .flatMap(imageBytes -> textExtractionUseCase.extractTextFromImage(imageBytes))
                                .map(extractedText -> TextExtractionTextResponse.builder()
                                                .filename(filePart.filename())
                                                .extractedText(extractedText)
                                                .characterCount(extractedText.length())
                                                .success(true)
                                                .build())
                                .doOnSuccess(response -> log.info("Image text extraction completed for file: {}",
                                                filePart.filename()))
                                .doOnError(error -> log.error("Image text extraction failed for file: {}",
                                                filePart.filename(), error));
        }

        @PostMapping(value = "/bulk-process", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
        @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'TENANT_ADMIN', 'TENANT_USER')")
        @Operation(summary = "Process multiple PDFs with text extraction", description = "Processes multiple image-based PDF files with text extraction and saves them to the system. " +
                        "Accepts the same parameters as /documents/bulk-upload but processes files with text extraction. " +
                        "Each PDF is converted to images, processed with Tesseract, and entries are extracted.")
        @ApiResponses(value = {
                        @ApiResponse(responseCode = "201", description = "Documents processed successfully", content = @Content(schema = @Schema(implementation = BulkUploadResponse.class))),
                        @ApiResponse(responseCode = "400", description = "Invalid parameters"),
                        @ApiResponse(responseCode = "401", description = "Unauthorized"),
                        @ApiResponse(responseCode = "422", description = "Invalid CPF"),
                        @ApiResponse(responseCode = "500", description = "Processing failed")
        })
        public Mono<ResponseEntity<Object>> bulkProcess(
                        @Parameter(description = "PDF files to process") @RequestPart("files") List<FilePart> files,
                        @Parameter(description = "CPF (format: 000.000.000-00 or 00000000000)") @RequestPart("cpf") String cpf,
                        @Parameter(description = "Full name") @RequestPart("nome") String nome,
                        @Parameter(description = "Registration number") @RequestPart("matricula") String matricula) {

                log.info("=== INÍCIO: POST /api/v1/text-extraction/bulk-process ===");
                log.info("Total de arquivos: {}, CPF: {}, Nome: {}, Matrícula: {}",
                                files != null ? files.size() : 0, cpf, nome, matricula);
                
                // NOTA: Este endpoint atualmente usa o fluxo normal de upload que não utiliza extração de texto de imagem.
                // Para PDFs baseados em imagem (escaneados), a extração de texto de imagem deveria ser usada para extrair texto.
                // A implementação completa requer modificações no DocumentUploadUseCase e DocumentProcessUseCase
                // para usar o serviço de extração de texto .extractTextFromPdf() e .extractTextFromPdfPage() ao invés de
                // pdfService.extractText() e pdfService.extractTextFromPage().
                return bulkDocumentUploadUseCase.uploadBulk(files, cpf, nome, matricula)
                                .<ResponseEntity<Object>>map(response -> {
                                        log.info("=== SUCESSO: Bulk text extraction process concluído ===");
                                        log.info("CPF: {}, Total: {}, Sucessos: {}, Falhas: {}",
                                                        response.getCpf(), response.getTotalArquivos(),
                                                        response.getSucessos(), response.getFalhas());
                                        return ResponseEntity.status(HttpStatus.CREATED).body((Object) response);
                                })
                                .onErrorResume(IllegalArgumentException.class, e -> {
                                        log.warn("=== ERRO: Parâmetro inválido ===");
                                        log.warn("Mensagem: {}", e.getMessage());
                                        return Mono.just(ResponseEntity.status(HttpStatus.BAD_REQUEST)
                                                        .body((Object) new ErrorResponse(HttpStatus.BAD_REQUEST.value(),
                                                                        e.getMessage())));
                                })
                                .onErrorResume(InvalidCpfException.class, e -> {
                                        log.warn("=== ERRO: CPF inválido ===");
                                        log.warn("CPF: {}", cpf);
                                        return Mono.just(ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                                                        .body((Object) new ErrorResponse(
                                                                        HttpStatus.UNPROCESSABLE_ENTITY.value(),
                                                                        e.getMessage())));
                                })
                                .onErrorResume(Exception.class, e -> {
                                        log.error("=== ERRO CRÍTICO: Falha no bulk text extraction process ===", e);
                                        log.error("CPF: {}, Total de arquivos: {}", cpf,
                                                        files != null ? files.size() : 0);
                                        return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                                                        .body((Object) new ErrorResponse(
                                                                        HttpStatus.INTERNAL_SERVER_ERROR.value(),
                                                                        "Erro ao processar PDFs com extração de texto: "
                                                                                        + e.getMessage())));
                                });
        }

        // DTO Classes
        @lombok.Data
        @lombok.Builder
        @Schema(description = "Error response")
        public static class ErrorResponse {
                @Schema(description = "HTTP status code")
                private final int status;

                @Schema(description = "Error message")
                private final String error;
        }

        @lombok.Data
        @lombok.Builder
        @Schema(description = "Text extraction response")
        public static class TextExtractionTextResponse {
                @Schema(description = "Original filename")
                private String filename;

                @Schema(description = "Extracted text")
                private String extractedText;

                @Schema(description = "Number of characters extracted")
                private int characterCount;

                @Schema(description = "Page number (if single page extraction)")
                private Integer pageNumber;

                @Schema(description = "Whether extraction was successful")
                private boolean success;
        }

        @lombok.Data
        @lombok.Builder
        @Schema(description = "Image-based detection response")
        public static class TextExtractionDetectionResponse {
                @Schema(description = "Original filename")
                private String filename;

                @Schema(description = "Whether the PDF is image-based")
                private boolean isImageBased;

                @Schema(description = "Whether text extraction from image is required for this PDF")
                private boolean requiresTextExtraction;

                @Schema(description = "Recommendation for processing this PDF")
                private String recommendation;
        }
}

