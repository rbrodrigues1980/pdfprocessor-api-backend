package br.com.verticelabs.pdfprocessor.infrastructure.tenant;

import reactor.core.publisher.Mono;
import reactor.util.context.Context;

/**
 * Helper para acessar o tenantId do ReactiveContext no WebFlux
 */
public class ReactiveTenantContext {
    
    private static final String TENANT_ID_KEY = "tenantId";
    
    /**
     * Obtém o tenantId do contexto reativo atual
     * Retorna "GLOBAL" se for SUPER_ADMIN sem tenantId específico
     */
    public static Mono<String> getTenantId() {
        return Mono.deferContextual(ctx -> {
            String tenantId = ctx.getOrDefault(TENANT_ID_KEY, null);
            if (tenantId == null) {
                return Mono.error(new IllegalStateException("TenantId não encontrado no contexto. Certifique-se de que o TenantFilter está configurado."));
            }
            // Se for "GLOBAL", significa SUPER_ADMIN sem tenant específico
            // Nesse caso, pode retornar null ou "GLOBAL" dependendo do uso
            return Mono.just(tenantId);
        });
    }
    
    /**
     * Verifica se o tenantId é "GLOBAL" (SUPER_ADMIN sem tenant específico)
     */
    public static Mono<Boolean> isGlobalTenant() {
        return getTenantId()
                .map(tenantId -> "GLOBAL".equals(tenantId));
    }
    
    /**
     * Adiciona o tenantId ao contexto reativo
     */
    public static <T> Mono<T> withTenant(Mono<T> mono, String tenantId) {
        return mono.contextWrite(Context.of(TENANT_ID_KEY, tenantId));
    }
    
    /**
     * Adiciona o tenantId ao contexto reativo (versão para Flux)
     */
    public static <T> reactor.core.publisher.Flux<T> withTenant(reactor.core.publisher.Flux<T> flux, String tenantId) {
        return flux.contextWrite(Context.of(TENANT_ID_KEY, tenantId));
    }
}

