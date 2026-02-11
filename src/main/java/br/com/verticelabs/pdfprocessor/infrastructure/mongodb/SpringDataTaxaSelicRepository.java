package br.com.verticelabs.pdfprocessor.infrastructure.mongodb;

import br.com.verticelabs.pdfprocessor.domain.model.TaxaSelic;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;

@Repository
public interface SpringDataTaxaSelicRepository extends ReactiveMongoRepository<TaxaSelic, String> {

    Mono<TaxaSelic> findByNumeroReuniaoCopom(Integer numeroReuniaoCopom);

    Mono<TaxaSelic> findByDataFimVigenciaIsNull();

    Flux<TaxaSelic> findAllByOrderByNumeroReuniaoCopomDesc();

    Flux<TaxaSelic> findByDataInicioVigenciaBetweenOrderByDataInicioVigenciaDesc(Instant inicio, Instant fim);

    Mono<Boolean> existsByNumeroReuniaoCopom(Integer numeroReuniaoCopom);
}
