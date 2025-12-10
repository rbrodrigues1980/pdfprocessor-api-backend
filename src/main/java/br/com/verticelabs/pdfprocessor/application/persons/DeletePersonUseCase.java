package br.com.verticelabs.pdfprocessor.application.persons;

import br.com.verticelabs.pdfprocessor.domain.exceptions.PersonNotFoundException;
import br.com.verticelabs.pdfprocessor.domain.repository.PersonRepository;
import br.com.verticelabs.pdfprocessor.infrastructure.security.ReactiveSecurityContextHelper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Slf4j
@Service
@RequiredArgsConstructor
public class DeletePersonUseCase {

    private final PersonRepository personRepository;

    public Mono<Void> execute(String personId) {
        log.info("Excluindo definitivamente pessoa: ID={}", personId);
        
        return ReactiveSecurityContextHelper.isSuperAdmin()
                .flatMap(isSuperAdmin -> {
                    if (Boolean.TRUE.equals(isSuperAdmin)) {
                        // SUPER_ADMIN pode excluir qualquer pessoa
                        return personRepository.findById(personId)
                                .switchIfEmpty(Mono.error(new PersonNotFoundException("ID: " + personId)))
                                .flatMap(person -> personRepository.deleteById(personId))
                                .doOnSuccess(v -> log.info("Pessoa excluída definitivamente: ID={}", personId));
                    } else {
                        // Outros usuários só podem excluir pessoas do seu tenant
                        return ReactiveSecurityContextHelper.getTenantId()
                                .flatMap(tenantId -> personRepository.findByTenantIdAndId(tenantId, personId)
                                        .switchIfEmpty(Mono.error(new PersonNotFoundException("ID: " + personId)))
                                        .flatMap(person -> personRepository.deleteById(personId))
                                        .doOnSuccess(v -> log.info("Pessoa excluída definitivamente: ID={}", personId)));
                    }
                })
                .then();
    }
}

