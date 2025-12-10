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
public class ActivateUserUseCase {
    
    private final UserRepository userRepository;
    
    public Mono<User> execute(String userId) {
        return ReactiveSecurityContextHelper.isSuperAdmin()
                .flatMap(isSuperAdmin -> {
                    if (isSuperAdmin) {
                        return activateAsSuperAdmin(userId);
                    } else {
                        return ReactiveSecurityContextHelper.getTenantId()
                                .flatMap(tenantId -> activateAsTenantAdmin(tenantId, userId));
                    }
                });
    }
    
    private Mono<User> activateAsSuperAdmin(String userId) {
        return userRepository.findById(userId)
                .switchIfEmpty(Mono.error(new UserNotFoundException("Usuário não encontrado: " + userId)))
                .flatMap(this::activateUser);
    }
    
    private Mono<User> activateAsTenantAdmin(String tenantId, String userId) {
        return userRepository.findByTenantIdAndId(tenantId, userId)
                .switchIfEmpty(Mono.error(new UserNotFoundException("Usuário não encontrado ou não pertence ao seu tenant: " + userId)))
                .flatMap(this::activateUser);
    }
    
    private Mono<User> activateUser(User user) {
        user.setAtivo(true);
        user.setDesativadoEm(null);
        user.setUpdatedAt(Instant.now());
        
        return userRepository.save(user)
                .doOnSuccess(u -> log.info("✅ Usuário reativado: {} ({})", u.getEmail(), u.getId()));
    }
}

