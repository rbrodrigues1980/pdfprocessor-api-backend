package br.com.verticelabs.pdfprocessor.domain.repository;

import br.com.verticelabs.pdfprocessor.domain.model.User;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface UserRepository {
    Mono<User> findByEmail(String email); // Email é único globalmente
    
    Mono<User> findByTenantIdAndId(String tenantId, String id);
    
    Mono<User> findById(String id);
    
    Mono<User> findByRefreshToken(String refreshToken); // Busca usuário pelo refresh token
    
    Mono<User> save(User user);
    
    Mono<Boolean> existsByEmail(String email);
    
    Flux<User> findAllByTenantId(String tenantId);
    
    Flux<User> findAllByTenantIdAndAtivo(String tenantId, Boolean ativo);
    
    Mono<Long> countByTenantId(String tenantId);
    
    Mono<Long> countByTenantIdAndRole(String tenantId, String role);
    
    Mono<Long> countByRole(String role);
    
    // Métodos legados (manter para compatibilidade)
    @Deprecated
    Mono<User> findByUsername(String username);
    
    @Deprecated
    Mono<Boolean> existsByUsername(String username);
}
