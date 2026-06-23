package br.com.verticelabs.pdfprocessor.infrastructure.mongodb;

import br.com.verticelabs.pdfprocessor.domain.model.DeveloperRepasse;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

@Repository
public interface SpringDataDeveloperRepasseRepository extends ReactiveMongoRepository<DeveloperRepasse, String> {

    Mono<DeveloperRepasse> findByPersonId(String personId);

    Mono<Boolean> existsByPersonId(String personId);
}
