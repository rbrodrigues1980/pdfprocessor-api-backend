package br.com.verticelabs.pdfprocessor.application.tenant;

import br.com.verticelabs.pdfprocessor.domain.model.Tenant;
import br.com.verticelabs.pdfprocessor.domain.repository.TenantRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.UUID;

/**
 * Use case para criar tenant (apenas SUPER_ADMIN pode executar)
 */
@Service
@RequiredArgsConstructor
public class CreateTenantUseCase {
    
    private final TenantRepository tenantRepository;
    
    public Mono<Tenant> execute(String nome, String dominio) {
        return tenantRepository.findByNome(nome)
                .flatMap(existing -> Mono.<Tenant>error(new RuntimeException("Tenant com este nome já existe")))
                .switchIfEmpty(Mono.defer(() -> {
                    Tenant tenant = Tenant.builder()
                            .id(UUID.randomUUID().toString())
                            .nome(nome)
                            .dominio(dominio)
                            .ativo(true)
                            .config(Tenant.TenantConfig.builder()
                                    .twoFactorRequired(false)
                                    .build())
                            .createdAt(Instant.now())
                            .build();
                    
                    return tenantRepository.save(tenant);
                }));
    }
}

