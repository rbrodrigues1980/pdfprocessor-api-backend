package br.com.verticelabs.pdfprocessor.application.repasse;

import br.com.verticelabs.pdfprocessor.domain.exceptions.ForbiddenOperationException;
import br.com.verticelabs.pdfprocessor.domain.model.*;
import br.com.verticelabs.pdfprocessor.domain.repository.DeveloperRepasseRepository;
import br.com.verticelabs.pdfprocessor.domain.repository.PersonRepository;
import br.com.verticelabs.pdfprocessor.infrastructure.security.ReactiveSecurityContextHelper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Slf4j
@Service
@RequiredArgsConstructor
public class SyncDeveloperRepasseUseCase {

    private final PersonRepository personRepository;
    private final DeveloperRepasseRepository repasseRepository;
    private final CreateDeveloperRepasseService createDeveloperRepasseService;

    public Mono<SyncResult> execute() {
        return requireSuperAdmin()
                .thenMany(personRepository.findByValidadoTrue())
                .filter(person -> Boolean.TRUE.equals(person.getValidado()))
                .flatMap(person -> repasseRepository.existsByPersonId(person.getId())
                        .flatMap(exists -> Boolean.TRUE.equals(exists)
                                ? Mono.empty()
                                : createDeveloperRepasseService.createForValidatedPerson(person)))
                .count()
                .map(SyncResult::new);
    }

    private Mono<Void> requireSuperAdmin() {
        return ReactiveSecurityContextHelper.isSuperAdmin()
                .flatMap(isSuperAdmin -> Boolean.TRUE.equals(isSuperAdmin)
                        ? Mono.empty()
                        : Mono.error(new ForbiddenOperationException("Apenas SUPER_ADMIN pode gerenciar repasses")));
    }

    public record SyncResult(long criados) {}
}
