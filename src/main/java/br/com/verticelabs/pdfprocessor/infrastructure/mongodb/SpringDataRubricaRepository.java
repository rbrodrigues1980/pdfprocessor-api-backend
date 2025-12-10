package br.com.verticelabs.pdfprocessor.infrastructure.mongodb;

import br.com.verticelabs.pdfprocessor.domain.model.Rubrica;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Repository
public interface SpringDataRubricaRepository extends ReactiveMongoRepository<Rubrica, String> {
    Mono<Rubrica> findByCodigoAndTenantId(String codigo, String tenantId);
    
    Flux<Rubrica> findAllByTenantId(String tenantId);
    
    Flux<Rubrica> findAllByTenantIdAndAtivoTrue(String tenantId);
    
    Mono<Long> countByTenantIdAndAtivoTrue(String tenantId);
    
    Mono<Long> countByAtivoTrue();
    
    Mono<Boolean> existsByCodigoAndTenantId(String codigo, String tenantId);
    
    // Métodos legados
    @Deprecated
    Mono<Rubrica> findByCodigo(String codigo);

    @Deprecated
    Flux<Rubrica> findAllByAtivoTrue();

    @Deprecated
    Mono<Boolean> existsByCodigo(String codigo);
}
