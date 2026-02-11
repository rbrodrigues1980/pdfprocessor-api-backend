package br.com.verticelabs.pdfprocessor.interfaces.consolidation;

import br.com.verticelabs.pdfprocessor.application.consolidation.ConsolidationUseCase;
import br.com.verticelabs.pdfprocessor.domain.exceptions.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

@Slf4j
@RestController
@RequestMapping("/persons")
@RequiredArgsConstructor
public class ConsolidationController {

    private final ConsolidationUseCase consolidationUseCase;

    /**
     * GET /api/v1/persons/{cpf}/consolidated
     * Retorna a consolidação de todas as rubricas de uma pessoa em formato matricial.
     * 
     * Query params opcionais:
     * - ano: consolida apenas 1 ano (ex: "2017")
     * - origem: filtra CAIXA/FUNCEF
     */
    @GetMapping("/{cpf}/consolidated")
    public Mono<ResponseEntity<Object>> getConsolidated(
            @PathVariable String cpf,
            @RequestParam(required = false) String ano,
            @RequestParam(required = false) String origem) {
        
        log.info("=== INÍCIO: GET /api/v1/persons/{}/consolidated ===", cpf);
        log.info("Parâmetros recebidos - CPF: {}, Ano: {}, Origem: {}", cpf, ano, origem);

        return consolidationUseCase.consolidate(cpf, ano, origem)
                .<ResponseEntity<Object>>map(response -> {
                    if (response.getRubricas() == null || response.getRubricas().isEmpty()) {
                        log.warn("Consolidação retornou vazia para CPF: {}", cpf);
                        return ResponseEntity.status(HttpStatus.NO_CONTENT).body((Object) response);
                    }
                    log.info("=== SUCESSO: Consolidação concluída para CPF: {} ===", cpf);
                    log.info("Total de rubricas consolidadas: {}", response.getRubricas().size());
                    log.info("Anos encontrados: {}", response.getAnos());
                    log.info("Total geral: R$ {}", response.getTotalGeral());
                    return ResponseEntity.ok((Object) response);
                })
                .onErrorResume(PersonNotFoundException.class, e -> {
                    log.warn("=== ERRO: Pessoa não encontrada ===");
                    log.warn("CPF: {}", cpf);
                    log.warn("Status: 404 NOT_FOUND");
                    return Mono.just(ResponseEntity.status(HttpStatus.NOT_FOUND)
                            .body((Object) new ErrorResponse(HttpStatus.NOT_FOUND.value(), e.getMessage())));
                })
                .onErrorResume(NoEntriesFoundException.class, e -> {
                    log.warn("=== AVISO: Nenhuma entrada encontrada ===");
                    log.warn("CPF: {}", cpf);
                    log.warn("Filtros aplicados - Ano: {}, Origem: {}", ano, origem);
                    log.warn("Status: 204 NO_CONTENT");
                    return Mono.just(ResponseEntity.status(HttpStatus.NO_CONTENT)
                            .body((Object) new ErrorResponse(HttpStatus.NO_CONTENT.value(), e.getMessage())));
                })
                .onErrorResume(InvalidYearException.class, e -> {
                    log.warn("=== ERRO: Ano inválido ===");
                    log.warn("Ano fornecido: {}", ano);
                    log.warn("Status: 400 BAD_REQUEST");
                    return Mono.just(ResponseEntity.status(HttpStatus.BAD_REQUEST)
                            .body((Object) new ErrorResponse(HttpStatus.BAD_REQUEST.value(), e.getMessage())));
                })
                .onErrorResume(InvalidOriginException.class, e -> {
                    log.warn("=== ERRO: Origem inválida ===");
                    log.warn("Origem fornecida: {}", origem);
                    log.warn("Status: 400 BAD_REQUEST");
                    return Mono.just(ResponseEntity.status(HttpStatus.BAD_REQUEST)
                            .body((Object) new ErrorResponse(HttpStatus.BAD_REQUEST.value(), e.getMessage())));
                })
                .onErrorResume(Exception.class, e -> {
                    log.error("=== ERRO CRÍTICO: Falha ao consolidar dados ===", e);
                    log.error("CPF: {}", cpf);
                    log.error("Ano: {}, Origem: {}", ano, origem);
                    log.error("Mensagem de erro: {}", e.getMessage());
                    log.error("Status: 500 INTERNAL_SERVER_ERROR");
                    return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                            .body((Object) new ErrorResponse(HttpStatus.INTERNAL_SERVER_ERROR.value(), 
                                    "Erro interno ao processar consolidação")));
                });
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

