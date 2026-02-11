package br.com.verticelabs.pdfprocessor.infrastructure.config;

import br.com.verticelabs.pdfprocessor.domain.model.Tenant;
import br.com.verticelabs.pdfprocessor.domain.model.User;
import br.com.verticelabs.pdfprocessor.domain.repository.TenantRepository;
import br.com.verticelabs.pdfprocessor.domain.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

/**
 * Inicializador de dados de teste para multi-tenant
 * Cria: SUPER_ADMIN, Tenant de teste, TENANT_ADMIN e TENANT_USER
 */
@Slf4j
@Component
@Order(3) // Executa depois dos outros inicializadores
@RequiredArgsConstructor
public class MultiTenantTestDataInitializer implements CommandLineRunner {

    private final UserRepository userRepository;
    private final TenantRepository tenantRepository;
    private final PasswordEncoder passwordEncoder;

    // Credenciais de teste — valores lidos de variáveis de ambiente (com fallback apenas para dev local)
    private static final String SUPER_ADMIN_EMAIL = System.getenv().getOrDefault("SUPER_ADMIN_EMAIL", "superadmin@teste.com");
    private static final String SUPER_ADMIN_PASSWORD = System.getenv().getOrDefault("SUPER_ADMIN_PASSWORD", "SuperAdmin123!");
    
    // Tenant 1
    private static final String TENANT1_NAME = "Empresa Teste LTDA";
    private static final String TENANT1_DOMAIN = "teste.com.br";
    private static final String TENANT1_ADMIN_EMAIL = System.getenv().getOrDefault("TENANT1_ADMIN_EMAIL", "admin@teste.com");
    private static final String TENANT1_ADMIN_PASSWORD = System.getenv().getOrDefault("TENANT1_ADMIN_PASSWORD", "Admin123!");
    private static final String TENANT1_USER_EMAIL = "usuario@teste.com";
    private static final String TENANT1_USER_PASSWORD = System.getenv().getOrDefault("TENANT1_USER_PASSWORD", "Usuario123!");
    
    // Tenant 2
    private static final String TENANT2_NAME = "Empresa Demo S.A.";
    private static final String TENANT2_DOMAIN = "demo.com.br";
    private static final String TENANT2_ADMIN_EMAIL = "admin@demo.com";
    private static final String TENANT2_ADMIN_PASSWORD = System.getenv().getOrDefault("TENANT2_ADMIN_PASSWORD", "Admin456!");
    private static final String TENANT2_USER_EMAIL = "usuario@demo.com";
    private static final String TENANT2_USER_PASSWORD = System.getenv().getOrDefault("TENANT2_USER_PASSWORD", "Usuario456!");

