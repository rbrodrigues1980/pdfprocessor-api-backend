package br.com.verticelabs.pdfprocessor.domain.repository;

import br.com.verticelabs.pdfprocessor.domain.model.Rubrica;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface RubricaRepository {
    Mono<Rubrica> save(Rubrica rubrica);

    // Busca rubrica global ou do tenant específico
    Mono<Rubrica> findByCodigo(String codigo, String tenantId);

    // Busca todas rubricas (globais + do tenant)
    Flux<Rubrica> findAll(String tenantId);

    // Busca rubricas ativas (globais + do tenant)
    Flux<Rubrica> findAllByAtivoTrue(String tenantId);
    
    /**
     * Conta rubricas ativas (globais + do tenant)
     */
    Mono<Long> countByAtivoTrue(String tenantId);
    
    /**
     * Conta todas as rubricas ativas (sem filtrar por tenant) - para SUPER_ADMIN
     */
    Mono<Long> countAllAtivoTrue();

    Mono<Boolean> existsByCodigo(String codigo, String tenantId);

    Mono<Void> deleteByCodigo(String codigo, String tenantId);
    
    // Métodos legados (manter para compatibilidade)
    @Deprecated
    Mono<Rubrica> findByCodigo(String codigo);

    @Deprecated
    Flux<Rubrica> findAll();

    @Deprecated
    Flux<Rubrica> findAllByAtivoTrue();

    @Deprecated
    Mono<Boolean> existsByCodigo(String codigo);

    @Deprecated
    Mono<Void> deleteByCodigo(String codigo);
}
