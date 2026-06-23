package br.com.verticelabs.pdfprocessor.application.empresas;

import br.com.verticelabs.pdfprocessor.domain.exceptions.PersonEmpresaVinculoInvalidoException;
import br.com.verticelabs.pdfprocessor.domain.model.Empresa;
import br.com.verticelabs.pdfprocessor.domain.model.EmpresaPercentual;
import br.com.verticelabs.pdfprocessor.domain.model.Person;
import br.com.verticelabs.pdfprocessor.domain.repository.EmpresaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PersonEmpresaValidationTest {

    private static final String TENANT_ID = "tenant1";

    @Mock
    private EmpresaRepository empresaRepository;

    private PersonEmpresaVinculoService service;

    @BeforeEach
    void setUp() {
        service = new PersonEmpresaVinculoService(empresaRepository, new EmpresaPercentualHelper());
    }

    @Test
    void validate_limpaVinculoQuandoAmbosAusentes() {
        Person person = new Person();
        person.setEmpresaId("old");
        person.setPercentualHonorarioId("old-p");

        StepVerifier.create(service.validateAndApply(TENANT_ID, null, null, person))
                .verifyComplete();

        org.junit.jupiter.api.Assertions.assertNull(person.getEmpresaId());
        org.junit.jupiter.api.Assertions.assertNull(person.getPercentualHonorarioId());
    }

    @Test
    void validate_rejeitaEmpresaSemPercentual() {
        Person person = new Person();

        StepVerifier.create(service.validateAndApply(TENANT_ID, "emp1", null, person))
                .expectError(PersonEmpresaVinculoInvalidoException.class)
                .verify();
    }

    @Test
    void validate_rejeitaPercentualForaDaVigencia() {
        Person person = new Person();
        Empresa empresa = Empresa.builder()
                .id("emp1")
                .tenantId(TENANT_ID)
                .percentuais(List.of(EmpresaPercentual.builder()
                        .id("p1")
                        .descricao("Expirado")
                        .percentual(new BigDecimal("12"))
                        .vigenciaInicio(LocalDate.of(2020, 1, 1))
                        .vigenciaFim(LocalDate.of(2021, 12, 31))
                        .ativo(true)
                        .build()))
                .build();

        when(empresaRepository.findByTenantIdAndId(TENANT_ID, "emp1")).thenReturn(Mono.just(empresa));

        StepVerifier.create(service.validateAndApply(TENANT_ID, "emp1", "p1", person))
                .expectError(PersonEmpresaVinculoInvalidoException.class)
                .verify();
    }

    @Test
    void validate_aplicaVinculoQuandoPercentualVigente() {
        Person person = new Person();
        Empresa empresa = Empresa.builder()
                .id("emp1")
                .tenantId(TENANT_ID)
                .percentuais(List.of(EmpresaPercentual.builder()
                        .id("p1")
                        .descricao("Contratual")
                        .percentual(new BigDecimal("15"))
                        .vigenciaInicio(LocalDate.of(2024, 1, 1))
                        .vigenciaFim(null)
                        .ativo(true)
                        .build()))
                .build();

        when(empresaRepository.findByTenantIdAndId(TENANT_ID, "emp1")).thenReturn(Mono.just(empresa));

        StepVerifier.create(service.validateAndApply(TENANT_ID, "emp1", "p1", person))
                .verifyComplete();

        org.junit.jupiter.api.Assertions.assertEquals("emp1", person.getEmpresaId());
        org.junit.jupiter.api.Assertions.assertEquals("p1", person.getPercentualHonorarioId());
    }
}
