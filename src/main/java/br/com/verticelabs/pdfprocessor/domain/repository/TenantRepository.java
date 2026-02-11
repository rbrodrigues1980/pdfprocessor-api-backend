package br.com.verticelabs.pdfprocessor.domain.repository;

import br.com.verticelabs.pdfprocessor.domain.model.Tenant;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface TenantRepository {
    Mono<Tenant> findById(String id);
    
    Mono<Tenant> findByNome(String nome);
    
    Mono<Tenant> save(Tenant tenant);
    
    Flux<Tenant> findAll();
    
    Mono<Boolean> existsById(String id);
}

