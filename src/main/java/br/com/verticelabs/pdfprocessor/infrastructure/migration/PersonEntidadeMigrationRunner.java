package br.com.verticelabs.pdfprocessor.infrastructure.migration;

import br.com.verticelabs.pdfprocessor.domain.model.Person;
import br.com.verticelabs.pdfprocessor.domain.repository.EmpresaRepository;
import br.com.verticelabs.pdfprocessor.domain.repository.PersonRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.time.Instant;

/**
 * Migração best-effort: vincula {@code Person.entidade} (texto) a {@code Person.empresaId}
 * quando encontra empresa com mesma sigla ou nome no tenant.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "migration.person-entidade.enabled", havingValue = "true")
public class PersonEntidadeMigrationRunner {

    private final PersonRepository personRepository;
    private final EmpresaRepository empresaRepository;
    private final ReactiveMongoTemplate mongoTemplate;

    @EventListener(ApplicationReadyEvent.class)
    public void migrate() {
        Query query = new Query(new Criteria().andOperator(
                Criteria.where("entidade").exists(true),
                Criteria.where("entidade").ne(null),
                Criteria.where("entidade").ne("")));

        mongoTemplate.find(query, Person.class)
                .flatMap(this::migratePerson)
                .then()
                .subscribe(
                        unused -> log.info("Migração person.entidade concluída"),
                        error -> log.error("Erro na migração person.entidade", error));
    }

    private Mono<Person> migratePerson(Person person) {
        String entidade = person.getEntidade();
        if (entidade == null || entidade.isBlank()) {
            return Mono.empty();
        }
        if (person.getEmpresaId() != null && !person.getEmpresaId().isBlank()) {
            person.setEntidade(null);
            return personRepository.save(person);
        }

        String tenantId = person.getTenantId();
        return empresaRepository.findByTenantIdAndSiglaIgnoreCase(tenantId, entidade.trim())
                .switchIfEmpty(empresaRepository.findByTenantIdAndNomeIgnoreCase(tenantId, entidade.trim()))
                .flatMap(empresa -> {
                    person.setEmpresaId(empresa.getId());
                    person.setEntidade(null);
                    person.setUpdatedAt(Instant.now());
                    log.info("Migrado CPF {} entidade '{}' -> empresaId {}", person.getCpf(), entidade, empresa.getId());
                    return personRepository.save(person);
                })
                .switchIfEmpty(Mono.fromRunnable(() ->
                        log.warn("Entidade '{}' do CPF {} não encontrada em empresas (tenant {})",
                                entidade, person.getCpf(), tenantId)));
    }
}
