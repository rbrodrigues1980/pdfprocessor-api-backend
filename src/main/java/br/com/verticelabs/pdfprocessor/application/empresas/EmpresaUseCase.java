package br.com.verticelabs.pdfprocessor.application.empresas;

import br.com.verticelabs.pdfprocessor.domain.exceptions.EmpresaDuplicadaException;
import br.com.verticelabs.pdfprocessor.domain.exceptions.EmpresaEmUsoException;
import br.com.verticelabs.pdfprocessor.domain.exceptions.EmpresaNotFoundException;
import br.com.verticelabs.pdfprocessor.domain.model.Empresa;
import br.com.verticelabs.pdfprocessor.domain.repository.EmpresaRepository;
import br.com.verticelabs.pdfprocessor.domain.repository.PersonRepository;
import br.com.verticelabs.pdfprocessor.infrastructure.security.ReactiveSecurityContextHelper;
import br.com.verticelabs.pdfprocessor.interfaces.empresas.dto.CreateEmpresaRequest;
import br.com.verticelabs.pdfprocessor.interfaces.empresas.dto.UpdateEmpresaRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.Locale;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmpresaUseCase {

    private final EmpresaRepository empresaRepository;
    private final PersonRepository personRepository;
    private final EmpresaPercentualHelper percentualHelper;

    public Mono<Empresa> criar(CreateEmpresaRequest request) {
        return resolveTenantId()
                .flatMap(tenantId -> {
                    String sigla = percentualHelper.normalizeSigla(request.getSigla());
                    return empresaRepository.existsByTenantIdAndSigla(tenantId, sigla)
                            .flatMap(exists -> {
                                if (Boolean.TRUE.equals(exists)) {
                                    return Mono.error(new EmpresaDuplicadaException(sigla, tenantId));
                                }
                                Empresa empresa = Empresa.builder()
                                        .tenantId(tenantId)
                                        .nome(request.getNome().trim())
                                        .sigla(sigla)
                                        .cnpj(percentualHelper.normalizeCnpj(request.getCnpj()))
                                        .percentuais(percentualHelper.mapPercentuaisFromRequest(
                                                request.getPercentuais(), null))
                                        .ativo(true)
                                        .createdAt(Instant.now())
                                        .updatedAt(Instant.now())
                                        .build();
                                return empresaRepository.save(empresa);
                            });
                });
    }

    public Flux<Empresa> listar(String nomeFilter, String siglaFilter, boolean apenasAtivas) {
        return resolveTenantId()
                .flatMapMany(tenantId -> {
                    Flux<Empresa> flux = apenasAtivas
                            ? empresaRepository.findAllByTenantIdAndAtivoTrue(tenantId)
                            : empresaRepository.findAllByTenantId(tenantId);
                    return flux.filter(empresa -> matchesFilter(empresa, nomeFilter, siglaFilter));
                });
    }

    public Mono<Empresa> buscarPorId(String id) {
        return resolveTenantAccess(id);
    }

    public Mono<Empresa> atualizar(String id, UpdateEmpresaRequest request) {
        return resolveTenantAccess(id)
                .flatMap(empresa -> {
                    String sigla = percentualHelper.normalizeSigla(request.getSigla());
                    return empresaRepository.existsByTenantIdAndSiglaAndIdNot(
                                    empresa.getTenantId(), sigla, id)
                            .flatMap(exists -> {
                                if (Boolean.TRUE.equals(exists)) {
                                    return Mono.error(new EmpresaDuplicadaException(sigla, empresa.getTenantId()));
                                }
                                empresa.setNome(request.getNome().trim());
                                empresa.setSigla(sigla);
                                empresa.setCnpj(percentualHelper.normalizeCnpj(request.getCnpj()));
                                empresa.setPercentuais(percentualHelper.mapPercentuaisFromRequest(
                                        request.getPercentuais(), empresa.getPercentuais()));
                                empresa.setUpdatedAt(Instant.now());
                                return empresaRepository.save(empresa);
                            });
                });
    }

    public Mono<Void> ativar(String id) {
        return resolveTenantAccess(id)
                .flatMap(empresa -> {
                    empresa.setAtivo(true);
                    empresa.setUpdatedAt(Instant.now());
                    return empresaRepository.save(empresa).then();
                });
    }

    public Mono<Void> desativar(String id) {
        return resolveTenantAccess(id)
                .flatMap(empresa -> {
                    empresa.setAtivo(false);
                    empresa.setUpdatedAt(Instant.now());
                    return empresaRepository.save(empresa).then();
                });
    }

    public Mono<Void> excluir(String id) {
        return resolveTenantAccess(id)
                .flatMap(empresa -> countPersonsByEmpresaId(id)
                        .flatMap(count -> {
                            if (count > 0) {
                                return Mono.error(new EmpresaEmUsoException(id, count));
                            }
                            return empresaRepository.deleteById(id);
                        }));
    }

    private Mono<Long> countPersonsByEmpresaId(String empresaId) {
        return personRepository.countByEmpresaId(empresaId);
    }

    private Mono<Empresa> resolveTenantAccess(String id) {
        return ReactiveSecurityContextHelper.isSuperAdmin()
                .flatMap(isSuperAdmin -> {
                    if (Boolean.TRUE.equals(isSuperAdmin)) {
                        return empresaRepository.findById(id)
                                .switchIfEmpty(Mono.error(new EmpresaNotFoundException(id)));
                    }
                    return ReactiveSecurityContextHelper.getTenantId()
                            .flatMap(tenantId -> empresaRepository.findByTenantIdAndId(tenantId, id)
                                    .switchIfEmpty(Mono.error(new EmpresaNotFoundException(id))));
                });
    }

    private Mono<String> resolveTenantId() {
        return ReactiveSecurityContextHelper.isSuperAdmin()
                .flatMap(isSuperAdmin -> {
                    if (Boolean.TRUE.equals(isSuperAdmin)) {
                        return Mono.just("GLOBAL");
                    }
                    return ReactiveSecurityContextHelper.getTenantId();
                });
    }

    private boolean matchesFilter(Empresa empresa, String nomeFilter, String siglaFilter) {
        if (nomeFilter != null && !nomeFilter.isBlank()) {
            if (empresa.getNome() == null
                    || !empresa.getNome().toLowerCase(Locale.ROOT).contains(nomeFilter.toLowerCase(Locale.ROOT))) {
                return false;
            }
        }
        if (siglaFilter != null && !siglaFilter.isBlank()) {
            if (empresa.getSigla() == null
                    || !empresa.getSigla().toLowerCase(Locale.ROOT).contains(siglaFilter.toLowerCase(Locale.ROOT))) {
                return false;
            }
        }
        return true;
    }
}
