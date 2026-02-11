package br.com.verticelabs.pdfprocessor.application.users;

import br.com.verticelabs.pdfprocessor.domain.exceptions.TenantNotFoundException;
import br.com.verticelabs.pdfprocessor.domain.model.Tenant;
import br.com.verticelabs.pdfprocessor.domain.model.User;
import br.com.verticelabs.pdfprocessor.domain.repository.TenantRepository;
import br.com.verticelabs.pdfprocessor.domain.repository.UserRepository;
import br.com.verticelabs.pdfprocessor.infrastructure.security.ReactiveSecurityContextHelper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class CreateUserUseCase {
    
    private final UserRepository userRepository;
    private final TenantRepository tenantRepository;
    private final PasswordEncoder passwordEncoder;
    
    public Mono<User> execute(String nome, String email, String senha, Set<String> roles, String tenantId, String telefone) {
        return ReactiveSecurityContextHelper.getUserId()
                .flatMap(currentUserId -> ReactiveSecurityContextHelper.isSuperAdmin()
                        .flatMap(isSuperAdmin -> {
                            if (isSuperAdmin) {
                                return createAsSuperAdmin(nome, email, senha, roles, tenantId, telefone);
                            } else {
                                return ReactiveSecurityContextHelper.isTenantAdmin()
                                        .flatMap(isTenantAdmin -> {
                                            if (isTenantAdmin) {
                                                return ReactiveSecurityContextHelper.getTenantId()
                                                        .flatMap(currentTenantId -> 
                                                                createAsTenantAdmin(nome, email, senha, roles, currentTenantId, telefone));
                                            } else {
                                                return Mono.error(new RuntimeException("Apenas SUPER_ADMIN ou TENANT_ADMIN podem criar usuários"));
                                            }
                                        });
                            }
                        }));
    }
    
    private Mono<User> createAsSuperAdmin(String nome, String email, String senha, Set<String> roles, String tenantId, String telefone) {
        // SUPER_ADMIN pode criar qualquer tipo de usuário
        // Se roles contém SUPER_ADMIN, tenantId deve ser null
        boolean isCreatingSuperAdmin = roles.contains("SUPER_ADMIN");
        
        if (isCreatingSuperAdmin && tenantId != null) {
            return Mono.error(new RuntimeException("SUPER_ADMIN não pode ter tenantId"));
        }
        
        if (!isCreatingSuperAdmin && tenantId == null) {
            return Mono.error(new RuntimeException("Usuários que não são SUPER_ADMIN devem ter tenantId"));
        }
        
        if (isCreatingSuperAdmin) {
            // Para SUPER_ADMIN, tenantId é null
            return userRepository.existsByEmail(email)
                    .flatMap(exists -> {
                        if (exists) {
                            return Mono.error(new RuntimeException("Email já está em uso"));
                        }
                        
                        User user = User.builder()
                                .id(UUID.randomUUID().toString())
                                .tenantId(null) // SUPER_ADMIN não tem tenantId
                                .nome(nome)
                                .email(email)
                                .senhaHash(passwordEncoder.encode(senha))
                                .roles(roles != null ? new HashSet<>(roles) : new HashSet<>())
                                .telefone(telefone)
                                .ativo(true)
                                .createdAt(Instant.now())
                                .build();
                        
                        return userRepository.save(user)
                                .doOnSuccess(u -> log.info("✅ Usuário criado: {} ({})", u.getEmail(), u.getId()));
                    });
        } else {
            // Para outros usuários, validar tenant
            return validateTenant(tenantId)
                    .flatMap(tenant -> userRepository.existsByEmail(email)
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
                                        .roles(roles != null ? new HashSet<>(roles) : new HashSet<>())
                                        .telefone(telefone)
                                        .ativo(true)
                                        .createdAt(Instant.now())
                                        .build();
                                
                                return userRepository.save(user)
                                        .doOnSuccess(u -> log.info("✅ Usuário criado: {} ({})", u.getEmail(), u.getId()));
                            }));
        }
    }
    
    private Mono<User> createAsTenantAdmin(String nome, String email, String senha, Set<String> roles, String tenantId, String telefone) {
        // TENANT_ADMIN só pode criar TENANT_ADMIN ou TENANT_USER do seu tenant
        if (roles.contains("SUPER_ADMIN")) {
            return Mono.error(new RuntimeException("TENANT_ADMIN não pode criar SUPER_ADMIN"));
        }
        
        return validateTenant(tenantId)
                .flatMap(tenant -> userRepository.existsByEmail(email)
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
                                    .roles(roles != null ? new HashSet<>(roles) : new HashSet<>())
                                    .telefone(telefone)
                                    .ativo(true)
                                    .createdAt(Instant.now())
                                    .build();
                            
                            return userRepository.save(user)
                                    .doOnSuccess(u -> log.info("✅ Usuário criado: {} ({}) no tenant {}", u.getEmail(), u.getId(), tenantId));
                        }));
    }
    
    private Mono<Tenant> validateTenant(String tenantId) {
        return tenantRepository.findById(tenantId)
                .switchIfEmpty(Mono.error(new TenantNotFoundException("Tenant não encontrado: " + tenantId)))
                .filter(Tenant::getAtivo)
                .switchIfEmpty(Mono.error(new RuntimeException("Tenant está inativo")));
    }
}

