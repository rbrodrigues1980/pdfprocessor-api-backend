package br.com.verticelabs.pdfprocessor.interfaces.entries;

import br.com.verticelabs.pdfprocessor.application.entries.EntryQueryUseCase;
import br.com.verticelabs.pdfprocessor.interfaces.entries.dto.EntryResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/entries")
@RequiredArgsConstructor
public class EntryController {

    private final EntryQueryUseCase entryQueryUseCase;
    private final EntryMapper entryMapper;

    /**
     * GET /api/v1/entries
     * Endpoint global de entries com filtros.
     */
    @GetMapping
    public Mono<ResponseEntity<List<EntryResponse>>> getEntries(
            @RequestParam(required = false) String cpf,
            @RequestParam(required = false) String rubrica,
            @RequestParam(required = false) Integer ano,
            @RequestParam(required = false) Integer mes,
            @RequestParam(required = false) String origem,
            @RequestParam(required = false) String documentoId,
            @RequestParam(required = false) Double minValor,
            @RequestParam(required = false) Double maxValor) {
        log.debug("GET /entries com filtros - cpf: {}, rubrica: {}, ano: {}, mes: {}, origem: {}, documentoId: {}, minValor: {}, maxValor: {}", 
                cpf, rubrica, ano, mes, origem, documentoId, minValor, maxValor);
        
        return entryQueryUseCase.findWithFilters(
                        cpf, rubrica, ano, mes, origem, documentoId, minValor, maxValor)
                .map(entryMapper::toResponse)
                .collectList()
                .map(entries -> {
                    if (entries.isEmpty()) {
                        return ResponseEntity.status(HttpStatus.NO_CONTENT).body(entries);
                    }
                    return ResponseEntity.ok(entries);
                })
                .onErrorResume(IllegalArgumentException.class, e ->
                        Mono.just(ResponseEntity.status(HttpStatus.NOT_FOUND)
                                .body((List<EntryResponse>) null))
                )
                .onErrorResume(Exception.class, e -> {
                    log.error("Erro ao buscar entries com filtros", e);
                    return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                            .body((List<EntryResponse>) null));
                });
    }
}
