package br.com.verticelabs.pdfprocessor.infrastructure.mongodb;

import br.com.verticelabs.pdfprocessor.domain.model.RepasseValorConfig;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

import java.time.Instant;

@Repository
public interface SpringDataRepasseValorConfigRepository extends ReactiveMongoRepository<RepasseValorConfig, String> {

    Mono<RepasseValorConfig> findFirstByVigenciaDeLessThanEqualOrderByVigenciaDeDesc(Instant vigenciaDe);

    Mono<RepasseValorConfig> findFirstByOrderByVigenciaDeDesc();

    Mono<Boolean> existsByVigenciaDe(Instant vigenciaDe);
}
