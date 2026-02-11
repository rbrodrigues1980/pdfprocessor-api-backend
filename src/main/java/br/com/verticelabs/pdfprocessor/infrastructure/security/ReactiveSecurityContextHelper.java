package br.com.verticelabs.pdfprocessor.infrastructure.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Helper para acessar informações do usuário autenticado do SecurityContext no WebFlux
 */
public class ReactiveSecurityContextHelper {
    
    /**
     * Obtém o userId (subject do JWT) do contexto de segurança atual
     */
    public static Mono<String> getUserId() {
        return ReactiveSecurityContextHolder.getContext()
                .map(ctx -> ctx.getAuthentication())
                .cast(Authentication.class)
                .map(Authentication::getName)
                .switchIfEmpty(Mono.error(new IllegalStateException("Usuário não autenticado")));
    }
    
    /**
     * Obtém o tenantId do contexto de segurança atual (do details do Authentication)
     */
    public static Mono<String> getTenantId() {
        return ReactiveSecurityContextHolder.getContext()
                .map(ctx -> ctx.getAuthentication())
                .cast(Authentication.class)
                .flatMap(auth -> {
                    Object details = auth.getDetails();
                    if (details instanceof String && !((String) details).isEmpty()) {
                        return Mono.just((String) details);
                    }
                    return Mono.empty();
                })
                .switchIfEmpty(Mono.error(new IllegalStateException("Usuário não autenticado ou tenantId não encontrado")));
    }
    
    /**
     * Obtém as roles do usuário autenticado
     */
    public static Mono<List<String>> getRoles() {
        return ReactiveSecurityContextHolder.getContext()
                .map(ctx -> ctx.getAuthentication())
                .cast(Authentication.class)
                .map(auth -> auth.getAuthorities().stream()
                        .map(GrantedAuthority::getAuthority)
                        .map(authority -> authority.replace("ROLE_", "")) // Remove prefixo ROLE_
                        .collect(Collectors.toList()))
                .switchIfEmpty(Mono.error(new IllegalStateException("Usuário não autenticado")));
    }
    
    /**
     * Verifica se o usuário tem uma role específica
     */
    public static Mono<Boolean> hasRole(String role) {
        return getRoles()
                .map(roles -> roles.contains(role));
    }
    
    /**
     * Verifica se o usuário é SUPER_ADMIN
     */
    public static Mono<Boolean> isSuperAdmin() {
        return hasRole("SUPER_ADMIN");
    }
    
    /**
     * Verifica se o usuário é TENANT_ADMIN
     */
    public static Mono<Boolean> isTenantAdmin() {
        return hasRole("TENANT_ADMIN");
    }
}

