package br.com.verticelabs.pdfprocessor.infrastructure.mongodb;

import br.com.verticelabs.pdfprocessor.domain.model.Person;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Repository
public interface SpringDataPersonRepository extends ReactiveMongoRepository<Person, String> {
    Mono<Person> findByTenantIdAndCpf(String tenantId, String cpf);
    
    Mono<Boolean> existsByTenantIdAndCpf(String tenantId, String cpf);
    
    Flux<Person> findAllByTenantId(String tenantId);
    
    Mono<Long> countByTenantId(String tenantId);
    
    Mono<Person> findByTenantIdAndId(String tenantId, String id);
    
    // Métodos legados
    @Deprecated
    Mono<Person> findByCpf(String cpf);
    
    @Deprecated
    Mono<Boolean> existsByCpf(String cpf);
}

