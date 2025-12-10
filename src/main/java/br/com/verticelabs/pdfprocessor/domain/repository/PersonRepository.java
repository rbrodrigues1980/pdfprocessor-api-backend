package br.com.verticelabs.pdfprocessor.domain.repository;

import br.com.verticelabs.pdfprocessor.domain.model.Person;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface PersonRepository {
    Mono<Person> findById(String id);
    
    Mono<Person> findByTenantIdAndCpf(String tenantId, String cpf);
    
    Mono<Person> save(Person person);
    
    Mono<Boolean> existsByTenantIdAndCpf(String tenantId, String cpf);
    
    Flux<Person> findAllByTenantId(String tenantId);
    
    /**
     * Conta pessoas por tenant
     */
    Mono<Long> countByTenantId(String tenantId);
    
    /**
     * Conta todas as pessoas (sem filtrar por tenant) - para SUPER_ADMIN
     */
    Mono<Long> countAll();
    
    /**
     * Busca pessoa por ID e tenantId (para validação de acesso)
     */
    Mono<Person> findByTenantIdAndId(String tenantId, String id);
    
    /**
     * Exclui definitivamente uma pessoa
     */
    Mono<Void> deleteById(String id);
    
    // Métodos legados (manter para compatibilidade)
    @Deprecated
    Mono<Person> findByCpf(String cpf);
    
    @Deprecated
    Mono<Boolean> existsByCpf(String cpf);
}

