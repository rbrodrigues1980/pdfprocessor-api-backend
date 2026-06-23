package br.com.verticelabs.pdfprocessor.infrastructure.mongodb;

import br.com.verticelabs.pdfprocessor.domain.model.Empresa;
import br.com.verticelabs.pdfprocessor.domain.repository.EmpresaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Component
@RequiredArgsConstructor
public class MongoEmpresaRepositoryAdapter implements EmpresaRepository {

    private final SpringDataEmpresaRepository repository;

    @Override
    public Mono<Empresa> save(Empresa empresa) {
        return repository.save(empresa);
    }

    @Override
    public Mono<Empresa> findById(String id) {
        return repository.findById(id);
    }

    @Override
    public Mono<Empresa> findByTenantIdAndId(String tenantId, String id) {
        return repository.findByTenantIdAndId(tenantId, id);
    }

    @Override
    public Mono<Boolean> existsByTenantIdAndSigla(String tenantId, String sigla) {
        return repository.existsByTenantIdAndSigla(tenantId, sigla);
    }

    @Override
    public Mono<Boolean> existsByTenantIdAndSiglaAndIdNot(String tenantId, String sigla, String id) {
        return repository.existsByTenantIdAndSiglaAndIdNot(tenantId, sigla, id);
    }

    @Override
    public Flux<Empresa> findAllByTenantId(String tenantId) {
        return repository.findAllByTenantId(tenantId);
    }

    @Override
    public Flux<Empresa> findAllByTenantIdAndAtivoTrue(String tenantId) {
        return repository.findAllByTenantIdAndAtivoTrue(tenantId);
    }

    @Override
    public Mono<Empresa> findByTenantIdAndSiglaIgnoreCase(String tenantId, String sigla) {
        return repository.findByTenantIdAndSiglaIgnoreCase(tenantId, sigla);
    }

    @Override
    public Mono<Empresa> findByTenantIdAndNomeIgnoreCase(String tenantId, String nome) {
        return repository.findByTenantIdAndNomeIgnoreCase(tenantId, nome);
    }

    @Override
    public Flux<Empresa> findAllById(Iterable<String> ids) {
        return repository.findAllById(ids);
    }

    @Override
    public Mono<Void> deleteById(String id) {
        return repository.deleteById(id).then();
    }
}
