package br.com.verticelabs.pdfprocessor.infrastructure.mongodb;

import br.com.verticelabs.pdfprocessor.domain.model.Rubrica;
import br.com.verticelabs.pdfprocessor.domain.repository.RubricaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Component
@RequiredArgsConstructor
public class MongoRubricaRepositoryAdapter implements RubricaRepository {

    private final SpringDataRubricaRepository repository;

    @Override
    public Mono<Rubrica> save(Rubrica rubrica) {
        return repository.save(rubrica);
    }

    @Override
    public Mono<Rubrica> findByCodigo(String codigo) {
        return repository.findByCodigo(codigo);
    }

    @Override
    public Flux<Rubrica> findAll() {
        return repository.findAll();
    }

    @Override
    public Flux<Rubrica> findAllByAtivoTrue() {
        return repository.findAllByAtivoTrue();
    }

    @Override
    public Mono<Long> countAllAtivoTrue() {
        return repository.countByAtivoTrue();
    }

    @Override
    public Mono<Boolean> existsByCodigo(String codigo) {
        return repository.existsByCodigo(codigo);
    }

    @Override
    public Mono<Void> deleteByCodigo(String codigo) {
        return repository.findByCodigo(codigo)
                .flatMap(repository::delete)
                .then();
    }
}
