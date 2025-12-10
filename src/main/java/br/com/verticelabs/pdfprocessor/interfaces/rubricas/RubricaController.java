package br.com.verticelabs.pdfprocessor.interfaces.rubricas;

import br.com.verticelabs.pdfprocessor.application.rubricas.RubricaUseCase;
import br.com.verticelabs.pdfprocessor.domain.exceptions.RubricaDuplicadaException;
import br.com.verticelabs.pdfprocessor.domain.exceptions.RubricaNotFoundException;
import br.com.verticelabs.pdfprocessor.domain.model.Rubrica;
import br.com.verticelabs.pdfprocessor.interfaces.rubricas.dto.CreateRubricaRequest;
import br.com.verticelabs.pdfprocessor.interfaces.rubricas.dto.UpdateRubricaRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/rubricas")
@RequiredArgsConstructor
public class RubricaController {

    private final RubricaUseCase rubricaUseCase;

    @PostMapping
    public Mono<ResponseEntity<Object>> criar(@Valid @RequestBody CreateRubricaRequest request) {
        return rubricaUseCase.criar(request)
                .<ResponseEntity<Object>>map(rubrica -> ResponseEntity.status(HttpStatus.CREATED).body((Object) rubrica))
                .onErrorResume(RubricaDuplicadaException.class, e ->
                        Mono.just(ResponseEntity.status(HttpStatus.CONFLICT)
                                .body((Object) new ErrorResponse(HttpStatus.CONFLICT.value(), e.getMessage())))
                );
    }

    @GetMapping
    public Flux<Rubrica> listar(@RequestParam(required = false, defaultValue = "false") boolean apenasAtivas) {
        if (apenasAtivas) {
            return rubricaUseCase.listarAtivas();
        }
        return rubricaUseCase.listarTodas();
    }

    @GetMapping("/{codigo}")
    public Mono<ResponseEntity<Object>> buscarPorCodigo(@PathVariable String codigo) {
        return rubricaUseCase.buscarPorCodigo(codigo)
                .<ResponseEntity<Object>>map(rubrica -> ResponseEntity.ok((Object) rubrica))
                .onErrorResume(RubricaNotFoundException.class, e ->
                        Mono.just(ResponseEntity.status(HttpStatus.NOT_FOUND)
                                .body((Object) new ErrorResponse(HttpStatus.NOT_FOUND.value(), e.getMessage())))
                );
    }

    @PutMapping("/{codigo}")
    public Mono<ResponseEntity<Object>> atualizar(
            @PathVariable String codigo,
            @Valid @RequestBody UpdateRubricaRequest request) {
        return rubricaUseCase.atualizar(codigo, request)
                .<ResponseEntity<Object>>map(rubrica -> ResponseEntity.ok((Object) rubrica))
                .onErrorResume(RubricaNotFoundException.class, e ->
                        Mono.just(ResponseEntity.status(HttpStatus.NOT_FOUND)
                                .body((Object) new ErrorResponse(HttpStatus.NOT_FOUND.value(), e.getMessage())))
                );
    }

    @DeleteMapping("/{codigo}")
    public Mono<ResponseEntity<Object>> desativar(@PathVariable String codigo) {
        return rubricaUseCase.desativar(codigo)
                .then(Mono.just(ResponseEntity.ok().<Object>build()))
                .onErrorResume(RubricaNotFoundException.class, e ->
                        Mono.just(ResponseEntity.status(HttpStatus.NOT_FOUND)
                                .body((Object) new ErrorResponse(HttpStatus.NOT_FOUND.value(), e.getMessage())))
                );
    }

    // Classe interna para respostas de erro
    private static class ErrorResponse {
        private final int status;
        private final String error;

        public ErrorResponse(int status, String error) {
            this.status = status;
            this.error = error;
        }

        public int getStatus() {
            return status;
        }

        public String getError() {
            return error;
        }
    }
}

