package br.com.verticelabs.pdfprocessor.interfaces.dashboard;

import br.com.verticelabs.pdfprocessor.application.dashboard.DashboardUseCase;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@Slf4j
@RestController
@RequestMapping("/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final DashboardUseCase dashboardUseCase;

    /**
     * GET /api/v1/dashboard
     * Retorna todas as métricas do dashboard para o tenant do usuário autenticado
     */
    @GetMapping
    public Mono<ResponseEntity<Object>> getDashboard() {
        log.info("=== INÍCIO: GET /api/v1/dashboard ===");

        return dashboardUseCase.getDashboardMetrics()
                .<ResponseEntity<Object>>map(ResponseEntity::ok)
                .doOnSuccess(response -> {
                    log.info("=== SUCESSO: Dashboard metrics retornadas ===");
                })
                .onErrorResume(IllegalStateException.class, e -> {
                    String message = e.getMessage();
                    if (message != null && (message.contains("não autenticado") || message.contains("tenantId não encontrado"))) {
                        log.warn("=== ERRO: Usuário não autenticado ou tenantId não encontrado ===");
                        // Retornar erro 401 sem corpo para evitar conflito com Spring Security
                        return Mono.just(ResponseEntity.status(HttpStatus.UNAUTHORIZED).build());
                    }
                    log.error("=== ERRO: Falha ao obter métricas do dashboard ===", e);
                    return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                            .body((Object) new ErrorResponse(500, "Erro ao obter métricas do dashboard: " + message)));
                })
                .onErrorResume(Exception.class, e -> {
                    log.error("=== ERRO: Falha ao obter métricas do dashboard ===", e);
                    return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                            .body((Object) new ErrorResponse(500, "Erro ao obter métricas do dashboard: " + e.getMessage())));
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

