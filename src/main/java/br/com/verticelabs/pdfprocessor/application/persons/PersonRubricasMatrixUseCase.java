package br.com.verticelabs.pdfprocessor.application.persons;

import br.com.verticelabs.pdfprocessor.domain.model.PayrollEntry;
import br.com.verticelabs.pdfprocessor.domain.model.Person;
import br.com.verticelabs.pdfprocessor.domain.repository.PayrollEntryRepository;
import br.com.verticelabs.pdfprocessor.domain.repository.PersonRepository;
import br.com.verticelabs.pdfprocessor.infrastructure.security.ReactiveSecurityContextHelper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class PersonRubricasMatrixUseCase {

    private final PersonRepository personRepository;
    private final PayrollEntryRepository entryRepository;

    public Mono<PersonRubricasMatrixResponse> execute(String cpf) {
        return ReactiveSecurityContextHelper.isSuperAdmin()
                .flatMap(isSuperAdmin -> {
                    Mono<Person> personMono;
                    if (isSuperAdmin) {
                        // SUPER_ADMIN: buscar pessoa sem filtrar por tenantId
                        log.debug("SUPER_ADMIN: buscando pessoa sem filtro de tenantId para CPF: {}", cpf);
                        personMono = personRepository.findByCpf(cpf);
                    } else {
                        // Outros usuários: buscar pessoa filtrando por tenantId
                        log.debug("Usuário regular: buscando pessoa com filtro de tenantId para CPF: {}", cpf);
                        personMono = ReactiveSecurityContextHelper.getTenantId()
                                .flatMap(tenantId -> personRepository.findByTenantIdAndCpf(tenantId, cpf));
                    }

                    return personMono
                            .switchIfEmpty(
                                    Mono.error(new IllegalArgumentException("Pessoa não encontrada com CPF: " + cpf)))
                            .flatMap(person -> {
                                // Buscar todos os documentos da pessoa
                                List<String> documentoIds = person.getDocumentos();
                                if (documentoIds == null || documentoIds.isEmpty()) {
                                    log.debug("Pessoa {} não possui documentos", cpf);
                                    return Mono.just(new PersonRubricasMatrixResponse(
                                            cpf,
                                            person.getNome(),
                                            person.getMatricula(),
                                            new HashMap<>(),
                                            new HashMap<>(),
                                            java.math.BigDecimal.ZERO));
                                }

                                log.debug("Buscando entries de {} documentos para pessoa {}", documentoIds.size(), cpf);

                                // Buscar todas as entries de todos os documentos
                                // Se SUPER_ADMIN, buscar sem filtrar por tenantId
                                // Se não, usar o tenantId da pessoa encontrada
                                Flux<PayrollEntry> entriesFlux;
                                if (isSuperAdmin) {
                                    // SUPER_ADMIN: buscar entries sem filtrar por tenantId
                                    entriesFlux = Flux.fromIterable(documentoIds)
                                            .flatMap(documentId -> entryRepository.findByDocumentoId(documentId));
                                } else {
                                    // Outros usuários: usar tenantId da pessoa encontrada
                                    String tenantId = person.getTenantId();
                                    entriesFlux = Flux.fromIterable(documentoIds)
                                            .flatMap(documentId -> entryRepository
                                                    .findByTenantIdAndDocumentoId(tenantId, documentId));
                                }

                                return entriesFlux
                                        .collectList()
                                        .map(entries -> {
                                            log.debug("Encontradas {} entries para pessoa {}", entries.size(), cpf);
                                            // Agrupar por rubrica e referência (mês/ano)
                                            Map<String, Map<String, RubricaMatrixCell>> matrix = new HashMap<>();
                                            Map<String, java.math.BigDecimal> rubricasTotais = new HashMap<>();
                                            java.math.BigDecimal totalGeral = java.math.BigDecimal.ZERO;

                                            for (PayrollEntry entry : entries) {
                                                String rubricaCodigo = entry.getRubricaCodigo();
                                                String referencia = entry.getReferencia();
                                                java.math.BigDecimal valor = entry.getValor() != null ? entry.getValor()
                                                        : java.math.BigDecimal.ZERO;

                                                // Inicializar mapa da rubrica se necessário
                                                matrix.putIfAbsent(rubricaCodigo, new HashMap<>());

                                                // Adicionar valor à célula da matriz
                                                Map<String, RubricaMatrixCell> rubricaMap = matrix.get(rubricaCodigo);
                                                rubricaMap.putIfAbsent(referencia, new RubricaMatrixCell(referencia,
                                                        java.math.BigDecimal.ZERO, 0));
                                                RubricaMatrixCell cell = rubricaMap.get(referencia);
                                                cell.valor = cell.valor.add(valor);
                                                cell.quantidade += 1;

                                                // Atualizar total da rubrica
                                                rubricasTotais.put(rubricaCodigo,
                                                        rubricasTotais
                                                                .getOrDefault(rubricaCodigo, java.math.BigDecimal.ZERO)
                                                                .add(valor));

                                                // Atualizar total geral
                                                totalGeral = totalGeral.add(valor);
                                            }

                                            return new PersonRubricasMatrixResponse(
                                                    cpf,
                                                    person.getNome(),
                                                    person.getMatricula(),
                                                    matrix,
                                                    rubricasTotais,
                                                    totalGeral);
                                        });
                            });
                });
    }

    public static class PersonRubricasMatrixResponse {
        private final String cpf;
        private final String nome;
        private final String matricula;
        private final Map<String, Map<String, RubricaMatrixCell>> matrix; // rubricaCodigo -> referencia -> cell
        private final Map<String, java.math.BigDecimal> rubricasTotais; // rubricaCodigo -> total
        private final java.math.BigDecimal totalGeral;

        public PersonRubricasMatrixResponse(String cpf, String nome, String matricula,
                Map<String, Map<String, RubricaMatrixCell>> matrix,
                Map<String, java.math.BigDecimal> rubricasTotais,
                java.math.BigDecimal totalGeral) {
            this.cpf = cpf;
            this.nome = nome;
            this.matricula = matricula;
            this.matrix = matrix;
            this.rubricasTotais = rubricasTotais;
            this.totalGeral = totalGeral;
        }

        public String getCpf() {
            return cpf;
        }

        public String getNome() {
            return nome;
        }

        public String getMatricula() {
            return matricula;
        }

        public Map<String, Map<String, RubricaMatrixCell>> getMatrix() {
            return matrix;
        }

        public Map<String, java.math.BigDecimal> getRubricasTotais() {
            return rubricasTotais;
        }

        public java.math.BigDecimal getTotalGeral() {
            return totalGeral;
        }
    }

    public static class RubricaMatrixCell {
        private final String referencia;
        private java.math.BigDecimal valor;
        private int quantidade;

        public RubricaMatrixCell(String referencia, java.math.BigDecimal valor, int quantidade) {
            this.referencia = referencia;
            this.valor = valor;
            this.quantidade = quantidade;
        }

        public String getReferencia() {
            return referencia;
        }

        public java.math.BigDecimal getValor() {
            return valor;
        }

        public int getQuantidade() {
            return quantidade;
        }
    }
}
