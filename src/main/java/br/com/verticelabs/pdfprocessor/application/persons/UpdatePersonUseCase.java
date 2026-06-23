package br.com.verticelabs.pdfprocessor.application.persons;

import br.com.verticelabs.pdfprocessor.application.empresas.PersonEmpresaVinculoService;
import br.com.verticelabs.pdfprocessor.domain.exceptions.PersonNotFoundException;
import br.com.verticelabs.pdfprocessor.domain.model.Person;
import br.com.verticelabs.pdfprocessor.domain.repository.PersonRepository;
import br.com.verticelabs.pdfprocessor.infrastructure.security.ReactiveSecurityContextHelper;
import br.com.verticelabs.pdfprocessor.interfaces.persons.dto.UpdatePersonRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Instant;

@Slf4j
@Service
@RequiredArgsConstructor
public class UpdatePersonUseCase {

    private final PersonRepository personRepository;
    private final PersonEmpresaVinculoService personEmpresaVinculoService;

    public Mono<Person> execute(String personId, UpdatePersonRequest request) {
        log.info("Atualizando pessoa: ID={}, Nome={}, Matrícula={}, Empresa={}",
                personId, request.getNome(), request.getMatricula(), request.getEmpresaId());

        return ReactiveSecurityContextHelper.isSuperAdmin()
                .flatMap(isSuperAdmin -> {
                    if (Boolean.TRUE.equals(isSuperAdmin)) {
                        return personRepository.findById(personId)
                                .switchIfEmpty(Mono.error(new PersonNotFoundException("ID: " + personId)))
                                .flatMap(person -> applyAndSave(person, request));
                    }
                    return ReactiveSecurityContextHelper.getTenantId()
                            .flatMap(tenantId -> personRepository.findByTenantIdAndId(tenantId, personId)
                                    .switchIfEmpty(Mono.error(new PersonNotFoundException("ID: " + personId)))
                                    .flatMap(person -> applyAndSave(person, request)));
                });
    }

    private Mono<Person> applyAndSave(Person person, UpdatePersonRequest request) {
        person.setNome(request.getNome());
        if (request.getMatricula() != null) {
            person.setMatricula(request.getMatricula());
        }
        return personEmpresaVinculoService.validateAndApply(
                        person.getTenantId(), request.getEmpresaId(), request.getPercentualHonorarioId(), person)
                .then(Mono.defer(() -> {
                    person.setUpdatedAt(Instant.now());
                    return personRepository.save(person);
                }))
                .doOnSuccess(p -> log.info("Pessoa atualizada com sucesso: ID={}", p.getId()));
    }
}
