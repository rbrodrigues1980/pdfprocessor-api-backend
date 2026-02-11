package br.com.verticelabs.pdfprocessor.application.documents;

import br.com.verticelabs.pdfprocessor.domain.exceptions.PersonNotFoundException;
import br.com.verticelabs.pdfprocessor.domain.repository.PayrollDocumentRepository;
import br.com.verticelabs.pdfprocessor.domain.repository.PayrollEntryRepository;
import br.com.verticelabs.pdfprocessor.domain.repository.PersonRepository;
import br.com.verticelabs.pdfprocessor.domain.service.GridFsService;
import br.com.verticelabs.pdfprocessor.infrastructure.security.ReactiveSecurityContextHelper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.ArrayList;

@Slf4j
@Service
@RequiredArgsConstructor
public class DeleteDocumentUseCase {

    private final PayrollDocumentRepository documentRepository;
    private final PayrollEntryRepository entryRepository;
    private final PersonRepository personRepository;
    private final GridFsService gridFsService;

    /**
     * Exclui um documento e todas as suas referências:
     * - PayrollDocument
     * - PayrollEntry relacionadas (pelo documentoId)
     * - Arquivo no GridFS (fs.files e fs.chunks)
     * - Referência do documento na lista de documentos da Person
     */
    public Mono<Void> execute(String documentId) {
        log.info("=== INÍCIO DA EXCLUSÃO DE DOCUMENTO ===");
        log.info("DocumentId: {}", documentId);

        // 1. Buscar documento com validação de acesso
        return ReactiveSecurityContextHelper.isSuperAdmin()
                .flatMap(isSuperAdmin -> {
                    if (Boolean.TRUE.equals(isSuperAdmin)) {
                        // SUPER_ADMIN pode buscar qualquer documento
                        // Primeiro tentar buscar pelo tenantId do contexto, se não houver, buscar em todos
                        return ReactiveSecurityContextHelper.getTenantId()
                                .flatMap(tenantId -> documentRepository.findByTenantIdAndId(tenantId, documentId))
                                .onErrorResume(IllegalStateException.class, e -> {
                                    // Se não houver tenantId no contexto (SUPER_ADMIN sem tenant específico),
                                    // usar método deprecated findById para buscar em qualquer tenant
                                    // Nota: Este é o único caso onde usamos o método deprecated, necessário para SUPER_ADMIN
                                    @SuppressWarnings("deprecation")
                                    var result = documentRepository.findById(documentId);
                                    return result;
                                });
                    } else {
                        // Outros usuários só podem buscar documentos do seu tenant
                        return ReactiveSecurityContextHelper.getTenantId()
                                .flatMap(tenantId -> documentRepository.findByTenantIdAndId(tenantId, documentId));
                    }
                })
                .switchIfEmpty(Mono.error(new IllegalArgumentException("Documento não encontrado: " + documentId)))
                .flatMap(document -> {
                    log.info("✓ Documento encontrado: ID={}, CPF={}, Tipo={}, Status={}, FileId={}",
                            document.getId(), document.getCpf(), document.getTipo(), document.getStatus(),
                            document.getOriginalFileId());

                    String tenantId = document.getTenantId();

                    // 2. Deletar todas as entries relacionadas ao documento
                    log.info("Deletando entries relacionadas ao documento: {}", documentId);
                    return entryRepository.deleteByTenantIdAndDocumentoId(tenantId, documentId)
                            .doOnSuccess(v -> log.info("✓ Entries deletadas com sucesso"))
                            .then(Mono.just(document));
                })
                .flatMap(document -> {
                    String originalFileId = document.getOriginalFileId();

                    // 3. Deletar arquivo do GridFS (fs.files e fs.chunks)
                    if (originalFileId != null && !originalFileId.trim().isEmpty()) {
                        log.info("Deletando arquivo do GridFS: {}", originalFileId);
                        return gridFsService.deleteFile(originalFileId)
                                .doOnSuccess(v -> log.info("✓ Arquivo deletado do GridFS com sucesso"))
                                .onErrorResume(error -> {
                                    log.warn("⚠ Erro ao deletar arquivo do GridFS (pode não existir mais): {}", error.getMessage());
                                    // Continuar mesmo se o arquivo não existir
                                    return Mono.empty();
                                })
                                .then(Mono.just(document));
                    } else {
                        log.warn("⚠ Documento não possui originalFileId, pulando exclusão do GridFS");
                        return Mono.just(document);
                    }
                })
                .flatMap(document -> {
                    String tenantId = document.getTenantId();
                    String cpf = document.getCpf();
                    String docId = document.getId();

                    // 4. Remover referência do documento na lista de documentos da Person
                    log.info("Removendo referência do documento na Person (CPF: {})", cpf);
                    return personRepository.findByTenantIdAndCpf(tenantId, cpf)
                            .switchIfEmpty(Mono.error(new PersonNotFoundException("CPF: " + cpf)))
                            .flatMap(person -> {
                                // Remover documentId da lista de documentos
                                if (person.getDocumentos() != null && person.getDocumentos().contains(docId)) {
                                    // Criar nova lista sem o documento
                                    ArrayList<String> novosDocumentos = new ArrayList<>(person.getDocumentos());
                                    novosDocumentos.remove(docId);
                                    person.setDocumentos(novosDocumentos);
                                    
                                    log.info("Removendo documento {} da lista de documentos da Person. Total antes: {}, depois: {}",
                                            docId, person.getDocumentos().size() + 1, novosDocumentos.size());
                                    
                                    return personRepository.save(person)
                                            .doOnSuccess(p -> log.info("✓ Person atualizada. Documento removido da lista"))
                                            .thenReturn(document);
                                } else {
                                    log.warn("⚠ Documento {} não estava na lista de documentos da Person", docId);
                                    return Mono.just(document);
                                }
                            });
                })
                .flatMap(document -> {
                    String tenantId = document.getTenantId();
                    String docId = document.getId();

                    // 5. Deletar o documento em si
                    log.info("Deletando documento: {}", docId);
                    return documentRepository.deleteByTenantIdAndId(tenantId, docId)
                            .doOnSuccess(v -> {
                                log.info("=== EXCLUSÃO DE DOCUMENTO CONCLUÍDA COM SUCESSO ===");
                                log.info("DocumentId: {}", documentId);
                                log.info("✓ PayrollDocument deletado");
                                log.info("✓ PayrollEntries deletadas");
                                log.info("✓ Arquivo GridFS deletado");
                                log.info("✓ Referência removida da Person");
                            });
                });
    }
}

