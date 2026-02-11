package br.com.verticelabs.pdfprocessor.application.users;

import br.com.verticelabs.pdfprocessor.domain.exceptions.UserNotFoundException;
import br.com.verticelabs.pdfprocessor.domain.model.User;
import br.com.verticelabs.pdfprocessor.domain.repository.UserRepository;
import br.com.verticelabs.pdfprocessor.infrastructure.security.ReactiveSecurityContextHelper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Instant;

@Slf4j
@Service
@RequiredArgsConstructor
public class DeactivateUserUseCase {
    
    private final UserRepository userRepository;
    
    public Mono<User> execute(String userId) {
        return ReactiveSecurityContextHelper.getUserId()
                .flatMap(currentUserId -> {
                    // Não pode desativar a si mesmo
                    if (currentUserId.equals(userId)) {
                        return Mono.error(new RuntimeException("Não é possível desativar a si mesmo"));
                    }
                    
                    return ReactiveSecurityContextHelper.isSuperAdmin()
                            .flatMap(isSuperAdmin -> {
                                if (isSuperAdmin) {
                                    return deactivateAsSuperAdmin(userId);
                                } else {
                                    return ReactiveSecurityContextHelper.getTenantId()
                                            .flatMap(tenantId -> deactivateAsTenantAdmin(tenantId, userId));
                                }
                            });
                });
    }
    
    private Mono<User> deactivateAsSuperAdmin(String userId) {
        return userRepository.findById(userId)
                .switchIfEmpty(Mono.error(new UserNotFoundException("Usuário não encontrado: " + userId)))
                .flatMap(user -> {
                    // Verificar se é o último SUPER_ADMIN
                    if (user.isSuperAdmin()) {
                        return userRepository.countByRole("SUPER_ADMIN")
                                .flatMap(count -> {
                                    if (count <= 1) {
                                        return Mono.error(new RuntimeException("Não é possível desativar o último SUPER_ADMIN"));
                                    }
                                    return deactivateUser(user);
                                });
                    }
                    return deactivateUser(user);
                });
    }
    
    private Mono<User> deactivateAsTenantAdmin(String tenantId, String userId) {
        return userRepository.findByTenantIdAndId(tenantId, userId)
                .switchIfEmpty(Mono.error(new UserNotFoundException("Usuário não encontrado ou não pertence ao seu tenant: " + userId)))
                .flatMap(user -> {
                    // Verificar se é o último TENANT_ADMIN do tenant
                    if (user.getRoles().contains("TENANT_ADMIN")) {
                        return userRepository.countByTenantIdAndRole(tenantId, "TENANT_ADMIN")
                                .flatMap(count -> {
                                    if (count <= 1) {
                                        return Mono.error(new RuntimeException("Não é possível desativar o último TENANT_ADMIN do tenant"));
                                    }
                                    return deactivateUser(user);
                                });
                    }
                    return deactivateUser(user);
                });
    }
    
    private Mono<User> deactivateUser(User user) {
        user.setAtivo(false);
        user.setDesativadoEm(Instant.now());
        user.setUpdatedAt(Instant.now());
        
        return userRepository.save(user)
                .doOnSuccess(u -> log.info("✅ Usuário desativado: {} ({})", u.getEmail(), u.getId()));
    }
}

