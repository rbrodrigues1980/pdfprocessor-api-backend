package br.com.verticelabs.pdfprocessor.domain.repository;

import br.com.verticelabs.pdfprocessor.domain.model.RepasseValorConfig;
import reactor.core.publisher.Mono;

import java.time.Instant;

public interface RepasseValorConfigRepository {

    Mono<RepasseValorConfig> save(RepasseValorConfig config);

    Mono<RepasseValorConfig> findEffectiveAt(Instant instant);

    Mono<RepasseValorConfig> findLatest();

    Mono<Boolean> existsByVigenciaDe(Instant vigenciaDe);
}
