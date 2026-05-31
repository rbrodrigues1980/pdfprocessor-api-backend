package br.com.verticelabs.pdfprocessor.infrastructure.mongodb;

import br.com.verticelabs.pdfprocessor.domain.model.Rubrica;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Repository
public interface SpringDataRubricaRepository extends ReactiveMongoRepository<Rubrica, String> {
    Mono<Rubrica> findByCodigo(String codigo);

    Flux<Rubrica> findAllByAtivoTrue();

    Mono<Long> countByAtivoTrue();

    Mono<Boolean> existsByCodigo(String codigo);
}
