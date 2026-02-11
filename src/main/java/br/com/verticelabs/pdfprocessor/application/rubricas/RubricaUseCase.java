package br.com.verticelabs.pdfprocessor.application.rubricas;

import br.com.verticelabs.pdfprocessor.domain.exceptions.RubricaDuplicadaException;
import br.com.verticelabs.pdfprocessor.domain.exceptions.RubricaNotFoundException;
import br.com.verticelabs.pdfprocessor.domain.model.Rubrica;
import br.com.verticelabs.pdfprocessor.domain.repository.RubricaRepository;
import br.com.verticelabs.pdfprocessor.interfaces.rubricas.dto.CreateRubricaRequest;
import br.com.verticelabs.pdfprocessor.interfaces.rubricas.dto.UpdateRubricaRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
public class RubricaUseCase {

    private final RubricaRepository rubricaRepository;

    public Mono<Rubrica> criar(CreateRubricaRequest request) {
        return rubricaRepository.existsByCodigo(request.getCodigo())
                .flatMap(exists -> {
                    if (Boolean.TRUE.equals(exists)) {
                        return Mono.error(new RubricaDuplicadaException(request.getCodigo()));
                    }
                    Rubrica rubrica = Rubrica.builder()
                            .codigo(request.getCodigo())
                            .descricao(request.getDescricao())
                            .categoria(request.getCategoria())
                            .ativo(true)
                            .build();
                    return rubricaRepository.save(rubrica);
                });
    }

    public Flux<Rubrica> listarTodas() {
        return rubricaRepository.findAll();
    }

    public Flux<Rubrica> listarAtivas() {
        return rubricaRepository.findAllByAtivoTrue();
    }

    public Mono<Rubrica> buscarPorCodigo(String codigo) {
        return rubricaRepository.findByCodigo(codigo)
                .switchIfEmpty(Mono.error(new RubricaNotFoundException(codigo)));
    }

    public Mono<Rubrica> atualizar(String codigo, UpdateRubricaRequest request) {
        return rubricaRepository.findByCodigo(codigo)
                .switchIfEmpty(Mono.error(new RubricaNotFoundException(codigo)))
                .flatMap(rubrica -> {
                    rubrica.setDescricao(request.getDescricao());
                    if (request.getCategoria() != null) {
                        rubrica.setCategoria(request.getCategoria());
                    }
                    return rubricaRepository.save(rubrica);
                });
    }

    public Mono<Void> desativar(String codigo) {
        return rubricaRepository.findByCodigo(codigo)
                .switchIfEmpty(Mono.error(new RubricaNotFoundException(codigo)))
                .flatMap(rubrica -> {
                    rubrica.setAtivo(false);
                    return rubricaRepository.save(rubrica);
                })
                .then();
    }
}

