package br.com.verticelabs.pdfprocessor.application.persons;

import br.com.verticelabs.pdfprocessor.domain.exceptions.InvalidCpfException;
import br.com.verticelabs.pdfprocessor.domain.exceptions.PersonDuplicadaException;
import br.com.verticelabs.pdfprocessor.domain.model.Person;
import br.com.verticelabs.pdfprocessor.domain.repository.PersonRepository;
import br.com.verticelabs.pdfprocessor.domain.service.CpfValidationService;
import br.com.verticelabs.pdfprocessor.infrastructure.security.ReactiveSecurityContextHelper;
import br.com.verticelabs.pdfprocessor.interfaces.persons.dto.CreatePersonRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Instant;

@Slf4j
@Service
@RequiredArgsConstructor
public class CreatePersonUseCase {

    private final PersonRepository personRepository;
    private final CpfValidationService cpfValidationService;

    public Mono<Person> execute(CreatePersonRequest request) {
        log.info("Criando pessoa: CPF={}, Nome={}, Matrícula={}", 
                request.getCpf(), request.getNome(), request.getMatricula());
        
        // Normalizar e validar CPF
        String normalizedCpf = cpfValidationService.normalize(request.getCpf());
        if (!cpfValidationService.isValid(normalizedCpf)) {
            log.error("CPF inválido: {}", request.getCpf());
            return Mono.error(new InvalidCpfException("CPF inválido: " + request.getCpf()));
        }
        
        // Verificar se é SUPER_ADMIN primeiro
        return ReactiveSecurityContextHelper.isSuperAdmin()
                .flatMap(isSuperAdmin -> {
                    if (Boolean.TRUE.equals(isSuperAdmin)) {
                        // SUPER_ADMIN pode criar pessoas - usar "GLOBAL" como tenantId
                        String tenantId = "GLOBAL";
                        log.info("SUPER_ADMIN criando pessoa para tenant: {}", tenantId);
                        
                        // Verificar se já existe pessoa com mesmo CPF no tenant GLOBAL
                        return personRepository.existsByTenantIdAndCpf(tenantId, normalizedCpf)
                                .flatMap(exists -> {
                                    if (Boolean.TRUE.equals(exists)) {
                                        log.error("Pessoa já existe com CPF: {} no tenant: {}", normalizedCpf, tenantId);
                                        return Mono.error(new PersonDuplicadaException(normalizedCpf, tenantId));
                                    }
                                    
                                    // Criar nova pessoa
                                    Person person = Person.builder()
                                            .tenantId(tenantId)
                                            .cpf(normalizedCpf)
                                            .nome(request.getNome())
                                            .matricula(request.getMatricula())
                                            .ativo(true)
                                            .createdAt(Instant.now())
                                            .updatedAt(Instant.now())
                                            .build();
                                    
                                    return personRepository.save(person)
                                            .doOnSuccess(p -> log.info("Pessoa criada com sucesso: ID={}, CPF={}", 
                                                    p.getId(), p.getCpf()));
                                });
                    } else {
                        // Outros usuários precisam ter tenantId
                        return ReactiveSecurityContextHelper.getTenantId()
                                .flatMap(tenantId -> {
                                    log.info("Criando pessoa para tenant: {}", tenantId);
                                    
                                    // Verificar se já existe pessoa com mesmo CPF no tenant
                                    return personRepository.existsByTenantIdAndCpf(tenantId, normalizedCpf)
                                            .flatMap(exists -> {
                                                if (Boolean.TRUE.equals(exists)) {
                                                    log.error("Pessoa já existe com CPF: {} no tenant: {}", normalizedCpf, tenantId);
                                                    return Mono.error(new PersonDuplicadaException(normalizedCpf, tenantId));
                                                }
                                                
                                                // Criar nova pessoa
                                                Person person = Person.builder()
                                                        .tenantId(tenantId)
                                                        .cpf(normalizedCpf)
                                                        .nome(request.getNome())
                                                        .matricula(request.getMatricula())
                                                        .ativo(true)
                                                        .createdAt(Instant.now())
                                                        .updatedAt(Instant.now())
                                                        .build();
                                                
                                                return personRepository.save(person)
                                                        .doOnSuccess(p -> log.info("Pessoa criada com sucesso: ID={}, CPF={}", 
                                                                p.getId(), p.getCpf()));
                                            });
                                });
                    }
                });
    }
}

