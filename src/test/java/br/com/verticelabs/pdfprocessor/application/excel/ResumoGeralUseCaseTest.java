package br.com.verticelabs.pdfprocessor.application.excel;

import br.com.verticelabs.pdfprocessor.application.consolidation.ConsolidationUseCase;
import br.com.verticelabs.pdfprocessor.application.empresas.EmpresaHonorariosResolver;
import br.com.verticelabs.pdfprocessor.domain.exceptions.NoEntriesFoundException;
import br.com.verticelabs.pdfprocessor.domain.model.DocumentType;
import br.com.verticelabs.pdfprocessor.domain.model.IrpfDeclaracaoData;
import br.com.verticelabs.pdfprocessor.domain.model.PayrollDocument;
import br.com.verticelabs.pdfprocessor.domain.model.Person;
import br.com.verticelabs.pdfprocessor.domain.repository.PayrollDocumentRepository;
import br.com.verticelabs.pdfprocessor.domain.repository.PersonRepository;
import br.com.verticelabs.pdfprocessor.infrastructure.excel.ExcelResumoGeralHelper;
import br.com.verticelabs.pdfprocessor.infrastructure.excel.ExcelResumoGeralLinhaDTO;
import br.com.verticelabs.pdfprocessor.infrastructure.security.ReactiveSecurityContextHelper;
import br.com.verticelabs.pdfprocessor.interfaces.consolidation.dto.ConsolidatedResponse;
import br.com.verticelabs.pdfprocessor.interfaces.consolidation.dto.ConsolidationRow;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ResumoGeralUseCaseTest {

    @Mock
    private PersonRepository personRepository;
    @Mock
    private ConsolidationUseCase consolidationUseCase;
    @Mock
    private PayrollDocumentRepository documentRepository;
    @Mock
    private ResumoGeralAssemblyService resumoGeralAssemblyService;
    @Mock
    private ResumoGeralResponseMapper resumoGeralResponseMapper;

    @InjectMocks
    private ResumoGeralUseCase resumoGeralUseCase;

    private Person person;

    @BeforeEach
    void setUp() {
        person = Person.builder()
                .id("p1")
                .tenantId("t1")
                .cpf("44274378934")
                .nome("MARCIA REGINA")
                .build();
    }

    private MockedStatic<ReactiveSecurityContextHelper> mockSuperAdmin() {
        MockedStatic<ReactiveSecurityContextHelper> security = mockStatic(ReactiveSecurityContextHelper.class);
        security.when(ReactiveSecurityContextHelper::isSuperAdmin).thenReturn(Mono.just(true));
        return security;
    }

    @Test
    void getByPersonId_retornaResumoQuandoHaLinhas() {
        ConsolidatedResponse consolidated = ConsolidatedResponse.builder()
                .cpf(person.getCpf())
                .nome(person.getNome())
                .anos(Set.of("2018"))
                .rubricas(List.of(ConsolidationRow.builder()
                        .codigo("001")
                        .valores(Map.of("2018-01", BigDecimal.TEN))
                        .build()))
                .build();

        ExcelResumoGeralLinhaDTO linha = ExcelResumoGeralLinhaDTO.builder()
                .anoCalendario("2018")
                .valorDeclaracao(new BigDecimal("1503.80"))
                .valorSimulacao(new BigDecimal("4044.88"))
                .principal(new BigDecimal("5548.68"))
                .observacao(ExcelResumoGeralHelper.OBS_IMPACTO)
                .build();

        ResumoGeralMontagemResult montagem = new ResumoGeralMontagemResult(
                List.of(linha),
                new EmpresaHonorariosResolver.HonorariosConfig(
                        new BigDecimal("0.12"), new BigDecimal("12.00"), "APCEF", "APCEF SC", null),
                new ExcelResumoGeralHelper.TotaisResumoGeral(
                        new BigDecimal("5548.68"), BigDecimal.ZERO, new BigDecimal("5548.68"),
                        new BigDecimal("665.84"), new BigDecimal("4882.84")),
                Map.of("2018", IrpfDeclaracaoData.builder().anoCalendario("2018").build()),
                Map.of("2018", BigDecimal.TEN),
                Map.of(), Map.of(),
                LocalDate.now(), LocalDateTime.now());

        var response = br.com.verticelabs.pdfprocessor.interfaces.excel.dto.ResumoGeralResponse.builder()
                .nome(person.getNome())
                .cpf(person.getCpf())
                .build();

        when(personRepository.findById("p1")).thenReturn(Mono.just(person));
        when(consolidationUseCase.consolidate(eq(person.getCpf()), eq(null), eq(null)))
                .thenReturn(Mono.just(consolidated));
        when(documentRepository.findByTenantIdAndCpf("t1", person.getCpf()))
                .thenReturn(Flux.just(PayrollDocument.builder()
                        .tipo(DocumentType.INCOME_TAX)
                        .irpfData(IrpfDeclaracaoData.builder().anoCalendario("2018").build())
                        .build()));
        when(resumoGeralAssemblyService.montar(eq(person), eq(consolidated), any()))
                .thenReturn(Mono.just(montagem));
        when(resumoGeralResponseMapper.toResponse(person, montagem)).thenReturn(response);

        try (MockedStatic<ReactiveSecurityContextHelper> security = mockSuperAdmin()) {
            StepVerifier.create(resumoGeralUseCase.getByPersonId("p1"))
                    .expectNext(response)
                    .verifyComplete();
        }
    }

    @Test
    void getByPersonId_retornaVazioQuandoSemLinhas() {
        ConsolidatedResponse consolidated = ConsolidatedResponse.builder()
                .cpf(person.getCpf())
                .rubricas(List.of(ConsolidationRow.builder().codigo("001").valores(new HashMap<>()).build()))
                .build();

        ResumoGeralMontagemResult montagemVazia = new ResumoGeralMontagemResult(
                List.of(),
                new EmpresaHonorariosResolver.HonorariosConfig(
                        new BigDecimal("0.12"), new BigDecimal("12.00"), null, null, null),
                new ExcelResumoGeralHelper.TotaisResumoGeral(
                        BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO),
                Map.of(), Map.of(), Map.of(), Map.of(),
                LocalDate.now(), LocalDateTime.now());

        when(personRepository.findById("p1")).thenReturn(Mono.just(person));
        when(consolidationUseCase.consolidate(eq(person.getCpf()), eq(null), eq(null)))
                .thenReturn(Mono.just(consolidated));
        when(documentRepository.findByTenantIdAndCpf("t1", person.getCpf())).thenReturn(Flux.empty());
        when(resumoGeralAssemblyService.montar(eq(person), eq(consolidated), any()))
                .thenReturn(Mono.just(montagemVazia));

        try (MockedStatic<ReactiveSecurityContextHelper> security = mockSuperAdmin()) {
            StepVerifier.create(resumoGeralUseCase.getByPersonId("p1"))
                    .verifyComplete();
        }
    }

    @Test
    void getByPersonId_erroQuandoSemRubricas() {
        when(personRepository.findById("p1")).thenReturn(Mono.just(person));
        when(consolidationUseCase.consolidate(eq(person.getCpf()), eq(null), eq(null)))
                .thenReturn(Mono.just(ConsolidatedResponse.builder()
                        .cpf(person.getCpf())
                        .rubricas(List.of())
                        .build()));

        try (MockedStatic<ReactiveSecurityContextHelper> security = mockSuperAdmin()) {
            StepVerifier.create(resumoGeralUseCase.getByPersonId("p1"))
                    .expectError(NoEntriesFoundException.class)
                    .verify();
        }
    }
}
