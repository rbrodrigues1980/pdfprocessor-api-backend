package br.com.verticelabs.pdfprocessor.infrastructure.mongodb;

import br.com.verticelabs.pdfprocessor.domain.model.Rubrica;
import br.com.verticelabs.pdfprocessor.domain.repository.RubricaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Component
@RequiredArgsConstructor
public class MongoRubricaRepositoryAdapter implements RubricaRepository {

    private final SpringDataRubricaRepository repository;

    @Override
    public Mono<Rubrica> save(Rubrica rubrica) {
        return repository.save(rubrica);
    }

    @Override
    public Mono<Rubrica> findByCodigo(String codigo, String tenantId) {
        if ("GLOBAL".equals(tenantId)) {
            return repository.findByCodigoAndTenantId(codigo, "GLOBAL");
        }
        // Buscar primeiro global, depois do tenant
        return repository.findByCodigoAndTenantId(codigo, "GLOBAL")
                .switchIfEmpty(repository.findByCodigoAndTenantId(codigo, tenantId));
    }

    @Override
    public Flux<Rubrica> findAll(String tenantId) {
        // Buscar rubricas globais + do tenant
        return repository.findAllByTenantId("GLOBAL")
                .concatWith(repository.findAllByTenantId(tenantId))
                .distinct();
    }

    @Override
    public Flux<Rubrica> findAllByAtivoTrue(String tenantId) {
        // Buscar rubricas ativas globais + do tenant
        return repository.findAllByTenantIdAndAtivoTrue("GLOBAL")
                .concatWith(repository.findAllByTenantIdAndAtivoTrue(tenantId))
                .distinct();
    }

    @Override
    public Mono<Long> countByAtivoTrue(String tenantId) {
        // Contar rubricas ativas globais + do tenant
        return repository.countByTenantIdAndAtivoTrue("GLOBAL")
                .flatMap(countGlobal -> 
                    repository.countByTenantIdAndAtivoTrue(tenantId)
                        .map(countTenant -> countGlobal + countTenant));
    }

    @Override
    public Mono<Long> countAllAtivoTrue() {
        return repository.countByAtivoTrue();
    }

    @Override
    public Mono<Boolean> existsByCodigo(String codigo, String tenantId) {
        if ("GLOBAL".equals(tenantId)) {
            return repository.existsByCodigoAndTenantId(codigo, "GLOBAL");
        }
        // Verificar se existe global ou no tenant
        return repository.existsByCodigoAndTenantId(codigo, "GLOBAL")
                .flatMap(existsGlobal -> existsGlobal ? 
                    Mono.just(true) : 
                    repository.existsByCodigoAndTenantId(codigo, tenantId));
    }

    @Override
    public Mono<Void> deleteByCodigo(String codigo, String tenantId) {
        return repository.findByCodigoAndTenantId(codigo, tenantId)
                .flatMap(repository::delete)
                .then();
    }

    // Métodos legados
    @Override
    @Deprecated
    public Mono<Rubrica> findByCodigo(String codigo) {
        return repository.findByCodigo(codigo);
    }

    @Override
    @Deprecated
    public Flux<Rubrica> findAll() {
        return repository.findAll();
    }

    @Override
    @Deprecated
    public Flux<Rubrica> findAllByAtivoTrue() {
        return repository.findAllByAtivoTrue();
    }

    @Override
    @Deprecated
    public Mono<Boolean> existsByCodigo(String codigo) {
        return repository.existsByCodigo(codigo);
    }

    @Override
    @Deprecated
    public Mono<Void> deleteByCodigo(String codigo) {
        return repository.findByCodigo(codigo)
                .flatMap(repository::delete)
                .then();
    }
}
