package br.com.verticelabs.pdfprocessor.infrastructure.mongodb;

import br.com.verticelabs.pdfprocessor.domain.model.PayrollEntry;
import br.com.verticelabs.pdfprocessor.domain.repository.PayrollEntryRepository;
import br.com.verticelabs.pdfprocessor.interfaces.dashboard.dto.DashboardChartItem;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class MongoPayrollEntryRepositoryAdapter implements PayrollEntryRepository {

    private final SpringDataPayrollEntryRepository repository;
    private final ReactiveMongoTemplate mongoTemplate;

    @Override
    public Mono<PayrollEntry> save(PayrollEntry entry) {
        return repository.save(entry);
    }

    @Override
    public Flux<PayrollEntry> saveAll(Flux<PayrollEntry> entries) {
        return repository.saveAll(entries);
    }

    @Override
    public Mono<PayrollEntry> findById(String id) {
        return repository.findById(id);
    }

    @Override
    public Flux<PayrollEntry> findByDocumentoId(String documentoId) {
        return repository.findByDocumentoId(documentoId);
    }

    @Override
    public Flux<PayrollEntry> findAll() {
        return repository.findAll();
    }

    @Override
    public Mono<Long> countByDocumentoId(String documentoId) {
        return repository.countByDocumentoId(documentoId);
    }

    @Override
    public Flux<PayrollEntry> findByTenantIdAndDocumentoId(String tenantId, String documentoId) {
        return repository.findByTenantIdAndDocumentoId(tenantId, documentoId);
    }

    @Override
    public Flux<PayrollEntry> findAllByTenantId(String tenantId) {
        return repository.findAllByTenantId(tenantId);
    }

    @Override
    public Mono<Long> countByTenantIdAndDocumentoId(String tenantId, String documentoId) {
        return repository.countByTenantIdAndDocumentoId(tenantId, documentoId);
    }

    @Override
    public Mono<Long> countByTenantId(String tenantId) {
        return repository.countByTenantId(tenantId);
    }

    @Override
    public Mono<Long> countAll() {
        return repository.count();
    }

    @Override
    public Flux<DashboardChartItem> countTopRubricas(String tenantId, int limit) {
        Criteria criteria = Criteria.where("rubricaCodigo").ne(null);
        if (tenantId != null) {
            criteria = criteria.and("tenantId").is(tenantId);
        }

        Aggregation aggregation = Aggregation.newAggregation(
                Aggregation.match(criteria),
                Aggregation.group("rubricaCodigo").count().as("valor"),
                Aggregation.sort(Sort.Direction.DESC, "valor"),
                Aggregation.limit(limit),
                Aggregation.project("valor").and("_id").as("label")
        );

        return mongoTemplate.aggregate(aggregation, "payroll_entries", Map.class)
                .map(doc -> DashboardChartItem.builder()
                        .label(doc.get("label").toString())
                        .valor(((Number) doc.get("valor")).longValue())
                        .build());
    }

    @Override
    public Flux<DashboardChartItem> countLancamentosPorAno(String tenantId) {
        Criteria criteria = Criteria.where("referencia").ne(null);
        if (tenantId != null) {
            criteria = criteria.and("tenantId").is(tenantId);
        }

        Aggregation aggregation = Aggregation.newAggregation(
                Aggregation.match(criteria),
                Aggregation.group("referencia").count().as("valor"),
                Aggregation.project("valor").and("_id").as("referencia")
        );

        return mongoTemplate.aggregate(aggregation, "payroll_entries", Map.class)
                .collectList()
                .flatMapMany(list -> {
                    Map<Integer, Long> porAno = new HashMap<>();
                    for (Map<String, Object> doc : list) {
                        String referencia = doc.get("referencia").toString();
                        if (referencia.length() >= 4) {
                            try {
                                int ano = Integer.parseInt(referencia.substring(0, 4));
                                long valor = ((Number) doc.get("valor")).longValue();
                                porAno.merge(ano, valor, Long::sum);
                            } catch (NumberFormatException ignored) {
                                // Ignorar referências inválidas
                            }
                        }
                    }
                    return Flux.fromIterable(porAno.entrySet().stream()
                            .sorted(Map.Entry.comparingByKey())
                            .map(entry -> DashboardChartItem.builder()
                                    .label(String.valueOf(entry.getKey()))
                                    .valor(entry.getValue())
                                    .build())
                            .collect(Collectors.toList()));
                });
    }

    @Override
    public Mono<Void> deleteByTenantIdAndDocumentoId(String tenantId, String documentoId) {
        return repository.findByTenantIdAndDocumentoId(tenantId, documentoId)
                .flatMap(repository::delete)
                .then();
    }

    @Override
    @Deprecated
    public Mono<Void> deleteByDocumentoId(String documentoId) {
        return repository.findByDocumentoId(documentoId)
                .flatMap(repository::delete)
                .then();
    }
}
