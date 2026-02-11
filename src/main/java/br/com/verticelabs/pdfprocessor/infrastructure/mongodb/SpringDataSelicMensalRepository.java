package br.com.verticelabs.pdfprocessor.infrastructure.mongodb;

import br.com.verticelabs.pdfprocessor.domain.model.SelicMensalEntity;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Repository Spring Data para SELIC mensal.
 */
public interface SpringDataSelicMensalRepository extends ReactiveMongoRepository<SelicMensalEntity, String> {

    Mono<SelicMensalEntity> findByAnoAndMes(Integer ano, Integer mes);

    Flux<SelicMensalEntity> findByAnoGreaterThanEqualAndAnoLessThanEqualOrderByAnoAscMesAsc(
            Integer anoInicio, Integer anoFim);

    Flux<SelicMensalEntity> findAllByOrderByAnoDescMesDesc();
}
