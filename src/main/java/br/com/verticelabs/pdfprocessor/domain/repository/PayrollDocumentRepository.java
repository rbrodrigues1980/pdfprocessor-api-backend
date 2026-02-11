package br.com.verticelabs.pdfprocessor.domain.repository;

import br.com.verticelabs.pdfprocessor.domain.model.DocumentStatus;
import br.com.verticelabs.pdfprocessor.domain.model.DocumentType;
import br.com.verticelabs.pdfprocessor.domain.model.PayrollDocument;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface PayrollDocumentRepository {
    Mono<PayrollDocument> save(PayrollDocument document);
    
    Mono<PayrollDocument> findByTenantIdAndId(String tenantId, String id);
    
    Flux<PayrollDocument> findAllByTenantId(String tenantId);
    
    Flux<PayrollDocument> findByTenantIdAndStatus(String tenantId, DocumentStatus status);
    
    Flux<PayrollDocument> findByTenantIdAndCpf(String tenantId, String cpf);
    
    Mono<PayrollDocument> findByTenantIdAndFileHash(String tenantId, String fileHash);
    
    Mono<Void> deleteByTenantIdAndId(String tenantId, String id);
    
    /**
     * Conta documentos por tenant
     */
    Mono<Long> countByTenantId(String tenantId);
    
    /**
     * Conta todos os documentos (sem filtrar por tenant) - para SUPER_ADMIN
     */
    Mono<Long> countAll();
    
    /**
     * Busca documentos com filtros dinâmicos (filtrado por tenant)
     */
    Flux<PayrollDocument> findByTenantIdAndFilters(
            String tenantId,
            String cpf,
            Integer ano,
            DocumentStatus status,
            DocumentType tipo,
            Long minEntries,
            Long maxEntries
    );
    
    // Métodos legados (manter para compatibilidade)
    @Deprecated
    Mono<PayrollDocument> findById(String id);
    
    @Deprecated
    Flux<PayrollDocument> findAll();
    
    @Deprecated
    Flux<PayrollDocument> findByStatus(DocumentStatus status);
    
    @Deprecated
    Flux<PayrollDocument> findByCpf(String cpf);
    
    @Deprecated
    Mono<PayrollDocument> findByFileHash(String fileHash);
    
    @Deprecated
    Mono<Void> deleteById(String id);
    
    @Deprecated
    Flux<PayrollDocument> findByFilters(
            String cpf,
            Integer ano,
            DocumentStatus status,
            DocumentType tipo,
            Long minEntries,
            Long maxEntries
    );
}

