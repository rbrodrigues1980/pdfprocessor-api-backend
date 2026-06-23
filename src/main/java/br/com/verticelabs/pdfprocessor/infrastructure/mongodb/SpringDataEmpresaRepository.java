package br.com.verticelabs.pdfprocessor.infrastructure.mongodb;

import br.com.verticelabs.pdfprocessor.domain.model.Empresa;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Repository
public interface SpringDataEmpresaRepository extends ReactiveMongoRepository<Empresa, String> {
    Mono<Empresa> findByTenantIdAndId(String tenantId, String id);

    Mono<Boolean> existsByTenantIdAndSigla(String tenantId, String sigla);

    Mono<Boolean> existsByTenantIdAndSiglaAndIdNot(String tenantId, String sigla, String id);

    Flux<Empresa> findAllByTenantId(String tenantId);

    Flux<Empresa> findAllByTenantIdAndAtivoTrue(String tenantId);

    Mono<Empresa> findByTenantIdAndSiglaIgnoreCase(String tenantId, String sigla);

    Mono<Empresa> findByTenantIdAndNomeIgnoreCase(String tenantId, String nome);
}
