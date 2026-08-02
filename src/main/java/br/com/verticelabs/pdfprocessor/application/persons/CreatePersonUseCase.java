package br.com.verticelabs.pdfprocessor.application.persons;

import br.com.verticelabs.pdfprocessor.application.empresas.PersonEmpresaVinculoService;
import br.com.verticelabs.pdfprocessor.domain.exceptions.InvalidCpfException;
import br.com.verticelabs.pdfprocessor.domain.exceptions.PersonDuplicadaException;
import br.com.verticelabs.pdfprocessor.domain.model.Person;
import br.com.verticelabs.pdfprocessor.domain.model.PersonStatus;
import br.com.verticelabs.pdfprocessor.domain.repository.PersonRepository;
import br.com.verticelabs.pdfprocessor.domain.service.CpfValidationService;
import br.com.verticelabs.pdfprocessor.domain.service.MatriculaNormalizer;
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
    private final PersonEmpresaVinculoService personEmpresaVinculoService;

    public Mono<Person> execute(CreatePersonRequest request) {
        log.info("Criando pessoa: CPF={}, Nome={}, Matrícula={}, Empresa={}",
                request.getCpf(), request.getNome(), request.getMatricula(), request.getEmpresaId());

        String normalizedCpf = cpfValidationService.normalize(request.getCpf());
        if (!cpfValidationService.isValid(normalizedCpf)) {
            return Mono.error(new InvalidCpfException("CPF inválido: " + request.getCpf()));
        }

        final String normalizedMatricula;
        try {
            normalizedMatricula = MatriculaNormalizer.normalizeOptional(request.getMatricula());
        } catch (IllegalArgumentException ex) {
            return Mono.error(ex);
        }

        return ReactiveSecurityContextHelper.isSuperAdmin()
                .flatMap(isSuperAdmin -> {
                    if (Boolean.TRUE.equals(isSuperAdmin)) {
                        return createForTenant("GLOBAL", normalizedCpf, normalizedMatricula, request);
                    }
                    return ReactiveSecurityContextHelper.getTenantId()
                            .flatMap(tenantId -> createForTenant(tenantId, normalizedCpf, normalizedMatricula, request));
                });
    }

    private Mono<Person> createForTenant(
            String tenantId, String normalizedCpf, String normalizedMatricula, CreatePersonRequest request) {
        return personRepository.existsByTenantIdAndCpf(tenantId, normalizedCpf)
                .flatMap(exists -> {
                    if (Boolean.TRUE.equals(exists)) {
                        return Mono.error(new PersonDuplicadaException(normalizedCpf, tenantId));
                    }

                    Person person = Person.builder()
                            .tenantId(tenantId)
                            .cpf(normalizedCpf)
                            .nome(request.getNome())
                            .matricula(normalizedMatricula)
                            .ativo(true)
                            .status(PersonStatus.EM_PROCESSAMENTO)
                            .createdAt(Instant.now())
                            .updatedAt(Instant.now())
                            .build();

                    return personEmpresaVinculoService.validateAndApply(
                                    tenantId, request.getEmpresaId(), request.getPercentualHonorarioId(), person)
                            .then(personRepository.save(person))
                            .doOnSuccess(p -> log.info("Pessoa criada com sucesso: ID={}, CPF={}, Matrícula={}",
                                    p.getId(), p.getCpf(), p.getMatricula()));
                });
    }
}