    @Override
    public void run(String... args) {
        log.info("🚀 Inicializando dados de teste multi-tenant...");
        
        createSuperAdmin()
                .then(createTestTenant1())
                .flatMap(tenant1Id -> 
                    Mono.when(
                        createTenantAdmin(tenant1Id, TENANT1_ADMIN_EMAIL, TENANT1_ADMIN_PASSWORD, "Administrador Empresa Teste"),
                        createTenantUser(tenant1Id, TENANT1_USER_EMAIL, TENANT1_USER_PASSWORD, "Usuário Empresa Teste")
                    )
                    .then(createTestTenant2())
                    .flatMap(tenant2Id ->
                        Mono.when(
                            createTenantAdmin(tenant2Id, TENANT2_ADMIN_EMAIL, TENANT2_ADMIN_PASSWORD, "Administrador Empresa Demo"),
                            createTenantUser(tenant2Id, TENANT2_USER_EMAIL, TENANT2_USER_PASSWORD, "Usuário Empresa Demo")
                        )
                    )
                )
                .doOnSuccess(v -> {
                    log.info("✅ Dados de teste multi-tenant criados com sucesso!");
                    log.info("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
                    log.info("📋 CREDENCIAIS DE TESTE:");
                    log.info("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
                    log.info("🔑 SUPER_ADMIN:");
                    log.info("   Email: {}", SUPER_ADMIN_EMAIL);
                    log.info("   Senha: {}", SUPER_ADMIN_PASSWORD);
                    log.info("   Role: SUPER_ADMIN (sem tenantId)");
                    log.info("");
                    log.info("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
                    log.info("🏢 TENANT 1: {}", TENANT1_NAME);
                    log.info("   Domínio: {}", TENANT1_DOMAIN);
                    log.info("");
                    log.info("   👤 TENANT_ADMIN:");
                    log.info("      Email: {}", TENANT1_ADMIN_EMAIL);
                    log.info("      Senha: {}", TENANT1_ADMIN_PASSWORD);
                    log.info("      Role: TENANT_ADMIN");
                    log.info("");
                    log.info("   👤 TENANT_USER:");
                    log.info("      Email: {}", TENANT1_USER_EMAIL);
                    log.info("      Senha: {}", TENANT1_USER_PASSWORD);
                    log.info("      Role: TENANT_USER");
                    log.info("");
                    log.info("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
                    log.info("🏢 TENANT 2: {}", TENANT2_NAME);
                    log.info("   Domínio: {}", TENANT2_DOMAIN);
                    log.info("");
                    log.info("   👤 TENANT_ADMIN:");
                    log.info("      Email: {}", TENANT2_ADMIN_EMAIL);
                    log.info("      Senha: {}", TENANT2_ADMIN_PASSWORD);
                    log.info("      Role: TENANT_ADMIN");
                    log.info("");
                    log.info("   👤 TENANT_USER:");
                    log.info("      Email: {}", TENANT2_USER_EMAIL);
                    log.info("      Senha: {}", TENANT2_USER_PASSWORD);
                    log.info("      Role: TENANT_USER");
                    log.info("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
                })
                .doOnError(error -> log.error("❌ Erro ao inicializar dados de teste multi-tenant", error))
                .subscribe();
    }

    private Mono<Void> createSuperAdmin() {
        return userRepository.existsByEmail(SUPER_ADMIN_EMAIL)
                .flatMap(exists -> {
                    if (Boolean.TRUE.equals(exists)) {
                        log.info("ℹ️  SUPER_ADMIN já existe: {}", SUPER_ADMIN_EMAIL);
                        return Mono.empty();
                    }
                    log.info("🔨 Criando SUPER_ADMIN...");
                    User superAdmin = User.builder()
                            .id(UUID.randomUUID().toString())
                            .tenantId(null) // SUPER_ADMIN não tem tenant
                            .nome("Super Administrador")
                            .email(SUPER_ADMIN_EMAIL)
                            .senhaHash(passwordEncoder.encode(SUPER_ADMIN_PASSWORD))
                            .roles(Set.of("SUPER_ADMIN"))
                            .twoFactorEnabled(false)
                            .ativo(true)
                            .createdAt(Instant.now())
                            .build();
                    
                    return userRepository.save(superAdmin)
                            .doOnSuccess(u -> log.info("✅ SUPER_ADMIN criado: {}", u.getEmail()))
                            .then();
                });
    }

    private Mono<String> createTestTenant1() {
        return tenantRepository.findByNome(TENANT1_NAME)
                .map(tenant -> {
                    log.info("ℹ️  Tenant 1 já existe: {} (ID: {})", tenant.getNome(), tenant.getId());
                    return tenant.getId();
                })
                .switchIfEmpty(
                        Mono.defer(() -> {
                            log.info("🔨 Criando tenant 1: {}...", TENANT1_NAME);
                            Tenant tenant = Tenant.builder()
                                    .id(UUID.randomUUID().toString())
                                    .nome(TENANT1_NAME)
                                    .dominio(TENANT1_DOMAIN)
                                    .ativo(true)
                                    .config(Tenant.TenantConfig.builder()
                                            .twoFactorRequired(false)
                                            .maxUsers(100)
                                            .build())
                                    .createdAt(Instant.now())
                                    .build();
                            
                            return tenantRepository.save(tenant)
                                    .doOnSuccess(t -> log.info("✅ Tenant 1 criado: {} (ID: {})", t.getNome(), t.getId()))
                                    .map(Tenant::getId);
                        })
                );
    }

    private Mono<String> createTestTenant2() {
        return tenantRepository.findByNome(TENANT2_NAME)
                .map(tenant -> {
                    log.info("ℹ️  Tenant 2 já existe: {} (ID: {})", tenant.getNome(), tenant.getId());
                    return tenant.getId();
                })
                .switchIfEmpty(
                        Mono.defer(() -> {
                            log.info("🔨 Criando tenant 2: {}...", TENANT2_NAME);
                            Tenant tenant = Tenant.builder()
                                    .id(UUID.randomUUID().toString())
                                    .nome(TENANT2_NAME)
                                    .dominio(TENANT2_DOMAIN)
                                    .ativo(true)
                                    .config(Tenant.TenantConfig.builder()
                                            .twoFactorRequired(false)
                                            .maxUsers(100)
                                            .build())
                                    .createdAt(Instant.now())
                                    .build();
                            
                            return tenantRepository.save(tenant)
                                    .doOnSuccess(t -> log.info("✅ Tenant 2 criado: {} (ID: {})", t.getNome(), t.getId()))
                                    .map(Tenant::getId);
                        })
                );
    }

    private Mono<Void> createTenantAdmin(String tenantId, String email, String password, String nome) {
        return userRepository.existsByEmail(email)
                .flatMap(exists -> {
                    if (Boolean.TRUE.equals(exists)) {
                        log.info("ℹ️  TENANT_ADMIN já existe: {}", email);
                        return Mono.empty();
                    }
                    log.info("🔨 Criando TENANT_ADMIN para tenant {}...", tenantId);
                    User admin = User.builder()
                            .id(UUID.randomUUID().toString())
                            .tenantId(tenantId)
                            .nome(nome)
                            .email(email)
                            .senhaHash(passwordEncoder.encode(password))
                            .roles(Set.of("TENANT_ADMIN"))
                            .twoFactorEnabled(false)
                            .ativo(true)
                            .createdAt(Instant.now())
                            .build();
                    
                    return userRepository.save(admin)
                            .doOnSuccess(u -> log.info("✅ TENANT_ADMIN criado: {}", u.getEmail()))
                            .then();
                });
    }

    private Mono<Void> createTenantUser(String tenantId, String email, String password, String nome) {
        return userRepository.existsByEmail(email)
                .flatMap(exists -> {
                    if (Boolean.TRUE.equals(exists)) {
                        log.info("ℹ️  TENANT_USER já existe: {}", email);
                        return Mono.empty();
                    }
                    log.info("🔨 Criando TENANT_USER para tenant {}...", tenantId);
                    User user = User.builder()
                            .id(UUID.randomUUID().toString())
                            .tenantId(tenantId)
                            .nome(nome)
                            .email(email)
                            .senhaHash(passwordEncoder.encode(password))
                            .roles(Set.of("TENANT_USER"))
                            .twoFactorEnabled(false)
                            .ativo(true)
                            .createdAt(Instant.now())
                            .build();
                    
                    return userRepository.save(user)
                            .doOnSuccess(u -> log.info("✅ TENANT_USER criado: {}", u.getEmail()))
                            .then();
                });
    }
}

