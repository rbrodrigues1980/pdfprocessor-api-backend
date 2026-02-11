package br.com.verticelabs.pdfprocessor.application.auth;

import br.com.verticelabs.pdfprocessor.domain.exceptions.TenantNotFoundException;
import br.com.verticelabs.pdfprocessor.domain.model.Tenant;
import br.com.verticelabs.pdfprocessor.domain.model.User;
import br.com.verticelabs.pdfprocessor.domain.repository.TenantRepository;
import br.com.verticelabs.pdfprocessor.domain.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

/**
 * Use case para criar um admin de tenant (apenas SUPER_ADMIN pode executar)
 */
@Service
@RequiredArgsConstructor
public class RegisterAdminUseCase {
    
    private final TenantRepository tenantRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    
    public Mono<User> execute(String tenantId, String nome, String email, String senha) {
        return tenantRepository.findById(tenantId)
                .switchIfEmpty(Mono.error(new TenantNotFoundException("Tenant não encontrado: " + tenantId)))
                .filter(Tenant::getAtivo)
                .switchIfEmpty(Mono.error(new RuntimeException("Tenant está inativo")))
                .flatMap(tenant -> userRepository.existsByEmail(email)
                        .flatMap(exists -> {
                            if (exists) {
                                return Mono.error(new RuntimeException("Email já está em uso"));
                            }
                            
                            User admin = User.builder()
                                    .id(UUID.randomUUID().toString())
                                    .tenantId(tenantId)
                                    .nome(nome)
                                    .email(email)
                                    .senhaHash(passwordEncoder.encode(senha))
                                    .roles(Set.of("TENANT_ADMIN"))
                                    .ativo(true)
                                    .createdAt(Instant.now())
                                    .build();
                            
                            return userRepository.save(admin);
                        }));
    }
}

