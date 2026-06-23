package br.com.verticelabs.pdfprocessor.interfaces.empresas;

import br.com.verticelabs.pdfprocessor.application.empresas.EmpresaUseCase;
import br.com.verticelabs.pdfprocessor.domain.exceptions.EmpresaDuplicadaException;
import br.com.verticelabs.pdfprocessor.domain.exceptions.EmpresaEmUsoException;
import br.com.verticelabs.pdfprocessor.domain.exceptions.EmpresaNotFoundException;
import br.com.verticelabs.pdfprocessor.interfaces.empresas.dto.CreateEmpresaRequest;
import br.com.verticelabs.pdfprocessor.interfaces.empresas.dto.EmpresaResponse;
import br.com.verticelabs.pdfprocessor.interfaces.empresas.dto.UpdateEmpresaRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/empresas")
@RequiredArgsConstructor
public class EmpresaController {

    private final EmpresaUseCase empresaUseCase;
    private final EmpresaMapper empresaMapper;

    @PostMapping
    public Mono<ResponseEntity<Object>> criar(@Valid @RequestBody CreateEmpresaRequest request) {
        return empresaUseCase.criar(request)
                .map(empresa -> ResponseEntity.status(HttpStatus.CREATED).body((Object) empresaMapper.toResponse(empresa)))
                .onErrorResume(EmpresaDuplicadaException.class, e ->
                        Mono.just(ResponseEntity.status(HttpStatus.CONFLICT)
                                .body((Object) new ErrorResponse(HttpStatus.CONFLICT.value(), e.getMessage()))));
    }

    @GetMapping
    public Flux<EmpresaResponse> listar(
            @RequestParam(required = false) String nome,
            @RequestParam(required = false) String sigla,
            @RequestParam(required = false, defaultValue = "false") boolean apenasAtivas) {
        return empresaUseCase.listar(nome, sigla, apenasAtivas)
                .map(empresaMapper::toResponse);
    }

    @GetMapping("/{id}")
    public Mono<ResponseEntity<Object>> buscarPorId(@PathVariable String id) {
        return empresaUseCase.buscarPorId(id)
                .map(empresa -> ResponseEntity.ok((Object) empresaMapper.toResponse(empresa)))
                .onErrorResume(EmpresaNotFoundException.class, e ->
                        Mono.just(ResponseEntity.status(HttpStatus.NOT_FOUND)
                                .body((Object) new ErrorResponse(HttpStatus.NOT_FOUND.value(), e.getMessage()))));
    }

    @PutMapping("/{id}")
    public Mono<ResponseEntity<Object>> atualizar(
            @PathVariable String id,
            @Valid @RequestBody UpdateEmpresaRequest request) {
        return empresaUseCase.atualizar(id, request)
                .map(empresa -> ResponseEntity.ok((Object) empresaMapper.toResponse(empresa)))
                .onErrorResume(EmpresaNotFoundException.class, e ->
                        Mono.just(ResponseEntity.status(HttpStatus.NOT_FOUND)
                                .body((Object) new ErrorResponse(HttpStatus.NOT_FOUND.value(), e.getMessage()))))
                .onErrorResume(EmpresaDuplicadaException.class, e ->
                        Mono.just(ResponseEntity.status(HttpStatus.CONFLICT)
                                .body((Object) new ErrorResponse(HttpStatus.CONFLICT.value(), e.getMessage()))));
    }

    @PatchMapping("/{id}/activate")
    public Mono<ResponseEntity<Object>> ativar(@PathVariable String id) {
        return empresaUseCase.ativar(id)
                .then(Mono.just(ResponseEntity.ok().<Object>build()))
                .onErrorResume(EmpresaNotFoundException.class, e ->
                        Mono.just(ResponseEntity.status(HttpStatus.NOT_FOUND)
                                .body((Object) new ErrorResponse(HttpStatus.NOT_FOUND.value(), e.getMessage()))));
    }

    @PatchMapping("/{id}/deactivate")
    public Mono<ResponseEntity<Object>> desativar(@PathVariable String id) {
        return empresaUseCase.desativar(id)
                .then(Mono.just(ResponseEntity.ok().<Object>build()))
                .onErrorResume(EmpresaNotFoundException.class, e ->
                        Mono.just(ResponseEntity.status(HttpStatus.NOT_FOUND)
                                .body((Object) new ErrorResponse(HttpStatus.NOT_FOUND.value(), e.getMessage()))));
    }

    @DeleteMapping("/{id}")
    public Mono<ResponseEntity<Object>> excluir(@PathVariable String id) {
        return empresaUseCase.excluir(id)
                .then(Mono.just(ResponseEntity.noContent().<Object>build()))
                .onErrorResume(EmpresaNotFoundException.class, e ->
                        Mono.just(ResponseEntity.status(HttpStatus.NOT_FOUND)
                                .body((Object) new ErrorResponse(HttpStatus.NOT_FOUND.value(), e.getMessage()))))
                .onErrorResume(EmpresaEmUsoException.class, e ->
                        Mono.just(ResponseEntity.status(HttpStatus.CONFLICT)
                                .body((Object) new ErrorResponse(HttpStatus.CONFLICT.value(), e.getMessage()))));
    }

    private record ErrorResponse(int status, String message) {}
}
