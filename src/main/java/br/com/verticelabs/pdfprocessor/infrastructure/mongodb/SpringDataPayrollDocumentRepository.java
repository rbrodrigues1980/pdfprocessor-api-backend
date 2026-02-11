package br.com.verticelabs.pdfprocessor.infrastructure.mongodb;

import br.com.verticelabs.pdfprocessor.domain.model.DocumentStatus;
import br.com.verticelabs.pdfprocessor.domain.model.PayrollDocument;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Repository
public interface SpringDataPayrollDocumentRepository extends ReactiveMongoRepository<PayrollDocument, String> {
    Mono<PayrollDocument> findByTenantIdAndId(String tenantId, String id);
    
    Flux<PayrollDocument> findAllByTenantId(String tenantId);
    
    Flux<PayrollDocument> findByTenantIdAndStatus(String tenantId, DocumentStatus status);
    
    Flux<PayrollDocument> findByTenantIdAndCpf(String tenantId, String cpf);
    
    Mono<PayrollDocument> findByTenantIdAndFileHash(String tenantId, String fileHash);
    
    Mono<Long> countByTenantId(String tenantId);
    
    // Métodos legados
    @Deprecated
    Flux<PayrollDocument> findByStatus(DocumentStatus status);
    
    @Deprecated
    Flux<PayrollDocument> findByCpf(String cpf);
    
    @Deprecated
    Mono<PayrollDocument> findByFileHash(String fileHash);
}

