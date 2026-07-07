package br.com.verticelabs.pdfprocessor.infrastructure.security;

import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.regex.Pattern;

/**
 * Autorização por ação para o perfil EVALUATOR (avaliador).
 *
 * <p>Modelo deny-by-default: quando o usuário autenticado tem a role EVALUATOR,
 * apenas um conjunto explícito de endpoints (leitura de clientes/documentos,
 * upload de declaração de IR e exportação de Excel/Resumo Geral) é permitido.
 * Qualquer outra combinação método+path retorna 403 — bloqueando criar/editar/
 * desativar/excluir/validar cliente, subir contracheque, excluir documento e a
 * gestão de usuários/tenants/empresas/rubricas.</p>
 *
 * <p>O isolamento por cliente (allowlist) é aplicado nos use cases via
 * {@link br.com.verticelabs.pdfprocessor.application.security.EvaluatorAccessService}.</p>
 */
@Slf4j
@Component
@Order(0)
public class EvaluatorAuthorizationFilter implements WebFilter {

    private static final String EVALUATOR_AUTHORITY = "ROLE_EVALUATOR";

    /** Regras (método + regex de path) permitidas ao avaliador. Prefixo /api/v1. */
    private static final List<AllowedRule> ALLOWED = List.of(
            // Leitura de clientes
            new AllowedRule(HttpMethod.GET, "^/api/v1/persons/?$"),
            new AllowedRule(HttpMethod.GET, "^/api/v1/persons/[^/]+$"),
            // Leitura de documentos/lançamentos/rubricas + exportações por cliente
            new AllowedRule(HttpMethod.GET,
                    "^/api/v1/persons/[^/]+/(documents|documents-by-id|entries|rubricas|"
                            + "resumo-geral|resumo-geral/pdf|excel|excel-by-id|excel-by-tenant)/?$"),
            // Upload de declaração de IR (por cliente)
            new AllowedRule(HttpMethod.POST, "^/api/v1/persons/[^/]+/income-tax/(upload|bulk-upload)/?$"),
            // Leitura de documentos por id
            new AllowedRule(HttpMethod.GET,
                    "^/api/v1/documents/[^/]+(/(entries|entries/paged|pages|summary|processing-status|irpf-data))?/?$")
    );

    @Override
    @org.springframework.lang.NonNull
    public Mono<Void> filter(@org.springframework.lang.NonNull ServerWebExchange exchange,
            @org.springframework.lang.NonNull WebFilterChain chain) {
        String path = exchange.getRequest().getPath().value();
        HttpMethod method = exchange.getRequest().getMethod();

        // Preflight CORS e rotas públicas não são restringidas aqui
        if (HttpMethod.OPTIONS.equals(method) || isPublicPath(path)) {
            return chain.filter(exchange);
        }

        return ReactiveSecurityContextHolder.getContext()
                .map(ctx -> ctx.getAuthentication())
                .cast(Authentication.class)
                .flatMap(auth -> {
                    boolean isEvaluator = auth.getAuthorities().stream()
                            .anyMatch(a -> EVALUATOR_AUTHORITY.equals(a.getAuthority()));
                    if (!isEvaluator) {
                        return chain.filter(exchange);
                    }
                    if (isAllowedForEvaluator(method, path)) {
                        return chain.filter(exchange);
                    }
                    log.warn("🚫 EVALUATOR bloqueado: {} {}", method, path);
                    exchange.getResponse().setStatusCode(HttpStatus.FORBIDDEN);
                    return exchange.getResponse().setComplete();
                })
                .switchIfEmpty(chain.filter(exchange)); // sem autenticação: Spring Security trata
    }

    private boolean isAllowedForEvaluator(HttpMethod method, String path) {
        for (AllowedRule rule : ALLOWED) {
            if (rule.method.equals(method) && rule.pattern.matcher(path).matches()) {
                return true;
            }
        }
        return false;
    }

    private boolean isPublicPath(String path) {
        return path.startsWith("/v3/api-docs")
                || path.startsWith("/swagger-ui")
                || path.startsWith("/webjars")
                || path.equals("/favicon.ico")
                || path.equals("/error")
                || path.startsWith("/actuator/health")
                || path.startsWith("/actuator/info")
                || path.startsWith("/api/v1/system")
                || path.startsWith("/api/v1/auth");
    }

    private static final class AllowedRule {
        private final HttpMethod method;
        private final Pattern pattern;

        private AllowedRule(HttpMethod method, String regex) {
            this.method = method;
            this.pattern = Pattern.compile(regex);
        }
    }
}
