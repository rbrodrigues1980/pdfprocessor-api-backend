package br.com.verticelabs.pdfprocessor.domain.repository;

import br.com.verticelabs.pdfprocessor.domain.model.Empresa;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface EmpresaRepository {
    Mono<Empresa> save(Empresa empresa);

    Mono<Empresa> findById(String id);

    Mono<Empresa> findByTenantIdAndId(String tenantId, String id);

    Mono<Boolean> existsByTenantIdAndSigla(String tenantId, String sigla);

    Mono<Boolean> existsByTenantIdAndSiglaAndIdNot(String tenantId, String sigla, String id);

    Flux<Empresa> findAllByTenantId(String tenantId);

    Flux<Empresa> findAllByTenantIdAndAtivoTrue(String tenantId);

    Mono<Empresa> findByTenantIdAndSiglaIgnoreCase(String tenantId, String sigla);

    Mono<Empresa> findByTenantIdAndNomeIgnoreCase(String tenantId, String nome);

    Flux<Empresa> findAllById(Iterable<String> ids);

    Mono<Void> deleteById(String id);
}
