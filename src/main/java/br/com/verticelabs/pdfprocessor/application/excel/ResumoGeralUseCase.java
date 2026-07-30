package br.com.verticelabs.pdfprocessor.application.excel;

import br.com.verticelabs.pdfprocessor.application.consolidation.ConsolidationUseCase;
import br.com.verticelabs.pdfprocessor.application.security.EvaluatorAccessService;
import br.com.verticelabs.pdfprocessor.domain.exceptions.NoEntriesFoundException;
import br.com.verticelabs.pdfprocessor.domain.exceptions.PersonNotFoundException;
import br.com.verticelabs.pdfprocessor.domain.model.DocumentType;
import br.com.verticelabs.pdfprocessor.domain.model.InformeRendimentosData;
import br.com.verticelabs.pdfprocessor.domain.model.InformeRendimentosData.InformacaoComplementarJudiciaria;
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
                .flatMap(this::montarForAuthorizedPerson);
    }

    /**
     * Monta o Resumo Geral para uma pessoa já autorizada/resolvida (ex.: relatório em lote).
     */
    public Mono<MontagemBundle> montarForAuthorizedPerson(Person person) {
        return consolidationUseCase.consolidate(person.getCpf(), person.getTenantId(), null, null)
                .flatMap(consolidated -> {
                    if (consolidated.getRubricas() == null || consolidated.getRubricas().isEmpty()) {
                        return Mono.error(new NoEntriesFoundException(person.getCpf()));
                    }
                    return Mono.zip(buscarIrpfDeclaracoes(person), buscarInformesRendimentos(person))
                            .flatMap(tuple -> resumoGeralAssemblyService.montar(
                                            person, consolidated, tuple.getT1(), tuple.getT2())
                                    .flatMap(montagem -> {
                                        if (montagem.linhas() == null || montagem.linhas().isEmpty()) {
                                            return Mono.empty();
                                        }
                                        return Mono.just(new MontagemBundle(person, montagem));
                                    }));
                });
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

    private Mono<Map<String, InformeRendimentosData>> buscarInformesRendimentos(Person person) {
        return documentRepository.findByTenantIdAndCpf(person.getTenantId(), person.getCpf())
                .filter(doc -> doc.getTipo() == DocumentType.INFORME_RENDIMENTOS
                        && doc.getInformeRendimentosData() != null)
                .collectList()
                .map(docs -> {
                    Map<String, InformeRendimentosData> map = new HashMap<>();
                    for (var doc : docs) {
                        InformeRendimentosData data = doc.getInformeRendimentosData();
                        String ano = data.getAnoCalendario();
                        if (ano == null || ano.isBlank()) {
                            continue;
                        }
                        String key = ano.trim();
                        InformeRendimentosData existing = map.get(key);
                        if (existing == null) {
                            map.put(key, data);
                        } else {
                            map.put(key, mergeInformes(existing, data));
                        }
                    }
                    return map;
                })
                .onErrorReturn(Map.of());
    }

    private static InformeRendimentosData mergeInformes(InformeRendimentosData a, InformeRendimentosData b) {
        java.util.List<InformacaoComplementarJudiciaria> merged = new java.util.ArrayList<>();
        if (a.getInformacoesComplementares() != null) {
            merged.addAll(a.getInformacoesComplementares());
        }
        if (b.getInformacoesComplementares() != null) {
            merged.addAll(b.getInformacoesComplementares());
        }
        return InformeRendimentosData.builder()
                .cnpjFontePagadora(a.getCnpjFontePagadora() != null ? a.getCnpjFontePagadora() : b.getCnpjFontePagadora())
                .razaoSocialFontePagadora(a.getRazaoSocialFontePagadora() != null
                        ? a.getRazaoSocialFontePagadora() : b.getRazaoSocialFontePagadora())
                .anoCalendario(a.getAnoCalendario() != null ? a.getAnoCalendario() : b.getAnoCalendario())
                .cpfBeneficiario(a.getCpfBeneficiario() != null ? a.getCpfBeneficiario() : b.getCpfBeneficiario())
                .nomeBeneficiario(a.getNomeBeneficiario() != null ? a.getNomeBeneficiario() : b.getNomeBeneficiario())
                .naturezaRendimento(a.getNaturezaRendimento() != null ? a.getNaturezaRendimento() : b.getNaturezaRendimento())
                .informacoesComplementaresRaw(a.getInformacoesComplementaresRaw())
                .informacoesComplementares(merged)
                .build();
    }
}
