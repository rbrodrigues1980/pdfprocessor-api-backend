package br.com.verticelabs.pdfprocessor.application.empresas;

import br.com.verticelabs.pdfprocessor.domain.exceptions.EmpresaDuplicadaException;
import br.com.verticelabs.pdfprocessor.domain.exceptions.EmpresaEmUsoException;
import br.com.verticelabs.pdfprocessor.domain.model.Empresa;
import br.com.verticelabs.pdfprocessor.domain.repository.EmpresaRepository;
import br.com.verticelabs.pdfprocessor.domain.repository.PersonRepository;
import br.com.verticelabs.pdfprocessor.infrastructure.security.ReactiveSecurityContextHelper;
import br.com.verticelabs.pdfprocessor.interfaces.empresas.dto.CreateEmpresaRequest;
import br.com.verticelabs.pdfprocessor.interfaces.empresas.dto.EmpresaPercentualDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EmpresaUseCaseTest {

    private static final String TENANT_ID = "tenant1";

    @Mock
    private EmpresaRepository empresaRepository;

    @Mock
    private PersonRepository personRepository;

    private EmpresaUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new EmpresaUseCase(empresaRepository, personRepository, new EmpresaPercentualHelper());
    }

    @Test
    void criar_persisteEmpresaQuandoSiglaUnica() {
        CreateEmpresaRequest request = buildCreateRequest("APCEF MG");

        try (MockedStatic<ReactiveSecurityContextHelper> security = mockTenantContext()) {
            when(empresaRepository.existsByTenantIdAndSigla(TENANT_ID, "APCEF MG")).thenReturn(Mono.just(false));
            when(empresaRepository.save(any(Empresa.class))).thenAnswer(inv -> {
                Empresa saved = inv.getArgument(0);
                saved.setId("emp1");
                return Mono.just(saved);
            });

            StepVerifier.create(useCase.criar(request))
                    .assertNext(empresa -> {
                        org.junit.jupiter.api.Assertions.assertEquals("APCEF MG", empresa.getSigla());
                        org.junit.jupiter.api.Assertions.assertEquals(TENANT_ID, empresa.getTenantId());
                        org.junit.jupiter.api.Assertions.assertEquals(1, empresa.getPercentuais().size());
                    })
                    .verifyComplete();
        }
    }

    @Test
    void criar_rejeitaSiglaDuplicada() {
        CreateEmpresaRequest request = buildCreateRequest("APCEF MG");

        try (MockedStatic<ReactiveSecurityContextHelper> security = mockTenantContext()) {
            when(empresaRepository.existsByTenantIdAndSigla(TENANT_ID, "APCEF MG")).thenReturn(Mono.just(true));

            StepVerifier.create(useCase.criar(request))
                    .expectError(EmpresaDuplicadaException.class)
                    .verify();
        }
    }

    @Test
    void excluir_bloqueiaQuandoPersonVinculado() {
        Empresa empresa = Empresa.builder().id("emp1").tenantId(TENANT_ID).sigla("APCEF MG").build();

        try (MockedStatic<ReactiveSecurityContextHelper> security = mockTenantContext()) {
            when(empresaRepository.findByTenantIdAndId(TENANT_ID, "emp1")).thenReturn(Mono.just(empresa));
            when(personRepository.countByEmpresaId("emp1")).thenReturn(Mono.just(2L));

            StepVerifier.create(useCase.excluir("emp1"))
                    .expectError(EmpresaEmUsoException.class)
                    .verify();
        }
    }

    @Test
    void excluir_removeQuandoSemVinculos() {
        Empresa empresa = Empresa.builder().id("emp1").tenantId(TENANT_ID).sigla("APCEF MG").build();

        try (MockedStatic<ReactiveSecurityContextHelper> security = mockTenantContext()) {
            when(empresaRepository.findByTenantIdAndId(TENANT_ID, "emp1")).thenReturn(Mono.just(empresa));
            when(personRepository.countByEmpresaId("emp1")).thenReturn(Mono.just(0L));
            when(empresaRepository.deleteById("emp1")).thenReturn(Mono.empty());

            StepVerifier.create(useCase.excluir("emp1"))
                    .verifyComplete();
        }
    }

    private MockedStatic<ReactiveSecurityContextHelper> mockTenantContext() {
        MockedStatic<ReactiveSecurityContextHelper> security = mockStatic(ReactiveSecurityContextHelper.class);
        security.when(ReactiveSecurityContextHelper::isSuperAdmin).thenReturn(Mono.just(false));
        security.when(ReactiveSecurityContextHelper::getTenantId).thenReturn(Mono.just(TENANT_ID));
        return security;
    }

    private CreateEmpresaRequest buildCreateRequest(String sigla) {
        CreateEmpresaRequest request = new CreateEmpresaRequest();
        request.setNome("Associação Teste");
        request.setSigla(sigla);
        EmpresaPercentualDTO percentual = new EmpresaPercentualDTO();
        percentual.setDescricao("Contratual");
        percentual.setPercentual(new BigDecimal("12"));
        percentual.setVigenciaInicio(LocalDate.of(2024, 1, 1));
        request.setPercentuais(List.of(percentual));
        return request;
    }
}
