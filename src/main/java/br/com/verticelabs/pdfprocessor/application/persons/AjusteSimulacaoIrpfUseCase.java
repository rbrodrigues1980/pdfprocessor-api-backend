package br.com.verticelabs.pdfprocessor.application.persons;

import br.com.verticelabs.pdfprocessor.domain.exceptions.DocumentNotFoundException;
import br.com.verticelabs.pdfprocessor.domain.exceptions.ForbiddenOperationException;
import br.com.verticelabs.pdfprocessor.domain.model.DocumentType;
import br.com.verticelabs.pdfprocessor.domain.model.IrpfDeclaracaoData;
import br.com.verticelabs.pdfprocessor.domain.model.PayrollDocument;
import br.com.verticelabs.pdfprocessor.domain.model.Person;
import br.com.verticelabs.pdfprocessor.domain.model.TipoAjusteSimulacaoIrpf;
import br.com.verticelabs.pdfprocessor.domain.repository.PayrollDocumentRepository;
import br.com.verticelabs.pdfprocessor.infrastructure.security.ReactiveSecurityContextHelper;
import br.com.verticelabs.pdfprocessor.interfaces.persons.dto.AjusteSimulacaoIrpfRequest;
import br.com.verticelabs.pdfprocessor.interfaces.persons.dto.AjusteSimulacaoIrpfResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class AjusteSimulacaoIrpfUseCase {

    private static final RoundingMode RM = RoundingMode.HALF_UP;

    private final GetPersonByIdUseCase getPersonByIdUseCase;
    private final PayrollDocumentRepository documentRepository;

    public Mono<AjusteSimulacaoIrpfResponse> listar(String personId) {
        return exigirAdministrador()
                .then(getPersonByIdUseCase.execute(personId))
                .flatMap(this::montarResposta);
    }

    public Mono<AjusteSimulacaoIrpfResponse> salvar(String personId, AjusteSimulacaoIrpfRequest request) {
        TipoAjusteSimulacaoIrpf tipo = TipoAjusteSimulacaoIrpf.from(request.getTipo());
        String ano = anoObrigatorio(request.getAnoCalendario());
        BigDecimal valor = normalizarValor(request.getValor());
        return exigirAdministrador()
                .then(getPersonByIdUseCase.execute(personId))
                .flatMap(person -> declaracoesDoAno(person, ano)
                        .flatMap(docs -> {
                            for (PayrollDocument doc : docs) {
                                aplicar(doc.getIrpfData(), tipo, valor);
                            }
                            return Flux.fromIterable(docs)
                                    .flatMap(documentRepository::save)
                                    .then(montarResposta(person));
                        }));
    }

    public Mono<AjusteSimulacaoIrpfResponse> excluir(String personId, AjusteSimulacaoIrpfRequest request) {
        TipoAjusteSimulacaoIrpf tipo = TipoAjusteSimulacaoIrpf.from(request.getTipo());
        String ano = anoObrigatorio(request.getAnoCalendario());
        return exigirAdministrador()
                .then(getPersonByIdUseCase.execute(personId))
                .flatMap(person -> declaracoesDoAno(person, ano)
                        .flatMap(docs -> {
                            for (PayrollDocument doc : docs) {
                                aplicar(doc.getIrpfData(), tipo, null);
                            }
                            return Flux.fromIterable(docs)
                                    .flatMap(documentRepository::save)
                                    .then(montarResposta(person));
                        }));
    }

    private Mono<List<PayrollDocument>> declaracoesDoAno(Person person, String ano) {
        return documentRepository.findByTenantIdAndCpf(person.getTenantId(), person.getCpf())
                .filter(this::ehDeclaracao)
                .filter(doc -> ano.equals(doc.getIrpfData().getAnoCalendario().trim()))
                .collectList()
                .flatMap(docs -> docs.isEmpty()
                        ? Mono.error(new DocumentNotFoundException(
                                "Nenhuma declaração de IR do ano-calendário " + ano))
                        : Mono.just(docs));
    }

    private Mono<AjusteSimulacaoIrpfResponse> montarResposta(Person person) {
        return documentRepository.findByTenantIdAndCpf(person.getTenantId(), person.getCpf())
                .filter(this::ehDeclaracao)
                .collectList()
                .map(this::paraResposta);
    }

    private AjusteSimulacaoIrpfResponse paraResposta(List<PayrollDocument> docs) {
        Map<String, AjusteSimulacaoIrpfResponse.DeclaracaoDisponivel> declaracoes = new LinkedHashMap<>();
        List<AjusteSimulacaoIrpfResponse.Ajuste> ajustes = new ArrayList<>();
        docs.stream()
                .sorted(Comparator.comparing(doc -> doc.getIrpfData().getAnoCalendario()))
                .forEach(doc -> {
                    IrpfDeclaracaoData data = doc.getIrpfData();
                    String ano = data.getAnoCalendario().trim();
                    declaracoes.putIfAbsent(ano, AjusteSimulacaoIrpfResponse.DeclaracaoDisponivel.builder()
                            .anoCalendario(ano)
                            .exercicio(data.getExercicio())
                            .build());
                    adicionarAjuste(ajustes, ano, data.getExercicio(),
                            TipoAjusteSimulacaoIrpf.RENDIMENTOS_PJ_TITULAR,
                            data.getAjusteSimulacaoRendimentosPjTitular());
                    adicionarAjuste(ajustes, ano, data.getExercicio(),
                            TipoAjusteSimulacaoIrpf.DEDUCAO_PREV_OFICIAL_PUBLICA,
                            data.getAjusteSimulacaoPrevOficialPublica());
                    adicionarAjuste(ajustes, ano, data.getExercicio(),
                            TipoAjusteSimulacaoIrpf.DEDUCAO_PREV_OFICIAL_RRA,
                            data.getAjusteSimulacaoPrevOficialRra());
                    adicionarAjuste(ajustes, ano, data.getExercicio(),
                            TipoAjusteSimulacaoIrpf.DEDUCAO_PREV_COMPLEMENTAR,
                            data.getAjusteSimulacaoPrevComplementar());
                    adicionarAjuste(ajustes, ano, data.getExercicio(),
                            TipoAjusteSimulacaoIrpf.IMPOSTO_DEPOSITO_JUDICIAL_CIVEL,
                            data.getAjusteSimulacaoImpostoDepositoJudicialCivel());
                });
        return AjusteSimulacaoIrpfResponse.builder()
                .declaracoes(new ArrayList<>(declaracoes.values()))
                .ajustes(ajustes)
                .build();
    }

    private void adicionarAjuste(
            List<AjusteSimulacaoIrpfResponse.Ajuste> ajustes,
            String ano,
            String exercicio,
            TipoAjusteSimulacaoIrpf tipo,
            BigDecimal valor) {
        if (valor == null) {
            return;
        }
        boolean jaListado = ajustes.stream().anyMatch(a ->
                ano.equals(a.getAnoCalendario()) && tipo.name().equals(a.getTipo()));
        if (jaListado) {
            return;
        }
        ajustes.add(AjusteSimulacaoIrpfResponse.Ajuste.builder()
                .anoCalendario(ano)
                .exercicio(exercicio)
                .tipo(tipo.name())
                .area(tipo.getArea().name())
                .rotuloArea(tipo.getArea().getRotulo())
                .rotulo(tipo.getRotulo())
                .valor(valor)
                .build());
    }

    private void aplicar(IrpfDeclaracaoData data, TipoAjusteSimulacaoIrpf tipo, BigDecimal valor) {
        switch (tipo) {
            case RENDIMENTOS_PJ_TITULAR -> data.setAjusteSimulacaoRendimentosPjTitular(valor);
            case DEDUCAO_PREV_OFICIAL_PUBLICA -> data.setAjusteSimulacaoPrevOficialPublica(valor);
            case DEDUCAO_PREV_OFICIAL_RRA -> data.setAjusteSimulacaoPrevOficialRra(valor);
            case DEDUCAO_PREV_COMPLEMENTAR -> data.setAjusteSimulacaoPrevComplementar(valor);
            case IMPOSTO_DEPOSITO_JUDICIAL_CIVEL -> data.setAjusteSimulacaoImpostoDepositoJudicialCivel(valor);
        }
    }

    private boolean ehDeclaracao(PayrollDocument doc) {
        return doc.getTipo() == DocumentType.INCOME_TAX
                && doc.getIrpfData() != null
                && doc.getIrpfData().getAnoCalendario() != null
                && !doc.getIrpfData().getAnoCalendario().isBlank();
    }

    private static String anoObrigatorio(String ano) {
        if (ano == null || ano.isBlank()) {
            throw new IllegalArgumentException("Ano-calendário é obrigatório");
        }
        return ano.trim();
    }

    private static BigDecimal normalizarValor(BigDecimal valor) {
        if (valor == null) {
            throw new IllegalArgumentException("Valor é obrigatório");
        }
        if (valor.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Valor não pode ser negativo");
        }
        return valor.setScale(2, RM);
    }

    private Mono<Void> exigirAdministrador() {
        return Mono.zip(
                        ReactiveSecurityContextHelper.isSuperAdmin().onErrorReturn(false),
                        ReactiveSecurityContextHelper.isTenantAdmin().onErrorReturn(false))
                .flatMap(roles -> Boolean.TRUE.equals(roles.getT1()) || Boolean.TRUE.equals(roles.getT2())
                        ? Mono.empty()
                        : Mono.error(new ForbiddenOperationException(
                                "Apenas administradores podem informar ajustes da simulação de IRPF")));
    }
}
