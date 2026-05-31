package br.com.verticelabs.pdfprocessor.domain.repository;

import br.com.verticelabs.pdfprocessor.domain.model.Rubrica;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface RubricaRepository {
    Mono<Rubrica> save(Rubrica rubrica);

    Mono<Rubrica> findByCodigo(String codigo);

    Flux<Rubrica> findAll();

    Flux<Rubrica> findAllByAtivoTrue();

    Mono<Long> countAllAtivoTrue();

    Mono<Boolean> existsByCodigo(String codigo);

    Mono<Void> deleteByCodigo(String codigo);
}
