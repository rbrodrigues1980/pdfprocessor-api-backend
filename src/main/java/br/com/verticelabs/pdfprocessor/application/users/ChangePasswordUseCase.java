package br.com.verticelabs.pdfprocessor.application.users;

import br.com.verticelabs.pdfprocessor.domain.exceptions.UserNotFoundException;
import br.com.verticelabs.pdfprocessor.domain.repository.UserRepository;
import br.com.verticelabs.pdfprocessor.infrastructure.security.ReactiveSecurityContextHelper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Instant;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChangePasswordUseCase {
    
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    
    public Mono<Void> execute(String userId, String senhaAtual, String novaSenha) {
        return ReactiveSecurityContextHelper.getUserId()
                .flatMap(currentUserId -> {
                    boolean isOwnUser = currentUserId.equals(userId);
                    
                    return ReactiveSecurityContextHelper.isSuperAdmin()
                            .flatMap(isSuperAdmin -> {
                                if (isSuperAdmin) {
                                    return changePasswordAsSuperAdmin(userId, novaSenha);
                                } else {
                                    return ReactiveSecurityContextHelper.isTenantAdmin()
                                            .flatMap(isTenantAdmin -> {
                                                if (isTenantAdmin) {
                                                    return ReactiveSecurityContextHelper.getTenantId()
                                                            .flatMap(tenantId -> changePasswordAsTenantAdmin(tenantId, userId, novaSenha));
                                                } else if (isOwnUser) {
                                                    // Próprio usuário pode alterar sua senha
                                                    return changePasswordAsOwnUser(userId, senhaAtual, novaSenha);
                                                } else {
                                                    return Mono.error(new RuntimeException("Sem permissão para alterar senha deste usuário"));
                                                }
                                            });
                                }
                            });
                });
    }
    
    private Mono<Void> changePasswordAsSuperAdmin(String userId, String novaSenha) {
        return userRepository.findById(userId)
                .switchIfEmpty(Mono.error(new UserNotFoundException("Usuário não encontrado: " + userId)))
                .flatMap(user -> {
                    user.setSenhaHash(passwordEncoder.encode(novaSenha));
                    user.setUpdatedAt(Instant.now());
                    return userRepository.save(user)
                            .doOnSuccess(u -> log.info("✅ Senha alterada para usuário: {} ({})", u.getEmail(), u.getId()))
                            .then();
                });
    }
    
    private Mono<Void> changePasswordAsTenantAdmin(String tenantId, String userId, String novaSenha) {
        return userRepository.findByTenantIdAndId(tenantId, userId)
                .switchIfEmpty(Mono.error(new UserNotFoundException("Usuário não encontrado ou não pertence ao seu tenant: " + userId)))
                .flatMap(user -> {
                    user.setSenhaHash(passwordEncoder.encode(novaSenha));
                    user.setUpdatedAt(Instant.now());
                    return userRepository.save(user)
                            .doOnSuccess(u -> log.info("✅ Senha alterada para usuário: {} ({})", u.getEmail(), u.getId()))
                            .then();
                });
    }
    
    private Mono<Void> changePasswordAsOwnUser(String userId, String senhaAtual, String novaSenha) {
        if (senhaAtual == null || senhaAtual.isEmpty()) {
            return Mono.error(new RuntimeException("Senha atual é obrigatória"));
        }
        
        return userRepository.findById(userId)
                .switchIfEmpty(Mono.error(new UserNotFoundException("Usuário não encontrado: " + userId)))
                .flatMap(user -> {
                    if (!passwordEncoder.matches(senhaAtual, user.getSenhaHash())) {
                        return Mono.error(new RuntimeException("Senha atual incorreta"));
                    }
                    
                    user.setSenhaHash(passwordEncoder.encode(novaSenha));
                    user.setUpdatedAt(Instant.now());
                    return userRepository.save(user)
                            .doOnSuccess(u -> log.info("✅ Senha alterada pelo próprio usuário: {} ({})", u.getEmail(), u.getId()))
                            .then();
                });
    }
}

