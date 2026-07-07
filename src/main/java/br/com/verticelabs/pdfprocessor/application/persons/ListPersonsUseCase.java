package br.com.verticelabs.pdfprocessor.application.persons;

import br.com.verticelabs.pdfprocessor.application.security.EvaluatorAccessService;
import br.com.verticelabs.pdfprocessor.domain.model.Person;
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

import br.com.verticelabs.pdfprocessor.domain.model.PayrollDocument;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ListPersonsUseCase {

    private static final Sort DEFAULT_SORT = Sort.by(
            Sort.Order.desc("updatedAt"),
            Sort.Order.desc("createdAt")
    );

    private final ReactiveMongoTemplate mongoTemplate;
    private final EvaluatorAccessService evaluatorAccessService;
    
    public Mono<ListPersonsResult> execute(String nomeFilter, String cpfFilter, String matriculaFilter, Boolean validadoFilter, int page, int size) {
        return evaluatorAccessService.isEvaluator()
                .flatMap(isEvaluator -> {
                    if (Boolean.TRUE.equals(isEvaluator)) {
                        // EVALUATOR vê apenas os clientes atribuídos (allowlist), ignorando tenant
                        return evaluatorAccessService.currentAllowedPersonIds()
                                .flatMap(allowed -> listAllowlistedPersons(allowed, nomeFilter, cpfFilter, matriculaFilter, validadoFilter, page, size));
                    }
                    return ReactiveSecurityContextHelper.isSuperAdmin()
                            .flatMap(isSuperAdmin -> {
                                if (isSuperAdmin) {
                                    // SUPER_ADMIN pode ver todas as pessoas (sem filtrar por tenantId)
                                    return listAllPersons(nomeFilter, cpfFilter, matriculaFilter, validadoFilter, page, size);
                                } else {
                                    // Outros usuários só veem pessoas do seu tenant
                                    return ReactiveSecurityContextHelper.getTenantId()
                                            .flatMap(tenantId -> listPersonsByTenant(tenantId, nomeFilter, cpfFilter, matriculaFilter, validadoFilter, page, size));
                                }
                            });
                });
    }

    private Mono<ListPersonsResult> listAllowlistedPersons(Set<String> allowedPersonIds, String nomeFilter, String cpfFilter, String matriculaFilter, Boolean validadoFilter, int page, int size) {
        if (allowedPersonIds == null || allowedPersonIds.isEmpty()) {
            return Mono.just(new ListPersonsResult(new ArrayList<>(), 0L, 0, page, size));
        }

        List<String> allowedIdsList = new ArrayList<>(allowedPersonIds);
        Query countQuery = buildQuery(null, nomeFilter, cpfFilter, matriculaFilter, validadoFilter)
                .addCriteria(Criteria.where("_id").in(allowedIdsList));
        Query findQuery = buildQuery(null, nomeFilter, cpfFilter, matriculaFilter, validadoFilter)
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
    
    private Mono<ListPersonsResult> listAllPersons(String nomeFilter, String cpfFilter, String matriculaFilter, Boolean validadoFilter, int page, int size) {
        Query countQuery = buildQuery(null, nomeFilter, cpfFilter, matriculaFilter, validadoFilter);
        Query findQuery = buildQuery(null, nomeFilter, cpfFilter, matriculaFilter, validadoFilter);
        
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

    private Mono<ListPersonsResult> listPersonsByTenant(String tenantId, String nomeFilter, String cpfFilter, String matriculaFilter, Boolean validadoFilter, int page, int size) {
        Query countQuery = buildQuery(tenantId, nomeFilter, cpfFilter, matriculaFilter, validadoFilter);
        Query findQuery = buildQuery(tenantId, nomeFilter, cpfFilter, matriculaFilter, validadoFilter);
        
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
     *
     * Motivo: o array Person.documentos é denormalizado e atualizado no upload via
     * read-modify-write não atômico. Em uploads concorrentes (bulk), o "last write
     * wins" pode perder IDs, deixando a contagem defasada (ex.: mostrar 9 quando há
     * 10 documentos). Aqui recalculamos a partir da fonte de verdade (a coleção de
     * documentos), corrigindo a exibição e auto-curando dados antigos.
     *
     * @param tenantId quando não-nulo, restringe a busca ao tenant; null para SUPER_ADMIN.
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

    private Query buildQuery(String tenantId, String nomeFilter, String cpfFilter, String matriculaFilter, Boolean validadoFilter) {
        Query query = new Query();
        
        // Filtrar por tenantId apenas se fornecido (null para SUPER_ADMIN ver todas)
        if (tenantId != null) {
            query.addCriteria(Criteria.where("tenantId").is(tenantId));
        }
        
        if (nomeFilter != null && !nomeFilter.isEmpty()) {
            query.addCriteria(Criteria.where("nome").regex(nomeFilter, "i"));
        }
        
        if (cpfFilter != null && !cpfFilter.isEmpty()) {
            query.addCriteria(Criteria.where("cpf").regex(cpfFilter, "i"));
        }
        
        if (matriculaFilter != null && !matriculaFilter.isEmpty()) {
            query.addCriteria(Criteria.where("matricula").regex(matriculaFilter, "i"));
        }

        if (validadoFilter != null) {
            if (Boolean.TRUE.equals(validadoFilter)) {
                query.addCriteria(Criteria.where("validado").is(true));
            } else {
                query.addCriteria(new Criteria().orOperator(
                        Criteria.where("validado").is(false),
                        Criteria.where("validado").is(null),
                        Criteria.where("validado").exists(false)));
            }
        }
        
        return query;
    }
    
    public record ListPersonsResult(List<Person> persons, Long total, Integer totalPages, Integer page, Integer size) {}
}

