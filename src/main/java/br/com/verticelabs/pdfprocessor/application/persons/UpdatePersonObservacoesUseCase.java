package br.com.verticelabs.pdfprocessor.application.persons;

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
public class UpdatePersonObservacoesUseCase {

    private final PersonRepository personRepository;

    public Mono<Person> execute(String personId, String observacoes) {
        String normalized = (observacoes != null && !observacoes.isBlank()) ? observacoes.trim() : null;
        log.info("Atualizando observações do cliente: ID={}, temTexto={}", personId, normalized != null);

        return ReactiveSecurityContextHelper.isSuperAdmin()
                .flatMap(isSuperAdmin -> {
                    Mono<Person> personMono = Boolean.TRUE.equals(isSuperAdmin)
                            ? personRepository.findById(personId)
                            : ReactiveSecurityContextHelper.getTenantId()
                                    .flatMap(tenantId -> personRepository.findByTenantIdAndId(tenantId, personId));

                    return personMono
                            .switchIfEmpty(Mono.error(new PersonNotFoundException("ID: " + personId)))
                            .flatMap(person -> {
                                person.setObservacoes(normalized);
                                person.setUpdatedAt(Instant.now());
                                return personRepository.save(person)
                                        .doOnSuccess(p -> log.info(
                                                "Observações do cliente atualizadas: ID={}", p.getId()));
                            });
                });
    }
}
