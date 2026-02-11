package br.com.verticelabs.pdfprocessor.domain.repository;

import br.com.verticelabs.pdfprocessor.domain.model.PayrollEntry;
import br.com.verticelabs.pdfprocessor.interfaces.dashboard.dto.TotalPorAno;
import br.com.verticelabs.pdfprocessor.interfaces.dashboard.dto.TotalPorMes;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface PayrollEntryRepository {
    Mono<PayrollEntry> save(PayrollEntry entry);
    
    Flux<PayrollEntry> saveAll(Flux<PayrollEntry> entries);
    
    Flux<PayrollEntry> findByTenantIdAndDocumentoId(String tenantId, String documentoId);
    
    Flux<PayrollEntry> findAllByTenantId(String tenantId);
    
    Mono<Long> countByTenantIdAndDocumentoId(String tenantId, String documentoId);
    
    /**
     * Conta todas as entries por tenant
     */
    Mono<Long> countByTenantId(String tenantId);
    
    /**
     * Conta todas as entries (sem filtrar por tenant) - para SUPER_ADMIN
     */
    Mono<Long> countAll();
    
    /**
     * Agrega valores totais por ano para um tenant específico
     */
    Flux<TotalPorAno> getTotalPorAno(String tenantId);
    
    /**
     * Agrega valores totais por ano para todos os tenants (SUPER_ADMIN)
     */
    Flux<TotalPorAno> getTotalPorAnoAll();
    
    /**
     * Agrega valores totais por mês para um tenant específico
     */
    Flux<TotalPorMes> getTotalPorMes(String tenantId);
    
    /**
     * Agrega valores totais por mês para todos os tenants (SUPER_ADMIN)
     */
    Flux<TotalPorMes> getTotalPorMesAll();
    
    Mono<Void> deleteByTenantIdAndDocumentoId(String tenantId, String documentoId);
    
    // Métodos legados (manter para compatibilidade)
    @Deprecated
    Mono<PayrollEntry> findById(String id);
    
    @Deprecated
    Flux<PayrollEntry> findByDocumentoId(String documentoId);
    
    @Deprecated
    Flux<PayrollEntry> findAll();
    
    @Deprecated
    Mono<Long> countByDocumentoId(String documentoId);
    
    @Deprecated
    Mono<Void> deleteByDocumentoId(String documentoId);
}

