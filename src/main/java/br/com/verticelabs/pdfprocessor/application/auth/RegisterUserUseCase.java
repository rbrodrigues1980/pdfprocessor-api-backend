package br.com.verticelabs.pdfprocessor.application.auth;

import br.com.verticelabs.pdfprocessor.domain.model.User;
import br.com.verticelabs.pdfprocessor.domain.repository.UserRepository;
import br.com.verticelabs.pdfprocessor.infrastructure.tenant.ReactiveTenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

/**
 * Use case para criar um usuário comum (TENANT_ADMIN pode executar)
 */
@Service
@RequiredArgsConstructor
public class RegisterUserUseCase {
    
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    
    public Mono<User> execute(String nome, String email, String senha, Set<String> roles) {
        return ReactiveTenantContext.getTenantId()
                .flatMap(tenantId -> userRepository.existsByEmail(email)
                        .flatMap(exists -> {
                            if (exists) {
                                return Mono.error(new RuntimeException("Email já está em uso"));
                            }
                            
                            User user = User.builder()
                                    .id(UUID.randomUUID().toString())
                                    .tenantId(tenantId)
                                    .nome(nome)
                                    .email(email)
                                    .senhaHash(passwordEncoder.encode(senha))
                                    .roles(roles != null && !roles.isEmpty() ? roles : Set.of("TENANT_USER"))
                                    .ativo(true)
                                    .createdAt(Instant.now())
                                    .build();
                            
                            return userRepository.save(user);
                        }));
    }
}

