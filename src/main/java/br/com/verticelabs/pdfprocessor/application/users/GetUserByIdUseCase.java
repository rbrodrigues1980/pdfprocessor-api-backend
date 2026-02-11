package br.com.verticelabs.pdfprocessor.application.users;

import br.com.verticelabs.pdfprocessor.domain.exceptions.UserNotFoundException;
import br.com.verticelabs.pdfprocessor.domain.model.User;
import br.com.verticelabs.pdfprocessor.domain.repository.UserRepository;
import br.com.verticelabs.pdfprocessor.infrastructure.security.ReactiveSecurityContextHelper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Slf4j
@Service
@RequiredArgsConstructor
public class GetUserByIdUseCase {
    
    private final UserRepository userRepository;
    
    public Mono<User> execute(String userId) {
        return ReactiveSecurityContextHelper.isSuperAdmin()
                .flatMap(isSuperAdmin -> {
                    if (isSuperAdmin) {
                        // SUPER_ADMIN pode buscar qualquer usuário
                        return userRepository.findById(userId)
                                .switchIfEmpty(Mono.error(new UserNotFoundException("Usuário não encontrado: " + userId)));
                    } else {
                        // TENANT_ADMIN só pode buscar usuários do seu tenant
                        return ReactiveSecurityContextHelper.getTenantId()
                                .flatMap(tenantId -> userRepository.findByTenantIdAndId(tenantId, userId)
                                        .switchIfEmpty(Mono.error(new UserNotFoundException("Usuário não encontrado ou não pertence ao seu tenant: " + userId))));
                    }
                });
    }
}

