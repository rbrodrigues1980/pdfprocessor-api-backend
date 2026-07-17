package br.com.verticelabs.pdfprocessor.application.persons;

import br.com.verticelabs.pdfprocessor.domain.exceptions.PersonNotFoundException;
import br.com.verticelabs.pdfprocessor.domain.model.Person;
import br.com.verticelabs.pdfprocessor.domain.model.PersonStatus;
import br.com.verticelabs.pdfprocessor.domain.repository.PersonRepository;
import br.com.verticelabs.pdfprocessor.infrastructure.security.ReactiveSecurityContextHelper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Instant;

@Slf4j
@Service
@RequiredArgsConstructor
public class UpdatePersonStatusUseCase {

    private final PersonRepository personRepository;

    public Mono<Person> execute(String personId, PersonStatus status) {
        log.info("Atualizando status do cliente: ID={}, status={}", personId, status);

        return ReactiveSecurityContextHelper.isSuperAdmin()
                .flatMap(isSuperAdmin -> {
                    Mono<Person> personMono = Boolean.TRUE.equals(isSuperAdmin)
                            ? personRepository.findById(personId)
                            : ReactiveSecurityContextHelper.getTenantId()
                                    .flatMap(tenantId -> personRepository.findByTenantIdAndId(tenantId, personId));

                    return personMono
                            .switchIfEmpty(Mono.error(new PersonNotFoundException("ID: " + personId)))
                            .flatMap(person -> {
                                person.setStatus(status);
                                person.setUpdatedAt(Instant.now());
                                return personRepository.save(person)
                                        .doOnSuccess(p -> log.info(
                                                "Status do cliente atualizado: ID={}, status={}",
                                                p.getId(), p.getStatus()));
                            });
                });
    }
}
