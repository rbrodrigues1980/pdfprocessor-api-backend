package br.com.verticelabs.pdfprocessor.interfaces.tenant;

import br.com.verticelabs.pdfprocessor.application.tenant.CreateTenantUseCase;
import br.com.verticelabs.pdfprocessor.domain.model.Tenant;
import br.com.verticelabs.pdfprocessor.domain.repository.TenantRepository;
import br.com.verticelabs.pdfprocessor.interfaces.tenant.dto.CreateTenantRequest;
import br.com.verticelabs.pdfprocessor.interfaces.tenant.dto.TenantResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/tenants")
@RequiredArgsConstructor
public class TenantController {
    
    private final CreateTenantUseCase createTenantUseCase;
    private final TenantRepository tenantRepository;
    
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Mono<TenantResponse> createTenant(@RequestBody CreateTenantRequest request) {
        return createTenantUseCase.execute(request.getNome(), request.getDominio())
                .map(this::toResponse);
    }
    
    @GetMapping
    @ResponseStatus(HttpStatus.OK)
    public Flux<TenantResponse> listTenants() {
        return tenantRepository.findAll()
                .map(this::toResponse);
    }
    
    @GetMapping("/{id}")
    @ResponseStatus(HttpStatus.OK)
    public Mono<TenantResponse> getTenant(@PathVariable String id) {
        return tenantRepository.findById(id)
                .map(this::toResponse);
    }
    
    private TenantResponse toResponse(Tenant tenant) {
        return TenantResponse.builder()
                .id(tenant.getId())
                .nome(tenant.getNome())
                .dominio(tenant.getDominio())
                .ativo(tenant.getAtivo())
                .createdAt(tenant.getCreatedAt())
                .build();
    }
}

