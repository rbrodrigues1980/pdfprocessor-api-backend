package br.com.verticelabs.pdfprocessor.application.documents;

import br.com.verticelabs.pdfprocessor.domain.repository.PayrollDocumentRepository;
import br.com.verticelabs.pdfprocessor.domain.repository.PayrollEntryRepository;
import br.com.verticelabs.pdfprocessor.domain.service.GridFsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentDeleteUseCase {

    private final PayrollDocumentRepository documentRepository;
    private final PayrollEntryRepository entryRepository;
    private final GridFsService gridFsService;

    /**
     * Exclui um documento e todos os dados relacionados:
     * - Todas as entries (payroll_entries) do documento
     * - O arquivo PDF do GridFS (se existir)
     * - O documento (payroll_documents)
     */
    public Mono<Void> deleteDocument(String documentId) {
        log.info("=== INÍCIO DA EXCLUSÃO DO DOCUMENTO ===");
        log.info("DocumentId: {}", documentId);

        return documentRepository.findById(documentId)
                .switchIfEmpty(Mono.error(new IllegalArgumentException("Documento não encontrado: " + documentId)))
                .flatMap(document -> {
                    log.info("Documento encontrado. Iniciando exclusão de dados relacionados...");
                    
                    // 1. Excluir todas as entries relacionadas
                    log.info("Excluindo entries do documento...");
                    return entryRepository.deleteByDocumentoId(documentId)
                            .doOnSuccess(v -> log.info("Entries excluídas com sucesso"))
                            .doOnError(error -> log.error("Erro ao excluir entries: {}", error.getMessage()))
                            .thenReturn(document);
                })
                .flatMap(document -> {
                    // 2. Excluir arquivo do GridFS (se existir)
                    if (document.getOriginalFileId() != null && !document.getOriginalFileId().isEmpty()) {
                        log.info("Excluindo arquivo do GridFS (fileId: {})...", document.getOriginalFileId());
                        return gridFsService.deleteFile(document.getOriginalFileId())
                                .doOnSuccess(v -> log.info("Arquivo excluído do GridFS com sucesso"))
                                .doOnError(error -> log.warn("Erro ao excluir arquivo do GridFS (pode não existir): {}", error.getMessage()))
                                .onErrorResume(error -> Mono.empty()) // Continua mesmo se o arquivo não existir
                                .thenReturn(document);
                    } else {
                        log.info("Documento não possui originalFileId, pulando exclusão do GridFS");
                        return Mono.just(document);
                    }
                })
                .flatMap(document -> {
                    // 3. Excluir o documento
                    log.info("Excluindo documento...");
                    return documentRepository.deleteById(documentId)
                            .doOnSuccess(v -> log.info("Documento excluído com sucesso"))
                            .doOnError(error -> log.error("Erro ao excluir documento: {}", error.getMessage()));
                })
                .doOnSuccess(v -> log.info("=== EXCLUSÃO CONCLUÍDA COM SUCESSO ==="))
                .doOnError(error -> log.error("=== ERRO NA EXCLUSÃO: {} ===", error.getMessage()));
    }
}

