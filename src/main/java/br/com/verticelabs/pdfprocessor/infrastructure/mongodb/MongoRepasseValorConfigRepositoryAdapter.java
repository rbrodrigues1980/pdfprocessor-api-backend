package br.com.verticelabs.pdfprocessor.infrastructure.mongodb;

import br.com.verticelabs.pdfprocessor.domain.model.RepasseValorConfig;
import br.com.verticelabs.pdfprocessor.domain.repository.RepasseValorConfigRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.time.Instant;

@Component
@RequiredArgsConstructor
public class MongoRepasseValorConfigRepositoryAdapter implements RepasseValorConfigRepository {

    private final SpringDataRepasseValorConfigRepository repository;

    @Override
    public Mono<RepasseValorConfig> save(RepasseValorConfig config) {
        return repository.save(config);
    }

    @Override
    public Mono<RepasseValorConfig> findEffectiveAt(Instant instant) {
        return repository.findFirstByVigenciaDeLessThanEqualOrderByVigenciaDeDesc(instant);
    }

    @Override
    public Mono<RepasseValorConfig> findLatest() {
        return repository.findFirstByOrderByVigenciaDeDesc();
    }

    @Override
    public Mono<Boolean> existsByVigenciaDe(Instant vigenciaDe) {
        return repository.existsByVigenciaDe(vigenciaDe);
    }
}
