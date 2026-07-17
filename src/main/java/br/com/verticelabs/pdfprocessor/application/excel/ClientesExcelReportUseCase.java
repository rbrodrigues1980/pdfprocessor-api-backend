package br.com.verticelabs.pdfprocessor.application.excel;

import br.com.verticelabs.pdfprocessor.application.empresas.EmpresaHonorariosResolver;
import br.com.verticelabs.pdfprocessor.application.persons.ListPersonsFilters;
import br.com.verticelabs.pdfprocessor.application.persons.ListPersonsUseCase;
import br.com.verticelabs.pdfprocessor.domain.exceptions.NoEntriesFoundException;
import br.com.verticelabs.pdfprocessor.domain.model.Person;
import br.com.verticelabs.pdfprocessor.domain.model.PersonStatus;
import br.com.verticelabs.pdfprocessor.infrastructure.excel.ExcelResumoGeralHelper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Slf4j
@Service
@RequiredArgsConstructor
public class ClientesExcelReportUseCase {

    private static final int CONCURRENCY = 3;
    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(2);
    private static final DateTimeFormatter FILENAME_TS =
            DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");

    private final ListPersonsUseCase listPersonsUseCase;
    private final ResumoGeralUseCase resumoGeralUseCase;
    private final EmpresaHonorariosResolver empresaHonorariosResolver;
    private final ClientesExcelReportService clientesExcelReportService;

    public Mono<ExcelExportResult> execute(ListPersonsFilters filters) {
        ListPersonsFilters safeFilters = filters != null
                ? filters
                : new ListPersonsFilters(null, null, null, null, null, null, null, null);

        if (!safeFilters.hasAnyFilter()) {
            return Mono.error(new IllegalArgumentException(
                    "É necessário aplicar pelo menos um filtro para gerar o relatório de clientes."));
        }

        log.info("Gerando relatório Excel de clientes com filtros={}", safeFilters);

        return listPersonsUseCase.findAllMatching(safeFilters)
                .flatMapMany(Flux::fromIterable)
                .flatMap(this::buildRow, CONCURRENCY)
                .collectList()
                .flatMap(rows -> {
                    String filename = "relatorio_clientes_" + LocalDateTime.now().format(FILENAME_TS) + ".xlsx";
                    log.info("Relatório Excel: {} cliente(s), arquivo={}", rows.size(), filename);
                    return clientesExcelReportService.generate(rows, filename)
                            .map(bytes -> new ExcelExportResult(bytes, filename));
                });
    }

    private Mono<ClienteExcelReportRow> buildRow(Person person) {
        return empresaHonorariosResolver.resolve(person)
                .flatMap(honorarios -> resumoGeralUseCase.montarForAuthorizedPerson(person)
                        .map(bundle -> toRow(person, honorarios, bundle.montagem().totais()))
                        .switchIfEmpty(Mono.fromSupplier(() -> toRowZeros(person, honorarios)))
                        .onErrorResume(NoEntriesFoundException.class,
                                e -> Mono.just(toRowZeros(person, honorarios)))
                        .onErrorResume(err -> {
                            // Consolidação sem entries chega como IllegalArgumentException em alguns fluxos
                            if (err instanceof NoEntriesFoundException
                                    || (err.getMessage() != null
                                    && err.getMessage().toLowerCase().contains("nenhuma entrada"))) {
                                return Mono.just(toRowZeros(person, honorarios));
                            }
                            log.error("Falha ao montar Resumo Geral do cliente {}: {}",
                                    person.getId(), err.getMessage());
                            return Mono.error(err);
                        }));
    }

    private ClienteExcelReportRow toRow(
            Person person,
            EmpresaHonorariosResolver.HonorariosConfig honorarios,
            ExcelResumoGeralHelper.TotaisResumoGeral totais) {
        BigDecimal principal = totais != null && totais.totalPrincipal() != null
                ? totais.totalPrincipal()
                : ZERO;
        BigDecimal principalMaisCorrecao = totais != null && totais.totalPrincipalMaisCorrecao() != null
                ? totais.totalPrincipalMaisCorrecao()
                : ZERO;
        return new ClienteExcelReportRow(
                person.getNome(),
                person.getCpf(),
                formatEntidade(person, honorarios),
                PersonStatus.fromNullable(person.getStatus()).getLabel(),
                honorarios.percentualExibicao(),
                principal,
                principalMaisCorrecao);
    }

    private ClienteExcelReportRow toRowZeros(
            Person person, EmpresaHonorariosResolver.HonorariosConfig honorarios) {
        return new ClienteExcelReportRow(
                person.getNome(),
                person.getCpf(),
                formatEntidade(person, honorarios),
                PersonStatus.fromNullable(person.getStatus()).getLabel(),
                honorarios.percentualExibicao(),
                ZERO,
                ZERO);
    }

    static String formatEntidade(Person person, EmpresaHonorariosResolver.HonorariosConfig honorarios) {
        String sigla = honorarios != null ? honorarios.empresaSigla() : null;
        String nome = honorarios != null ? honorarios.empresaNome() : null;
        if (sigla != null && !sigla.isBlank() && nome != null && !nome.isBlank()) {
            return sigla + " — " + nome;
        }
        if (sigla != null && !sigla.isBlank()) {
            return sigla;
        }
        if (nome != null && !nome.isBlank()) {
            return nome;
        }
        if (person.getEntidade() != null && !person.getEntidade().isBlank()) {
            return person.getEntidade();
        }
        return "";
    }
}
