package br.com.verticelabs.pdfprocessor.domain.repository;

import br.com.verticelabs.pdfprocessor.domain.model.AuditLog;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface AuditLogRepository {
    Mono<AuditLog> save(AuditLog auditLog);
    
    Flux<AuditLog> findByTenantId(String tenantId);
    
    Flux<AuditLog> findByTenantIdAndUserId(String tenantId, String userId);
    
    Flux<AuditLog> findByTenantIdAndEvento(String tenantId, String evento);
}

