package br.com.verticelabs.pdfprocessor.infrastructure.mongodb;

import br.com.verticelabs.pdfprocessor.domain.model.User;
import br.com.verticelabs.pdfprocessor.domain.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.HashSet;

@Component
@RequiredArgsConstructor
public class MongoUserRepositoryAdapter implements UserRepository {

    private final SpringDataUserRepository repository;

    @Override
    public Mono<User> findByEmail(String email) {
        return repository.findByEmail(email)
                .map(this::ensureMutableCollections);
    }

    @Override
    public Mono<User> findByTenantIdAndId(String tenantId, String id) {
        return repository.findByTenantIdAndId(tenantId, id)
                .map(this::ensureMutableCollections);
    }

    @Override
    public Mono<User> findById(String id) {
        return repository.findById(id)
                .map(this::ensureMutableCollections);
    }

    @Override
    public Mono<User> findByRefreshToken(String refreshToken) {
        return repository.findByRefreshToken(refreshToken)
                .map(this::ensureMutableCollections);
    }

    @Override
    public Mono<User> save(User user) {
        return repository.save(user);
    }

    @Override
    public Mono<Boolean> existsByEmail(String email) {
        return repository.existsByEmail(email);
    }

    @Override
    public Flux<User> findAllByTenantId(String tenantId) {
        return repository.findAllByTenantId(tenantId)
                .map(this::ensureMutableCollections);
    }

    @Override
    public Flux<User> findAllByTenantIdAndAtivo(String tenantId, Boolean ativo) {
        return repository.findAllByTenantIdAndAtivo(tenantId, ativo)
                .map(this::ensureMutableCollections);
    }

    @Override
    public Mono<Long> countByTenantId(String tenantId) {
        return repository.countByTenantId(tenantId);
    }

    @Override
    public Mono<Long> countByTenantIdAndRole(String tenantId, String role) {
        return repository.countByTenantIdAndRole(tenantId, role);
    }

    @Override
    public Mono<Long> countByRole(String role) {
        return repository.countByRole(role);
    }

    // Métodos legados
    @Override
    @Deprecated
    public Mono<User> findByUsername(String username) {
        return repository.findByUsername(username)
                .map(this::ensureMutableCollections);
    }

    @Override
    @Deprecated
    public Mono<Boolean> existsByUsername(String username) {
        return repository.existsByUsername(username);
    }
    
    /**
     * Garante que as coleções do User sejam mutáveis após deserialização do MongoDB
     */
    private User ensureMutableCollections(User user) {
        if (user != null && user.getRoles() != null) {
            // Se roles não for um HashSet, criar um novo HashSet mutável
            if (!(user.getRoles() instanceof java.util.HashSet)) {
                user.setRoles(new HashSet<>(user.getRoles()));
            }
        }
        return user;
    }
}
