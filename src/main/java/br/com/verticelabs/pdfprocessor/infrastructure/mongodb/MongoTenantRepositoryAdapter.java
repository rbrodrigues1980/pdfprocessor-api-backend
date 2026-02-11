package br.com.verticelabs.pdfprocessor.infrastructure.mongodb;

import br.com.verticelabs.pdfprocessor.domain.model.Tenant;
import br.com.verticelabs.pdfprocessor.domain.repository.TenantRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Component
@RequiredArgsConstructor
public class MongoTenantRepositoryAdapter implements TenantRepository {

    private final SpringDataTenantRepository repository;

    @Override
    public Mono<Tenant> findById(String id) {
        return repository.findById(id);
    }

    @Override
    public Mono<Tenant> findByNome(String nome) {
        return repository.findByNome(nome);
    }

    @Override
    public Mono<Tenant> save(Tenant tenant) {
        return repository.save(tenant);
    }

    @Override
    public Flux<Tenant> findAll() {
        return repository.findAll();
    }

    @Override
    public Mono<Boolean> existsById(String id) {
        return repository.existsById(id);
    }
}

