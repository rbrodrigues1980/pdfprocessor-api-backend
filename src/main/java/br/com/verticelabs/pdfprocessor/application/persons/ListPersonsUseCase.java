package br.com.verticelabs.pdfprocessor.application.persons;

import br.com.verticelabs.pdfprocessor.domain.model.Person;
import br.com.verticelabs.pdfprocessor.infrastructure.security.ReactiveSecurityContextHelper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ListPersonsUseCase {
    
    private final ReactiveMongoTemplate mongoTemplate;
    
    public Mono<ListPersonsResult> execute(String nomeFilter, String cpfFilter, String matriculaFilter, int page, int size) {
        return ReactiveSecurityContextHelper.isSuperAdmin()
                .flatMap(isSuperAdmin -> {
                    if (isSuperAdmin) {
                        // SUPER_ADMIN pode ver todas as pessoas (sem filtrar por tenantId)
                        return listAllPersons(nomeFilter, cpfFilter, matriculaFilter, page, size);
                    } else {
                        // Outros usuários só veem pessoas do seu tenant
                        return ReactiveSecurityContextHelper.getTenantId()
                                .flatMap(tenantId -> listPersonsByTenant(tenantId, nomeFilter, cpfFilter, matriculaFilter, page, size));
                    }
                });
    }
    
    private Mono<ListPersonsResult> listAllPersons(String nomeFilter, String cpfFilter, String matriculaFilter, int page, int size) {
        Query query = buildQuery(null, nomeFilter, cpfFilter, matriculaFilter);
        
        Pageable pageable = PageRequest.of(page, size);
        query.with(pageable);
        
        Mono<Long> countMono = Mono.from(mongoTemplate.count(query, Person.class));
        Mono<List<Person>> personsMono = mongoTemplate.find(query, Person.class).collectList();
        
        return Mono.zip(personsMono, countMono)
                .map(tuple -> {
                    List<Person> persons = tuple.getT1();
                    Long total = tuple.getT2();
                    int totalPages = (int) Math.ceil((double) total / size);
                    
                    return new ListPersonsResult(persons, total, totalPages, page, size);
                });
    }
    
    private Mono<ListPersonsResult> listPersonsByTenant(String tenantId, String nomeFilter, String cpfFilter, String matriculaFilter, int page, int size) {
        Query query = buildQuery(tenantId, nomeFilter, cpfFilter, matriculaFilter);
        
        Pageable pageable = PageRequest.of(page, size);
        query.with(pageable);
        
        Mono<Long> countMono = Mono.from(mongoTemplate.count(query, Person.class));
        Mono<List<Person>> personsMono = mongoTemplate.find(query, Person.class).collectList();
        
        return Mono.zip(personsMono, countMono)
                .map(tuple -> {
                    List<Person> persons = tuple.getT1();
                    Long total = tuple.getT2();
                    int totalPages = (int) Math.ceil((double) total / size);
                    
                    return new ListPersonsResult(persons, total, totalPages, page, size);
                });
    }
    
    private Query buildQuery(String tenantId, String nomeFilter, String cpfFilter, String matriculaFilter) {
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
        
        return query;
    }
    
    public record ListPersonsResult(List<Person> persons, Long total, Integer totalPages, Integer page, Integer size) {}
}

