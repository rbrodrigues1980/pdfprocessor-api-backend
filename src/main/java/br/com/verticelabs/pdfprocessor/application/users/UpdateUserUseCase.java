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
import java.util.HashSet;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class UpdateUserUseCase {
    
    private final UserRepository userRepository;
    
    public Mono<User> execute(String userId, String nome, String email, Set<String> roles, String telefone, Boolean ativo) {
        return ReactiveSecurityContextHelper.isSuperAdmin()
                .flatMap(isSuperAdmin -> {
                    if (isSuperAdmin) {
                        return updateAsSuperAdmin(userId, nome, email, roles, telefone, ativo);
                    } else {
                        return ReactiveSecurityContextHelper.getTenantId()
                                .flatMap(tenantId -> updateAsTenantAdmin(tenantId, userId, nome, email, roles, telefone, ativo));
                    }
                });
    }
    
    private Mono<User> updateAsSuperAdmin(String userId, String nome, String email, Set<String> roles, String telefone, Boolean ativo) {
        return userRepository.findById(userId)
                .switchIfEmpty(Mono.error(new UserNotFoundException("Usuário não encontrado: " + userId)))
                .flatMap(user -> {
                    // Verificar se email já existe em outro usuário
                    if (!user.getEmail().equals(email)) {
                        return userRepository.existsByEmail(email)
                                .flatMap(exists -> {
                                    if (exists) {
                                        return Mono.error(new RuntimeException("Email já está em uso"));
                                    }
                                    return updateUser(user, nome, email, roles, telefone, ativo);
                                });
                    }
                    return updateUser(user, nome, email, roles, telefone, ativo);
                });
    }
    
    private Mono<User> updateAsTenantAdmin(String tenantId, String userId, String nome, String email, Set<String> roles, String telefone, Boolean ativo) {
        return userRepository.findByTenantIdAndId(tenantId, userId)
                .switchIfEmpty(Mono.error(new UserNotFoundException("Usuário não encontrado ou não pertence ao seu tenant: " + userId)))
                .flatMap(user -> {
                    // TENANT_ADMIN não pode alterar para SUPER_ADMIN
                    if (roles.contains("SUPER_ADMIN")) {
                        return Mono.error(new RuntimeException("TENANT_ADMIN não pode alterar roles para SUPER_ADMIN"));
                    }
                    
                    // Verificar se email já existe em outro usuário
                    if (!user.getEmail().equals(email)) {
                        return userRepository.existsByEmail(email)
                                .flatMap(exists -> {
                                    if (exists) {
                                        return Mono.error(new RuntimeException("Email já está em uso"));
                                    }
                                    return updateUser(user, nome, email, roles, telefone, ativo);
                                });
                    }
                    return updateUser(user, nome, email, roles, telefone, ativo);
                });
    }
    
    private Mono<User> updateUser(User user, String nome, String email, Set<String> roles, String telefone, Boolean ativo) {
        user.setNome(nome);
        user.setEmail(email);
        // Garantir que roles seja um HashSet mutável
        user.setRoles(roles != null ? new HashSet<>(roles) : new HashSet<>());
        if (telefone != null) {
            user.setTelefone(telefone);
        }
        if (ativo != null) {
            user.setAtivo(ativo);
            if (!ativo && user.getDesativadoEm() == null) {
                user.setDesativadoEm(Instant.now());
            } else if (ativo) {
                user.setDesativadoEm(null);
            }
        }
        user.setUpdatedAt(Instant.now());
        
        return userRepository.save(user)
                .doOnSuccess(u -> log.info("✅ Usuário atualizado: {} ({})", u.getEmail(), u.getId()));
    }
}

