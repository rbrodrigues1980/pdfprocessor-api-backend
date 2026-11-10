package br.com.verticelabs.pdfprocessor.application.documents;

import br.com.verticelabs.pdfprocessor.domain.exceptions.DocumentNotFoundException;
import br.com.verticelabs.pdfprocessor.domain.model.PayrollDocument;
import br.com.verticelabs.pdfprocessor.domain.repository.PayrollDocumentRepository;
import br.com.verticelabs.pdfprocessor.domain.service.GridFsService;
import br.com.verticelabs.pdfprocessor.infrastructure.security.ReactiveSecurityContextHelper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.io.InputStream;

@Slf4j
@Service
@RequiredArgsConstructor
public class DownloadDocumentFileUseCase {

    private final PayrollDocumentRepository documentRepository;
    private final GridFsService gridFsService;

    public Mono<DownloadResult> execute(String documentId) {
        return findAccessibleDocument(documentId)
                .switchIfEmpty(Mono.error(new DocumentNotFoundException("Documento não encontrado")))
                .flatMap(document -> {
                    String fileId = document.getOriginalFileId();
                    if (fileId == null || fileId.isBlank()) {
                        return Mono.error(new DocumentNotFoundException(
                                "Arquivo original não disponível para este documento"));
                    }

                    String fallbackName = fallbackFileName(document);
                    return gridFsService.openFile(fileId)
                            .switchIfEmpty(Mono.error(new DocumentNotFoundException(
                                    "Arquivo original não encontrado")))
                            .map(stored -> new DownloadResult(
                                    stored.inputStream(),
                                    safeFileName(stored.filename(), fallbackName),
                                    stored.contentType() != null && !stored.contentType().isBlank()
                                            ? stored.contentType()
                                            : "application/pdf",
                                    stored.length()));
                });
    }

    private Mono<PayrollDocument> findAccessibleDocument(String documentId) {
        return ReactiveSecurityContextHelper.isSuperAdmin()
                .flatMap(isSuperAdmin -> {
                    if (Boolean.TRUE.equals(isSuperAdmin)) {
                        // SUPER_ADMIN: sempre cross-tenant (JWT pode ter tenantId legado)
                        @SuppressWarnings("deprecation")
                        var result = documentRepository.findById(documentId);
                        return result;
                    }
                    return ReactiveSecurityContextHelper.getTenantId()
                            .flatMap(tenantId -> documentRepository.findByTenantIdAndId(tenantId, documentId));
                });
    }

    private static String fallbackFileName(PayrollDocument document) {
        String tipo = document.getTipo() != null ? document.getTipo().name().toLowerCase() : "documento";
        if (document.getAnoDetectado() != null) {
            return tipo + "-" + document.getAnoDetectado() + ".pdf";
        }
        return tipo + ".pdf";
    }

    static String safeFileName(String raw, String fallback) {
        String name = raw == null ? "" : raw;
        int slash = Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\'));
        if (slash >= 0 && slash < name.length() - 1) {
            name = name.substring(slash + 1);
        }
        name = name.replace("\"", "")
                .replace("\r", "")
                .replace("\n", "")
                .trim();
        if (name.isBlank()) {
            name = fallback;
        }
        if (!name.toLowerCase().endsWith(".pdf")) {
            name = name + ".pdf";
        }
        return name;
    }

    public record DownloadResult(
            InputStream inputStream,
            String fileName,
            String contentType,
            long sizeBytes) {
    }
}
