package br.com.verticelabs.pdfprocessor.application.excel;

import br.com.verticelabs.pdfprocessor.application.consolidation.ConsolidationUseCase;
import br.com.verticelabs.pdfprocessor.application.security.EvaluatorAccessService;
import br.com.verticelabs.pdfprocessor.domain.exceptions.NoEntriesFoundException;
import br.com.verticelabs.pdfprocessor.domain.exceptions.PersonNotFoundException;
import br.com.verticelabs.pdfprocessor.domain.model.DocumentType;
import br.com.verticelabs.pdfprocessor.domain.model.IrpfDeclaracaoData;
import br.com.verticelabs.pdfprocessor.domain.model.Person;
import br.com.verticelabs.pdfprocessor.domain.repository.PayrollDocumentRepository;
import br.com.verticelabs.pdfprocessor.domain.repository.PersonRepository;
import br.com.verticelabs.pdfprocessor.infrastructure.security.ReactiveSecurityContextHelper;
import br.com.verticelabs.pdfprocessor.interfaces.excel.dto.ResumoGeralResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class ResumoGeralUseCase {

    private final PersonRepository personRepository;
    private final ConsolidationUseCase consolidationUseCase;
    private final PayrollDocumentRepository documentRepository;
    private final ResumoGeralAssemblyService resumoGeralAssemblyService;
    private final ResumoGeralResponseMapper resumoGeralResponseMapper;
    private final EvaluatorAccessService evaluatorAccessService;

    public record MontagemBundle(Person person, ResumoGeralMontagemResult montagem) {
    }

    public Mono<ResumoGeralResponse> getByPersonId(String personId) {
        log.info("ResumoGeralUseCase.getByPersonId: {}", personId);
        return montarByPersonId(personId)
                .map(bundle -> resumoGeralResponseMapper.toResponse(bundle.person(), bundle.montagem()));
    }

    public Mono<MontagemBundle> montarByPersonId(String personId) {
        log.info("ResumoGeralUseCase.montarByPersonId: {}", personId);

        return evaluatorAccessService.assertPersonAccessible(personId)
                .then(evaluatorAccessService.isEvaluator())
                .flatMap(isEvaluator -> {
                    if (Boolean.TRUE.equals(isEvaluator)) {
                        // EVALUATOR: acesso já validado pela allowlist; busca direta por id
                        return personRepository.findById(personId);
                    }
                    return ReactiveSecurityContextHelper.isSuperAdmin()
                            .flatMap(isSuperAdmin -> resolvePerson(personId, isSuperAdmin));
                })
                .switchIfEmpty(Mono.error(new PersonNotFoundException(personId)))
                .flatMap(person -> consolidationUseCase.consolidate(person.getCpf(), person.getTenantId(), null, null)
                        .flatMap(consolidated -> {
                            if (consolidated.getRubricas() == null || consolidated.getRubricas().isEmpty()) {
                                return Mono.error(new NoEntriesFoundException(person.getCpf()));
                            }
                            return buscarIrpfDeclaracoes(person)
                                    .flatMap(irpf -> resumoGeralAssemblyService.montar(person, consolidated, irpf)
                                            .flatMap(montagem -> {
                                                if (montagem.linhas() == null || montagem.linhas().isEmpty()) {
                                                    return Mono.empty();
                                                }
                                                return Mono.just(new MontagemBundle(person, montagem));
                                            }));
                        }));
    }

    private Mono<Person> resolvePerson(String personId, boolean isSuperAdmin) {
        if (isSuperAdmin) {
            return personRepository.findById(personId);
        }
        return ReactiveSecurityContextHelper.getTenantId()
                .flatMap(tenantId -> personRepository.findById(personId)
                        .flatMap(person -> {
                            if (!tenantId.equals(person.getTenantId())) {
                                return Mono.error(new PersonNotFoundException(personId));
                            }
                            return Mono.just(person);
                        }));
    }

    private Mono<Map<String, IrpfDeclaracaoData>> buscarIrpfDeclaracoes(Person person) {
        return documentRepository.findByTenantIdAndCpf(person.getTenantId(), person.getCpf())
                .filter(doc -> doc.getTipo() == DocumentType.INCOME_TAX && doc.getIrpfData() != null)
                .collectList()
                .map(docs -> {
                    Map<String, IrpfDeclaracaoData> map = new HashMap<>();
                    for (var doc : docs) {
                        IrpfDeclaracaoData data = doc.getIrpfData();
                        String anoCalendario = data.getAnoCalendario();
                        if (anoCalendario != null && !anoCalendario.isBlank()) {
                            map.put(anoCalendario.trim(), data);
                        }
                    }
                    return map;
                })
                .onErrorReturn(Map.of());
    }
}
