package br.com.verticelabs.pdfprocessor.application.documents;

import br.com.verticelabs.pdfprocessor.domain.exceptions.DocumentNotFoundException;
import br.com.verticelabs.pdfprocessor.domain.exceptions.PersonNotFoundException;
import br.com.verticelabs.pdfprocessor.domain.model.DocumentStatus;
import br.com.verticelabs.pdfprocessor.domain.model.DocumentType;
import br.com.verticelabs.pdfprocessor.domain.model.PayrollDocument;
import br.com.verticelabs.pdfprocessor.domain.repository.PayrollDocumentRepository;
import br.com.verticelabs.pdfprocessor.domain.repository.PersonRepository;
import br.com.verticelabs.pdfprocessor.infrastructure.security.ReactiveSecurityContextHelper;
import br.com.verticelabs.pdfprocessor.interfaces.documents.dto.DocumentListResponse;
import br.com.verticelabs.pdfprocessor.interfaces.documents.dto.DocumentListItemResponse;
import br.com.verticelabs.pdfprocessor.interfaces.documents.dto.DocumentPageResponse;
import br.com.verticelabs.pdfprocessor.interfaces.documents.dto.DocumentResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentQueryUseCase {

    private final PayrollDocumentRepository documentRepository;
    private final PersonRepository personRepository;

    /**
     * Busca um documento por ID
     */
    public Mono<DocumentResponse> findById(String id) {
        log.info("=== DocumentQueryUseCase.findById() INICIADO ===");
        log.info("ID do documento: {}", id);

        return documentRepository.findById(id)
                .switchIfEmpty(Mono.defer(() -> {
                    log.warn("Documento não encontrado: {}", id);
                    return Mono.error(new DocumentNotFoundException("Documento não encontrado: " + id));
                }))
                .map(this::toDocumentResponse)
                .doOnSuccess(response -> {
                    log.info("✓ Documento encontrado: {} - Status: {}", response.getId(), response.getStatus());
                })
                .doOnError(error -> {
                    log.error("Erro ao buscar documento: {}", id, error);
                });
    }

    /**
     * Lista todos os documentos de uma pessoa específica (por personId)
     */
    public Mono<DocumentListResponse> findByPersonId(String personId) {
        log.info("=== DocumentQueryUseCase.findByPersonId() INICIADO ===");
        log.info("PersonId: {}", personId);

        return ReactiveSecurityContextHelper.isSuperAdmin()
                .flatMap(isSuperAdmin -> {
                    if (isSuperAdmin) {
                        // SUPER_ADMIN: buscar pessoa diretamente pelo ID
                        return findDocumentsByPersonIdForSuperAdmin(personId);
                    } else {
                        // Outros usuários: validar que a pessoa pertence ao seu tenant
                        return ReactiveSecurityContextHelper.getTenantId()
                                .flatMap(tenantId -> findDocumentsByPersonIdForTenant(tenantId, personId));
                    }
                })
                .doOnError(error -> {
                    log.error("Erro ao buscar documentos do personId: {}", personId, error);
                });
    }

    private Mono<DocumentListResponse> findDocumentsByPersonIdForSuperAdmin(String personId) {
        // SUPER_ADMIN: buscar pessoa diretamente pelo ID (sem filtrar por tenantId)
        return personRepository.findById(personId)
                .switchIfEmpty(Mono.defer(() -> {
                    log.warn("Pessoa não encontrada com personId: {}", personId);
                    return Mono.error(new PersonNotFoundException("Pessoa não encontrada: " + personId));
                }))
                .flatMap(person -> {
                    log.info("✓ Pessoa encontrada: {} ({})", person.getNome(), person.getCpf());
                    // Buscar documentos usando tenantId e CPF da pessoa encontrada
                    return documentRepository.findByTenantIdAndCpf(person.getTenantId(), person.getCpf())
                            .map(doc -> DocumentListItemResponse.builder()
                                    .id(doc.getId())
                                    .ano(doc.getAnoDetectado())
                                    .status(doc.getStatus())
                                    .tipo(documentTypeToString(doc.getTipo()))
                                    .mesesDetectados(doc.getMesesDetectados())
                                    .dataUpload(doc.getDataUpload())
                                    .dataProcessamento(doc.getDataProcessamento())
                                    .totalEntries(doc.getTotalEntries())
                                    .build())
                            .collectList()
                            .map(docs -> {
                                log.info("✓ {} documentos encontrados para personId: {} (SUPER_ADMIN)", docs.size(), personId);
                                return DocumentListResponse.builder()
                                        .cpf(person.getCpf())
                                        .documentos(docs)
                                        .build();
                            });
                });
    }

    private Mono<DocumentListResponse> findDocumentsByPersonIdForTenant(String tenantId, String personId) {
        // Buscar pessoa e validar que pertence ao tenant do usuário
        return personRepository.findById(personId)
                .switchIfEmpty(Mono.defer(() -> {
                    log.warn("Pessoa não encontrada com personId: {}", personId);
                    return Mono.error(new PersonNotFoundException("Pessoa não encontrada: " + personId));
                }))
                .flatMap(person -> {
                    // Validar que a pessoa pertence ao tenant do usuário
                    if (!tenantId.equals(person.getTenantId())) {
                        log.warn("Pessoa {} não pertence ao tenant {} (pertence a {})", personId, tenantId, person.getTenantId());
                        return Mono.error(new PersonNotFoundException("Pessoa não encontrada: " + personId));
                    }
                    
                    log.info("✓ Pessoa encontrada: {} ({})", person.getNome(), person.getCpf());
                    // Buscar documentos usando tenantId e CPF da pessoa
                    return documentRepository.findByTenantIdAndCpf(tenantId, person.getCpf())
                            .map(doc -> DocumentListItemResponse.builder()
                                    .id(doc.getId())
                                    .ano(doc.getAnoDetectado())
                                    .status(doc.getStatus())
                                    .tipo(documentTypeToString(doc.getTipo()))
                                    .mesesDetectados(doc.getMesesDetectados())
                                    .dataUpload(doc.getDataUpload())
                                    .dataProcessamento(doc.getDataProcessamento())
                                    .totalEntries(doc.getTotalEntries())
                                    .build())
                            .collectList()
                            .map(docs -> {
                                log.info("✓ {} documentos encontrados para personId: {} no tenant: {}", docs.size(), personId, tenantId);
                                return DocumentListResponse.builder()
                                        .cpf(person.getCpf())
                                        .documentos(docs)
                                        .build();
                            });
                });
    }

    /**
     * Lista todos os documentos de um CPF
     */
    public Mono<DocumentListResponse> findByCpf(String cpf) {
        log.info("=== DocumentQueryUseCase.findByCpf() INICIADO ===");
        log.info("CPF: {}", cpf);

        return ReactiveSecurityContextHelper.isSuperAdmin()
                .flatMap(isSuperAdmin -> {
                    if (isSuperAdmin) {
                        // SUPER_ADMIN pode ver documentos de qualquer tenant
                        return findDocumentsByCpfForSuperAdmin(cpf);
                    } else {
                        // Outros usuários só veem documentos do seu tenant
                        return ReactiveSecurityContextHelper.getTenantId()
                                .flatMap(tenantId -> findDocumentsByCpfForTenant(tenantId, cpf));
                    }
                })
                .doOnError(error -> {
                    log.error("Erro ao buscar documentos do CPF: {}", cpf, error);
                });
    }

    private Mono<DocumentListResponse> findDocumentsByCpfForSuperAdmin(String cpf) {
        // SUPER_ADMIN: buscar documentos diretamente pelo CPF (sem filtrar por tenantId)
        return documentRepository.findByCpf(cpf)
                .map(doc -> DocumentListItemResponse.builder()
                        .id(doc.getId())
                        .ano(doc.getAnoDetectado())
                        .status(doc.getStatus())
                        .tipo(documentTypeToString(doc.getTipo()))
                        .mesesDetectados(doc.getMesesDetectados())
                        .dataUpload(doc.getDataUpload())
                        .dataProcessamento(doc.getDataProcessamento())
                        .totalEntries(doc.getTotalEntries())
                        .build())
                .collectList()
                .map(docs -> {
                    log.info("✓ {} documentos encontrados para CPF: {} (SUPER_ADMIN)", docs.size(), cpf);
                    return DocumentListResponse.builder()
                            .cpf(cpf)
                            .documentos(docs)
                            .build();
                });
    }

    private Mono<DocumentListResponse> findDocumentsByCpfForTenant(String tenantId, String cpf) {
        // Validar se a pessoa existe no tenant antes de buscar documentos
        return personRepository.findByTenantIdAndCpf(tenantId, cpf)
                .switchIfEmpty(Mono.defer(() -> {
                    log.warn("Pessoa não encontrada para CPF: {} no tenant: {}", cpf, tenantId);
                    return Mono.error(new PersonNotFoundException("Pessoa não encontrada: " + cpf));
                }))
                .flatMap(person -> {
                    log.info("✓ Pessoa encontrada: {} ({})", person.getNome(), person.getCpf());
                    // Buscar documentos filtrando por tenantId e CPF
                    return documentRepository.findByTenantIdAndCpf(tenantId, cpf)
                            .map(doc -> DocumentListItemResponse.builder()
                                    .id(doc.getId())
                                    .ano(doc.getAnoDetectado())
                                    .status(doc.getStatus())
                                    .tipo(documentTypeToString(doc.getTipo()))
                                    .mesesDetectados(doc.getMesesDetectados())
                                    .dataUpload(doc.getDataUpload())
                                    .dataProcessamento(doc.getDataProcessamento())
                                    .totalEntries(doc.getTotalEntries())
                                    .build())
                            .collectList()
                            .map(docs -> {
                                log.info("✓ {} documentos encontrados para CPF: {} no tenant: {}", docs.size(), cpf, tenantId);
                                return DocumentListResponse.builder()
                                        .cpf(cpf)
                                        .documentos(docs)
                                        .build();
                            });
                });
    }

    /**
     * Busca documentos com filtros
     */
    public Flux<DocumentResponse> findByFilters(
            String cpf,
            Integer ano,
            String statusStr,
            String tipoStr,
            Long minEntries,
            Long maxEntries) {
        log.info("=== DocumentQueryUseCase.findByFilters() INICIADO ===");
        log.info("Filtros - CPF: {}, Ano: {}, Status: {}, Tipo: {}, MinEntries: {}, MaxEntries: {}",
                cpf, ano, statusStr, tipoStr, minEntries, maxEntries);

        DocumentStatus status = null;
        if (statusStr != null && !statusStr.trim().isEmpty()) {
            try {
                status = DocumentStatus.valueOf(statusStr.toUpperCase());
            } catch (IllegalArgumentException e) {
                log.warn("Status inválido: {}", statusStr);
                return Flux.error(new IllegalArgumentException("Status inválido: " + statusStr));
            }
        }

        DocumentType tipo = null;
        if (tipoStr != null && !tipoStr.trim().isEmpty()) {
            try {
                tipo = DocumentType.valueOf(tipoStr.toUpperCase());
            } catch (IllegalArgumentException e) {
                log.warn("Tipo inválido: {}", tipoStr);
                return Flux.error(new IllegalArgumentException("Tipo inválido: " + tipoStr));
            }
        }

        return documentRepository.findByFilters(cpf, ano, status, tipo, minEntries, maxEntries)
                .map(this::toDocumentResponse)
                .doOnComplete(() -> {
                    log.info("✓ Busca com filtros concluída");
                })
                .doOnError(error -> {
                    log.error("Erro ao buscar documentos com filtros", error);
                });
    }

    /**
     * Busca informações das páginas de um documento
     */
    public Mono<DocumentPageResponse> findPagesById(String id) {
        log.info("=== DocumentQueryUseCase.findPagesById() INICIADO ===");
        log.info("ID do documento: {}", id);

        return documentRepository.findById(id)
                .switchIfEmpty(Mono.defer(() -> {
                    log.warn("Documento não encontrado: {}", id);
                    return Mono.error(new DocumentNotFoundException("Documento não encontrado: " + id));
                }))
                .map(document -> {
                    var pages = document.getDetectedPages().stream()
                            .map(page -> DocumentPageResponse.PageInfo.builder()
                                    .page(page.getPage())
                                    .origem(page.getOrigem())
                                    .build())
                            .collect(Collectors.toList());

                    log.info("✓ {} páginas encontradas para documento: {}", pages.size(), id);
                    return DocumentPageResponse.builder()
                            .documentId(id)
                            .pages(pages)
                            .build();
                })
                .doOnError(error -> {
                    log.error("Erro ao buscar páginas do documento: {}", id, error);
                });
    }

    /**
     * Converte DocumentType para String, mapeando INCOME_TAX para "IRPF"
     */
    private String documentTypeToString(DocumentType tipo) {
        if (tipo == null) {
            return null;
        }
        if (tipo == DocumentType.INCOME_TAX) {
            return "IRPF";
        }
        return tipo.name();
    }

    private DocumentResponse toDocumentResponse(PayrollDocument document) {
        return DocumentResponse.builder()
                .id(document.getId())
                .cpf(document.getCpf())
                .status(document.getStatus())
                .tipo(document.getTipo())
                .ano(document.getAnoDetectado())
                .entriesCount(document.getTotalEntries())
                .dataUpload(document.getDataUpload())
                .dataProcessamento(document.getDataProcessamento())
                .erro(document.getErro())
                .build();
    }
}

