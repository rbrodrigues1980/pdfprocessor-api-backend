package br.com.verticelabs.pdfprocessor.application.persons;

import br.com.verticelabs.pdfprocessor.application.security.EvaluatorAccessService;
import br.com.verticelabs.pdfprocessor.domain.model.PayrollDocument;
import br.com.verticelabs.pdfprocessor.domain.model.Person;
import br.com.verticelabs.pdfprocessor.domain.model.PersonStatus;
import br.com.verticelabs.pdfprocessor.infrastructure.security.ReactiveSecurityContextHelper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ListPersonsUseCase {

    static final ZoneId ZONE_BR = ZoneId.of("America/Sao_Paulo");

    private static final Sort DEFAULT_SORT = Sort.by(
            Sort.Order.desc("updatedAt"),
            Sort.Order.desc("createdAt")
    );

    private final ReactiveMongoTemplate mongoTemplate;
    private final EvaluatorAccessService evaluatorAccessService;

    public Mono<ListPersonsResult> execute(ListPersonsFilters filters, int page, int size) {
        ListPersonsFilters safeFilters = safeFilters(filters);

        return evaluatorAccessService.isEvaluator()
                .flatMap(isEvaluator -> {
                    if (Boolean.TRUE.equals(isEvaluator)) {
                        return evaluatorAccessService.currentAllowedPersonIds()
                                .flatMap(allowed -> listAllowlistedPersons(allowed, safeFilters, page, size));
                    }
                    return ReactiveSecurityContextHelper.isSuperAdmin()
                            .flatMap(isSuperAdmin -> {
                                if (isSuperAdmin) {
                                    return listAllPersons(safeFilters, page, size);
                                }
                                return ReactiveSecurityContextHelper.getTenantId()
                                        .flatMap(tenantId -> listPersonsByTenant(tenantId, safeFilters, page, size));
                            });
                });
    }

    /**
     * Busca todos os clientes que batem com os filtros, sem paginação.
     * Reutiliza a mesma query, ordenação e escopos de acesso da listagem.
     */
    public Mono<List<Person>> findAllMatching(ListPersonsFilters filters) {
        ListPersonsFilters safeFilters = safeFilters(filters);

        return evaluatorAccessService.isEvaluator()
                .flatMap(isEvaluator -> {
                    if (Boolean.TRUE.equals(isEvaluator)) {
                        return evaluatorAccessService.currentAllowedPersonIds()
                                .flatMap(allowed -> {
                                    if (allowed == null || allowed.isEmpty()) {
                                        return Mono.just(List.<Person>of());
                                    }
                                    Query findQuery = buildQuery(null, safeFilters)
                                            .addCriteria(Criteria.where("_id").in(new ArrayList<>(allowed)))
                                            .with(DEFAULT_SORT);
                                    return mongoTemplate.find(findQuery, Person.class).collectList();
                                });
                    }
                    return ReactiveSecurityContextHelper.isSuperAdmin()
                            .flatMap(isSuperAdmin -> {
                                if (Boolean.TRUE.equals(isSuperAdmin)) {
                                    Query findQuery = buildQuery(null, safeFilters).with(DEFAULT_SORT);
                                    return mongoTemplate.find(findQuery, Person.class).collectList();
                                }
                                return ReactiveSecurityContextHelper.getTenantId()
                                        .flatMap(tenantId -> {
                                            Query findQuery = buildQuery(tenantId, safeFilters).with(DEFAULT_SORT);
                                            return mongoTemplate.find(findQuery, Person.class).collectList();
                                        });
                            });
                });
    }

    private static ListPersonsFilters safeFilters(ListPersonsFilters filters) {
        return filters != null
                ? filters
                : new ListPersonsFilters(null, null, null, null, null, null, null, null);
    }

    private Mono<ListPersonsResult> listAllowlistedPersons(
            Set<String> allowedPersonIds, ListPersonsFilters filters, int page, int size) {
        if (allowedPersonIds == null || allowedPersonIds.isEmpty()) {
            return Mono.just(new ListPersonsResult(new ArrayList<>(), 0L, 0, page, size));
        }

        List<String> allowedIdsList = new ArrayList<>(allowedPersonIds);
        Query countQuery = buildQuery(null, filters)
                .addCriteria(Criteria.where("_id").in(allowedIdsList));
        Query findQuery = buildQuery(null, filters)
                .addCriteria(Criteria.where("_id").in(allowedIdsList));

        Pageable pageable = PageRequest.of(page, size, DEFAULT_SORT);
        findQuery.with(pageable);

        Mono<Long> countMono = Mono.from(mongoTemplate.count(countQuery, Person.class));
        Mono<List<Person>> personsMono = mongoTemplate.find(findQuery, Person.class).collectList();

        return Mono.zip(personsMono, countMono)
                .flatMap(tuple -> {
                    List<Person> persons = tuple.getT1();
                    Long total = tuple.getT2();
                    int totalPages = (int) Math.ceil((double) total / size);
                    return reconcileDocumentos(persons, null)
                            .map(reconciled -> new ListPersonsResult(reconciled, total, totalPages, page, size));
                });
    }

    private Mono<ListPersonsResult> listAllPersons(ListPersonsFilters filters, int page, int size) {
        Query countQuery = buildQuery(null, filters);
        Query findQuery = buildQuery(null, filters);

        Pageable pageable = PageRequest.of(page, size, DEFAULT_SORT);
        findQuery.with(pageable);

        Mono<Long> countMono = Mono.from(mongoTemplate.count(countQuery, Person.class));
        Mono<List<Person>> personsMono = mongoTemplate.find(findQuery, Person.class).collectList();

        return Mono.zip(personsMono, countMono)
                .flatMap(tuple -> {
                    List<Person> persons = tuple.getT1();
                    Long total = tuple.getT2();
                    int totalPages = (int) Math.ceil((double) total / size);

                    return reconcileDocumentos(persons, null)
                            .map(reconciled -> new ListPersonsResult(reconciled, total, totalPages, page, size));
                });
    }

    private Mono<ListPersonsResult> listPersonsByTenant(
            String tenantId, ListPersonsFilters filters, int page, int size) {
        Query countQuery = buildQuery(tenantId, filters);
        Query findQuery = buildQuery(tenantId, filters);

        Pageable pageable = PageRequest.of(page, size, DEFAULT_SORT);
        findQuery.with(pageable);

        Mono<Long> countMono = Mono.from(mongoTemplate.count(countQuery, Person.class));
        Mono<List<Person>> personsMono = mongoTemplate.find(findQuery, Person.class).collectList();

        return Mono.zip(personsMono, countMono)
                .flatMap(tuple -> {
                    List<Person> persons = tuple.getT1();
                    Long total = tuple.getT2();
                    int totalPages = (int) Math.ceil((double) total / size);

                    return reconcileDocumentos(persons, tenantId)
                            .map(reconciled -> new ListPersonsResult(reconciled, total, totalPages, page, size));
                });
    }

    /**
     * Reconcilia a lista de IDs de documentos de cada pessoa com a coleção real
     * de payroll_documents.
     */
    private Mono<List<Person>> reconcileDocumentos(List<Person> persons, String tenantId) {
        if (persons.isEmpty()) {
            return Mono.just(persons);
        }

        List<String> cpfs = persons.stream()
                .map(Person::getCpf)
                .filter(c -> c != null && !c.isEmpty())
                .distinct()
                .collect(Collectors.toList());

        if (cpfs.isEmpty()) {
            return Mono.just(persons);
        }

        Query docQuery = new Query(Criteria.where("cpf").in(cpfs));
        if (tenantId != null) {
            docQuery.addCriteria(Criteria.where("tenantId").is(tenantId));
        }

        return mongoTemplate.find(docQuery, PayrollDocument.class)
                .collectList()
                .map(docs -> {
                    Map<String, List<String>> docIdsByCpf = docs.stream()
                            .collect(Collectors.groupingBy(
                                    PayrollDocument::getCpf,
                                    Collectors.mapping(PayrollDocument::getId, Collectors.toList())));

                    for (Person person : persons) {
                        List<String> realIds = docIdsByCpf.getOrDefault(person.getCpf(), new ArrayList<>());
                        person.setDocumentos(new ArrayList<>(realIds));
                    }
                    return persons;
                });
    }

    Query buildQuery(String tenantId, ListPersonsFilters filters) {
        Query query = new Query();

        if (tenantId != null) {
            query.addCriteria(Criteria.where("tenantId").is(tenantId));
        }

        if (filters.nome() != null && !filters.nome().isEmpty()) {
            query.addCriteria(Criteria.where("nome").regex(filters.nome(), "i"));
        }

        if (filters.cpf() != null && !filters.cpf().isEmpty()) {
            query.addCriteria(Criteria.where("cpf").regex(filters.cpf(), "i"));
        }

        if (filters.matricula() != null && !filters.matricula().isEmpty()) {
            query.addCriteria(Criteria.where("matricula").regex(filters.matricula(), "i"));
        }

        if (filters.validado() != null) {
            if (Boolean.TRUE.equals(filters.validado())) {
                query.addCriteria(Criteria.where("validado").is(true));
            } else {
                // Evita $or: false/null/ausente ≡ não é true (não conflita com outros $or)
                query.addCriteria(Criteria.where("validado").ne(true));
            }
        }

        if (filters.empresaId() != null && !filters.empresaId().isBlank()) {
            query.addCriteria(Criteria.where("empresaId").is(filters.empresaId().trim()));
        }

        applyCreatedAtRange(query, filters.cadastroDe(), filters.cadastroAte());
        applyStatusFilter(query, filters.status());

        return query;
    }

    /**
     * Filtra por status. Documentos antigos sem o campo são tratados como {@link PersonStatus#EM_PROCESSAMENTO}.
     * Usa {@code $nin} em vez de {@code $or} para não conflitar com outros critérios {@code $or}/{@code null}
     * no mesmo {@link Query} (limitação do Spring Data MongoDB).
     */
    static void applyStatusFilter(Query query, PersonStatus status) {
        if (status == null) {
            return;
        }
        if (status == PersonStatus.EM_PROCESSAMENTO) {
            query.addCriteria(Criteria.where("status").nin(
                    PersonStatus.AGUARDANDO_DOC_COMPLEMENTAR,
                    PersonStatus.AGUARDANDO_DOC_EXERCICIO,
                    PersonStatus.FINALIZADO));
        } else {
            query.addCriteria(Criteria.where("status").is(status));
        }
    }

    /**
     * Aplica intervalo aberto/fechado sobre {@code createdAt}.
     * {@code cadastroAte} é inclusivo (limite exclusivo no início do dia seguinte).
     */
    static void applyCreatedAtRange(Query query, LocalDate cadastroDe, LocalDate cadastroAte) {
        Instant de = startOfDay(cadastroDe);
        Instant ateExclusivo = startOfNextDay(cadastroAte);

        if (de != null && ateExclusivo != null) {
            query.addCriteria(Criteria.where("createdAt").gte(de).lt(ateExclusivo));
        } else if (de != null) {
            query.addCriteria(Criteria.where("createdAt").gte(de));
        } else if (ateExclusivo != null) {
            query.addCriteria(Criteria.where("createdAt").lt(ateExclusivo));
        }
    }

    static Instant startOfDay(LocalDate date) {
        if (date == null) {
            return null;
        }
        return date.atStartOfDay(ZONE_BR).toInstant();
    }

    static Instant startOfNextDay(LocalDate date) {
        if (date == null) {
            return null;
        }
        return date.plusDays(1).atStartOfDay(ZONE_BR).toInstant();
    }

    public record ListPersonsResult(List<Person> persons, Long total, Integer totalPages, Integer page, Integer size) {}
}
