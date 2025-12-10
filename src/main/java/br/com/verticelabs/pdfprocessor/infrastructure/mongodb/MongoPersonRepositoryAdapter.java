package br.com.verticelabs.pdfprocessor.infrastructure.mongodb;

import br.com.verticelabs.pdfprocessor.domain.model.Person;
import br.com.verticelabs.pdfprocessor.domain.repository.PersonRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Component
@RequiredArgsConstructor
public class MongoPersonRepositoryAdapter implements PersonRepository {

    private final SpringDataPersonRepository repository;

    @Override
    public Mono<Person> findById(String id) {
        return repository.findById(id);
    }

    @Override
    public Mono<Person> findByTenantIdAndCpf(String tenantId, String cpf) {
        return repository.findByTenantIdAndCpf(tenantId, cpf);
    }

    @Override
    public Mono<Person> save(Person person) {
        return repository.save(person);
    }

    @Override
    public Mono<Boolean> existsByTenantIdAndCpf(String tenantId, String cpf) {
        return repository.existsByTenantIdAndCpf(tenantId, cpf);
    }

    @Override
    public Flux<Person> findAllByTenantId(String tenantId) {
        return repository.findAllByTenantId(tenantId);
    }

    @Override
    public Mono<Long> countByTenantId(String tenantId) {
        return repository.countByTenantId(tenantId);
    }

    @Override
    public Mono<Long> countAll() {
        return repository.count();
    }

    @Override
    public Mono<Person> findByTenantIdAndId(String tenantId, String id) {
        return repository.findByTenantIdAndId(tenantId, id);
    }

    @Override
    public Mono<Void> deleteById(String id) {
        return repository.deleteById(id).then();
    }

    // Métodos legados
    @Override
    @Deprecated
    public Mono<Person> findByCpf(String cpf) {
        return repository.findByCpf(cpf);
    }

    @Override
    @Deprecated
    public Mono<Boolean> existsByCpf(String cpf) {
        return repository.existsByCpf(cpf);
    }
}

