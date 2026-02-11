package br.com.verticelabs.pdfprocessor.infrastructure.mongodb;

import br.com.verticelabs.pdfprocessor.domain.model.User;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Repository
public interface SpringDataUserRepository extends ReactiveMongoRepository<User, String> {
    // Usar Query customizada para evitar erro quando há duplicados (antes do índice único ser aplicado)
    // O método retorna apenas o primeiro resultado encontrado
    @Query("{ 'email': ?0 }")
    Flux<User> findAllByEmail(String email);
    
    // Método que retorna apenas o primeiro resultado (para compatibilidade)
    default Mono<User> findByEmail(String email) {
        return findAllByEmail(email).next();
    }
    
    Mono<User> findByTenantIdAndId(String tenantId, String id);
    
    Flux<User> findAllByTenantId(String tenantId);
    
    Flux<User> findAllByTenantIdAndAtivo(String tenantId, Boolean ativo);
    
    Mono<Boolean> existsByEmail(String email);
    
    @Query(value = "{ 'tenantId': ?0 }", count = true)
    Mono<Long> countByTenantId(String tenantId);
    
    @Query(value = "{ 'tenantId': ?0, 'roles': { $in: [?1] } }", count = true)
    Mono<Long> countByTenantIdAndRole(String tenantId, String role);
    
    @Query(value = "{ 'roles': { $in: [?0] } }", count = true)
    Mono<Long> countByRole(String role);
    
    // Buscar usuário pelo refresh token (busca em todos os usuários)
    default Mono<User> findByRefreshToken(String refreshToken) {
        return findAll()
                .filter(user -> user.getRefreshTokens() != null)
                .filter(user -> user.getRefreshTokens().stream()
                        .anyMatch(rt -> rt.getToken().equals(refreshToken) && 
                                       !Boolean.TRUE.equals(rt.getUsed()) && 
                                       rt.getExpiresAt() != null &&
                                       rt.getExpiresAt().isAfter(java.time.Instant.now())))
                .next();
    }
    
    // Métodos legados - implementados manualmente porque username não existe mais
    // Usar findByEmail ao invés de findByUsername
    @Deprecated
    default Mono<User> findByUsername(String username) {
        // Por compatibilidade, buscar por email (username era usado como email antes)
        return findByEmail(username);
    }

    @Deprecated
    default Mono<Boolean> existsByUsername(String username) {
        // Por compatibilidade, verificar por email
        return existsByEmail(username);
    }
}
