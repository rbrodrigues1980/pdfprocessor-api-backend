package br.com.verticelabs.pdfprocessor.infrastructure.mongodb;

import br.com.verticelabs.pdfprocessor.domain.model.PayrollEntry;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Repository
public interface SpringDataPayrollEntryRepository extends ReactiveMongoRepository<PayrollEntry, String> {
    Flux<PayrollEntry> findByTenantIdAndDocumentoId(String tenantId, String documentoId);
    
    Flux<PayrollEntry> findAllByTenantId(String tenantId);
    
    Mono<Long> countByTenantIdAndDocumentoId(String tenantId, String documentoId);
    
    Mono<Long> countByTenantId(String tenantId);
    
    // Métodos legados
    @Deprecated
    Flux<PayrollEntry> findByDocumentoId(String documentoId);
    
    @Deprecated
    Mono<Long> countByDocumentoId(String documentoId);
}

