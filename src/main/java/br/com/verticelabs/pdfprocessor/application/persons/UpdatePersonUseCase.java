package br.com.verticelabs.pdfprocessor.application.persons;

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

    public Mono<Person> execute(String personId, UpdatePersonRequest request) {
        log.info("Atualizando pessoa: ID={}, Nome={}, Matrícula={}", 
                personId, request.getNome(), request.getMatricula());
        
        return ReactiveSecurityContextHelper.isSuperAdmin()
                .flatMap(isSuperAdmin -> {
                    if (Boolean.TRUE.equals(isSuperAdmin)) {
                        // SUPER_ADMIN pode atualizar qualquer pessoa
                        return personRepository.findById(personId)
                                .switchIfEmpty(Mono.error(new PersonNotFoundException("ID: " + personId)))
                                .flatMap(person -> {
                                    person.setNome(request.getNome());
                                    if (request.getMatricula() != null) {
                                        person.setMatricula(request.getMatricula());
                                    }
                                    person.setUpdatedAt(Instant.now());
                                    
                                    return personRepository.save(person)
                                            .doOnSuccess(p -> log.info("Pessoa atualizada com sucesso: ID={}", p.getId()));
                                });
                    } else {
                        // Outros usuários só podem atualizar pessoas do seu tenant
                        return ReactiveSecurityContextHelper.getTenantId()
                                .flatMap(tenantId -> personRepository.findByTenantIdAndId(tenantId, personId)
                                        .switchIfEmpty(Mono.error(new PersonNotFoundException("ID: " + personId)))
                                        .flatMap(person -> {
                                            person.setNome(request.getNome());
                                            if (request.getMatricula() != null) {
                                                person.setMatricula(request.getMatricula());
                                            }
                                            person.setUpdatedAt(Instant.now());
                                            
                                            return personRepository.save(person)
                                                    .doOnSuccess(p -> log.info("Pessoa atualizada com sucesso: ID={}", p.getId()));
                                        }));
                    }
                });
    }
}

