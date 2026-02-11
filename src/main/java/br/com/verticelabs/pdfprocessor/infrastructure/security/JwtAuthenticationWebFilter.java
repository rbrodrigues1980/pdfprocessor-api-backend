package br.com.verticelabs.pdfprocessor.infrastructure.security;

import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.web.server.authentication.AuthenticationWebFilter;
import org.springframework.security.web.server.authentication.ServerAuthenticationConverter;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Filtro JWT para WebFlux que converte o token em Authentication
 */
public class JwtAuthenticationWebFilter extends AuthenticationWebFilter {
    
    public JwtAuthenticationWebFilter(JwtService jwtService) {
        super(new JwtReactiveAuthenticationManager(jwtService));
        setServerAuthenticationConverter(new JwtServerAuthenticationConverter(jwtService));
    }
    
    private static class JwtReactiveAuthenticationManager implements org.springframework.security.authentication.ReactiveAuthenticationManager {
        @SuppressWarnings("unused")
        public JwtReactiveAuthenticationManager(JwtService jwtService) {
            // jwtService não é usado aqui, mas mantido para compatibilidade
        }
        
        @Override
        public Mono<Authentication> authenticate(Authentication authentication) {
            return Mono.just(authentication);
        }
    }
    
    private static class JwtServerAuthenticationConverter implements ServerAuthenticationConverter {
        private final JwtService jwtService;
        
        public JwtServerAuthenticationConverter(JwtService jwtService) {
            this.jwtService = jwtService;
        }
        
        @Override
        public Mono<Authentication> convert(ServerWebExchange exchange) {
            // Se não houver token, retornar empty (não é erro, apenas não autenticado)
            String authHeader = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
            if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                return Mono.empty();
            }
            
            String token = authHeader.substring(7);
            
            // Validar token
            if (!jwtService.isTokenValid(token)) {
                return Mono.empty();
            }
            
            try {
                String userId = jwtService.extractUsername(token);
                String tenantId = jwtService.extractTenantId(token);
                List<String> roles = jwtService.extractRoles(token);
                
                List<SimpleGrantedAuthority> authorities = roles.stream()
                        .map(role -> new SimpleGrantedAuthority("ROLE_" + role))
                        .collect(Collectors.toList());
                
                UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                        userId, null, authorities
                );
                
                // Adicionar tenantId como detalhe
                auth.setDetails(tenantId);
                
                return Mono.just(auth);
            } catch (Exception e) {
                // Se houver erro ao processar token, retornar empty
                return Mono.empty();
            }
        }
    }
}

