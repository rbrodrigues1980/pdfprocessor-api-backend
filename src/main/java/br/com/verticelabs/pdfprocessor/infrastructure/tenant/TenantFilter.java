package br.com.verticelabs.pdfprocessor.infrastructure.tenant;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

/**
 * Filtro que garante isolamento multi-tenant em todas as requisições
 * Extrai o tenantId do JWT ou do header X-Tenant-ID (apenas para SUPER_ADMIN)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TenantFilter implements WebFilter {
    
    private static final String TENANT_ID_HEADER = "X-Tenant-ID";
    private static final String TENANT_ID_KEY = "tenantId";
    
    @Override
    @org.springframework.lang.NonNull
    public Mono<Void> filter(@org.springframework.lang.NonNull ServerWebExchange exchange, @org.springframework.lang.NonNull WebFilterChain chain) {
        // Ignorar rotas públicas
        String path = exchange.getRequest().getPath().value();
        if (isPublicPath(path)) {
            return chain.filter(exchange);
        }
        
        // Verificar se o usuário está autenticado antes de tentar resolver o tenant
        return ReactiveSecurityContextHolder.getContext()
                .flatMap(securityContext -> {
                    // Se não tem autenticação, deixar o Spring Security tratar
                    if (securityContext.getAuthentication() == null || 
                        !securityContext.getAuthentication().isAuthenticated()) {
                        log.debug("⚠️ Usuário não autenticado para path: {} - deixando Spring Security tratar", path);
                        return chain.filter(exchange);
                    }
                    
                    // Usuário autenticado - resolver tenant
                    return resolveTenant(exchange)
                            .flatMap(tenantId -> {
                                log.debug("🔐 Tenant resolvido: {} para path: {}", tenantId, path);
                                return chain.filter(exchange)
                                        .contextWrite(ctx -> ctx.put(TENANT_ID_KEY, tenantId));
                            })
                            .onErrorResume(e -> {
                                log.error("❌ Erro ao resolver tenant: {} - {}", path, e.getMessage());
                                // Não retornar 403 aqui - deixar o Spring Security tratar
                                // O erro será tratado pelo controller ou pelo Spring Security
                                return chain.filter(exchange);
                            });
                })
                .switchIfEmpty(chain.filter(exchange)); // Se não tem SecurityContext, deixar passar
    }
    
    private Mono<String> resolveTenant(ServerWebExchange exchange) {
        // 1. Tentar obter do header X-Tenant-ID (apenas para SUPER_ADMIN)
        String forcedTenant = exchange.getRequest().getHeaders().getFirst(TENANT_ID_HEADER);
        
        return ReactiveSecurityContextHolder.getContext()
                .map(ctx -> ctx.getAuthentication())
                .cast(Authentication.class)
                .flatMap(auth -> {
                    // Verificar se é SUPER_ADMIN
                    boolean isSuperAdmin = auth.getAuthorities().stream()
                            .anyMatch(a -> a.getAuthority().equals("ROLE_SUPER_ADMIN"));
                    
                    // 2. Extrair tenantId do JWT (details do authentication)
                    Object tenantIdObj = auth.getDetails();
                    String tenantIdFromJwt = null;
                    if (tenantIdObj instanceof String && !((String) tenantIdObj).isEmpty()) {
                        tenantIdFromJwt = (String) tenantIdObj;
                    }
                    
                    // Lógica de resolução:
                    // 1. Se SUPER_ADMIN tem header X-Tenant-ID, usar o header (permite mudar de tenant)
                    if (isSuperAdmin && forcedTenant != null && !forcedTenant.isEmpty()) {
                        log.debug("🔑 SUPER_ADMIN usando tenant forçado via header: {}", forcedTenant);
                        return Mono.just(forcedTenant);
                    }
                    
                    // 2. Se tem tenantId no JWT, usar ele
                    if (tenantIdFromJwt != null && !tenantIdFromJwt.isEmpty()) {
                        log.debug("🔐 Usando tenantId do JWT: {}", tenantIdFromJwt);
                        return Mono.just(tenantIdFromJwt);
                    }
                    
                    // 3. Se for SUPER_ADMIN sem tenantId, permitir com "GLOBAL" (pode acessar qualquer tenant)
                    if (isSuperAdmin) {
                        log.debug("🔑 SUPER_ADMIN sem tenantId - permitindo acesso global");
                        return Mono.just("GLOBAL"); // Valor especial para SUPER_ADMIN
                    }
                    
                    // 4. Se não for SUPER_ADMIN e não tem tenantId, usar "GLOBAL" como fallback
                    // (o controller vai validar as permissões)
                    log.warn("⚠️ TenantId não encontrado no JWT para usuário não-SUPER_ADMIN - usando GLOBAL");
                    return Mono.just("GLOBAL");
                });
    }
    
    private boolean isPublicPath(String path) {
        return path.startsWith("/v3/api-docs") ||
               path.startsWith("/swagger-ui") ||
               path.startsWith("/webjars") ||
               path.equals("/favicon.ico") ||
               path.equals("/error") ||
               path.startsWith("/api/v1/system") ||
               path.startsWith("/api/v1/auth"); // Todas as rotas de autenticação são públicas
    }
}

