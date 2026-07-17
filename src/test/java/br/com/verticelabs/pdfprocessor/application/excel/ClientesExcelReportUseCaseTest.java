package br.com.verticelabs.pdfprocessor.application.excel;

import br.com.verticelabs.pdfprocessor.application.empresas.EmpresaHonorariosResolver;
import br.com.verticelabs.pdfprocessor.application.persons.ListPersonsFilters;
import br.com.verticelabs.pdfprocessor.application.persons.ListPersonsUseCase;
import br.com.verticelabs.pdfprocessor.domain.exceptions.NoEntriesFoundException;
import br.com.verticelabs.pdfprocessor.domain.model.Person;
import br.com.verticelabs.pdfprocessor.infrastructure.excel.ExcelResumoGeralHelper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("ClientesExcelReportUseCase")
class ClientesExcelReportUseCaseTest {

    @Mock
    private ListPersonsUseCase listPersonsUseCase;
    @Mock
    private ResumoGeralUseCase resumoGeralUseCase;
    @Mock
    private EmpresaHonorariosResolver empresaHonorariosResolver;
    @Mock
    private ClientesExcelReportService clientesExcelReportService;

    @InjectMocks
    private ClientesExcelReportUseCase useCase;

    @Test
    @DisplayName("cliente sem resumo geral entra com totais zerados")
    void clienteSemResumoEntraComZeros() {
        Person person = Person.builder()
                .id("p1")
                .cpf("12345678901")
                .nome("CLIENTE SEM DADOS")
                .build();

        EmpresaHonorariosResolver.HonorariosConfig honorarios =
                new EmpresaHonorariosResolver.HonorariosConfig(
                        new BigDecimal("0.12"),
                        new BigDecimal("12.00"),
                        "AEA/AL",
                        "Associação",
                        null);

        when(listPersonsUseCase.findAllMatching(any())).thenReturn(Mono.just(List.of(person)));
        when(empresaHonorariosResolver.resolve(person)).thenReturn(Mono.just(honorarios));
        when(resumoGeralUseCase.montarForAuthorizedPerson(person))
                .thenReturn(Mono.error(new NoEntriesFoundException(person.getCpf())));
        when(clientesExcelReportService.generate(anyList(), anyString()))
                .thenAnswer(inv -> {
                    @SuppressWarnings("unchecked")
                    List<ClienteExcelReportRow> rows = inv.getArgument(0);
                    assertEquals(1, rows.size());
                    ClienteExcelReportRow row = rows.get(0);
                    assertEquals("CLIENTE SEM DADOS", row.nome());
                    assertEquals("AEA/AL — Associação", row.entidade());
                    assertEquals(new BigDecimal("12.00"), row.percentualHonorarios());
                    assertEquals(0, row.totalPrincipalPgfn().compareTo(BigDecimal.ZERO));
                    assertEquals(0, row.totalPrincipalMaisCorrecao().compareTo(BigDecimal.ZERO));
                    return Mono.just(new byte[]{1, 2, 3});
                });

        StepVerifier.create(useCase.execute(new ListPersonsFilters(
                        null, null, null, null, "emp-1", null, null, null)))
                .assertNext(result -> {
                    assertEquals(3, result.getBytes().length);
                    assertEquals(true, result.getFilename().startsWith("relatorio_clientes_"));
                    assertEquals(true, result.getFilename().endsWith(".xlsx"));
                })
                .verifyComplete();

        verify(clientesExcelReportService).generate(anyList(), anyString());
    }

    @Test
    @DisplayName("rejeita geração sem nenhum filtro")
    void rejeitaSemFiltro() {
        StepVerifier.create(useCase.execute(new ListPersonsFilters(
                        null, null, null, null, null, null, null, null)))
                .expectErrorMatches(err ->
                        err instanceof IllegalArgumentException
                                && err.getMessage() != null
                                && err.getMessage().contains("pelo menos um filtro"))
                .verify();
    }

    @Test
    @DisplayName("cliente com resumo geral usa totais montados")
    void clienteComResumoUsaTotais() {
        Person person = Person.builder()
                .id("p2")
                .cpf("98765432100")
                .nome("CLIENTE COM DADOS")
                .build();

        EmpresaHonorariosResolver.HonorariosConfig honorarios =
                new EmpresaHonorariosResolver.HonorariosConfig(
                        new BigDecimal("0.10"),
                        new BigDecimal("10.00"),
                        "APCEF",
                        "APCEF SC",
                        null);

        ExcelResumoGeralHelper.TotaisResumoGeral totais =
                new ExcelResumoGeralHelper.TotaisResumoGeral(
                        new BigDecimal("100.00"),
                        new BigDecimal("20.00"),
                        new BigDecimal("120.00"),
                        new BigDecimal("12.00"),
                        new BigDecimal("108.00"));

        ResumoGeralMontagemResult montagem = new ResumoGeralMontagemResult(
                List.of(), honorarios, totais, null, null, null, null, null, null);

        when(listPersonsUseCase.findAllMatching(any())).thenReturn(Mono.just(List.of(person)));
        when(empresaHonorariosResolver.resolve(person)).thenReturn(Mono.just(honorarios));
        when(resumoGeralUseCase.montarForAuthorizedPerson(person))
                .thenReturn(Mono.just(new ResumoGeralUseCase.MontagemBundle(person, montagem)));
        when(clientesExcelReportService.generate(anyList(), anyString()))
                .thenAnswer(inv -> {
                    @SuppressWarnings("unchecked")
                    List<ClienteExcelReportRow> rows = inv.getArgument(0);
                    assertEquals(new BigDecimal("100.00"), rows.get(0).totalPrincipalPgfn());
                    assertEquals(new BigDecimal("120.00"), rows.get(0).totalPrincipalMaisCorrecao());
                    return Mono.just(new byte[]{9});
                });

        StepVerifier.create(useCase.execute(new ListPersonsFilters(
                        "CLIENTE", null, null, null, null, null, null, null)))
                .assertNext(result -> assertEquals(1, result.getBytes().length))
                .verifyComplete();
    }

    @Test
    @DisplayName("formatEntidade usa legado quando empresa ausente")
    void formatEntidadeUsaLegado() {
        Person person = Person.builder().entidade("LEGADO").build();
        EmpresaHonorariosResolver.HonorariosConfig honorarios =
                new EmpresaHonorariosResolver.HonorariosConfig(
                        BigDecimal.ZERO, BigDecimal.ZERO, null, null, null);

        assertEquals("LEGADO", ClientesExcelReportUseCase.formatEntidade(person, honorarios));
    }
}
