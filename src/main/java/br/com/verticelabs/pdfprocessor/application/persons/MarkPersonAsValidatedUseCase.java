package br.com.verticelabs.pdfprocessor.application.persons;

import br.com.verticelabs.pdfprocessor.application.repasse.CreateDeveloperRepasseService;
import br.com.verticelabs.pdfprocessor.domain.exceptions.PersonAlreadyValidatedException;
import br.com.verticelabs.pdfprocessor.domain.exceptions.PersonNotFoundException;
import br.com.verticelabs.pdfprocessor.domain.model.Person;
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
public class MarkPersonAsValidatedUseCase {

    private final PersonRepository personRepository;
    private final CreateDeveloperRepasseService createDeveloperRepasseService;

    public Mono<Person> execute(String personId) {
        log.info("Marcando cliente como validado: ID={}", personId);

        return ReactiveSecurityContextHelper.isSuperAdmin()
                .flatMap(isSuperAdmin -> {
                    Mono<Person> personMono = Boolean.TRUE.equals(isSuperAdmin)
                            ? personRepository.findById(personId)
                            : ReactiveSecurityContextHelper.getTenantId()
                                    .flatMap(tenantId -> personRepository.findByTenantIdAndId(tenantId, personId));

                    return personMono
                            .switchIfEmpty(Mono.error(new PersonNotFoundException("ID: " + personId)))
                            .flatMap(person -> {
                                if (Boolean.TRUE.equals(person.getValidado())) {
                                    return Mono.error(new PersonAlreadyValidatedException(personId));
                                }

                                Instant now = Instant.now();
                                person.setValidado(true);
                                person.setValidadoEm(now);
                                person.setUpdatedAt(now);

                                return personRepository.save(person)
                                        .flatMap(saved -> createDeveloperRepasseService.createForValidatedPerson(saved)
                                                .thenReturn(saved))
                                        .doOnSuccess(p -> log.info("Cliente marcado como validado: ID={}", p.getId()));
                            });
                });
    }
}
