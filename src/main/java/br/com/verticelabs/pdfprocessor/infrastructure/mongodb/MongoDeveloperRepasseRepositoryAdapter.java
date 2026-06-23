package br.com.verticelabs.pdfprocessor.infrastructure.mongodb;

import br.com.verticelabs.pdfprocessor.application.repasse.RepasseListFilter;
import br.com.verticelabs.pdfprocessor.domain.model.DeveloperRepasse;
import br.com.verticelabs.pdfprocessor.domain.model.RepasseStatus;
import br.com.verticelabs.pdfprocessor.domain.repository.DeveloperRepasseRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.DateOperators;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class MongoDeveloperRepasseRepositoryAdapter implements DeveloperRepasseRepository {

    private final SpringDataDeveloperRepasseRepository repository;
    private final ReactiveMongoTemplate mongoTemplate;

    @Override
    public Mono<DeveloperRepasse> save(DeveloperRepasse repasse) {
        return repository.save(repasse);
    }

    @Override
    public Mono<DeveloperRepasse> findById(String id) {
        return repository.findById(id);
    }

    @Override
    public Mono<DeveloperRepasse> findByPersonId(String personId) {
        return repository.findByPersonId(personId);
    }

    @Override
    public Mono<Boolean> existsByPersonId(String personId) {
        return repository.existsByPersonId(personId);
    }

    @Override
    public Flux<DeveloperRepasse> findByFilters(RepasseListFilter filter, int page, int size) {
        Query query = buildFilterQuery(filter)
                .with(PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "validadoEm")));
        return mongoTemplate.find(query, DeveloperRepasse.class);
    }

    @Override
    public Mono<Long> countByFilters(RepasseListFilter filter) {
        Query query = buildFilterQuery(filter);
        return mongoTemplate.count(query, DeveloperRepasse.class);
    }

    @Override
    public Mono<Long> countByStatus(RepasseStatus status) {
        return mongoTemplate.count(Query.query(Criteria.where("status").is(status)), DeveloperRepasse.class);
    }

    @Override
    public Mono<BigDecimal> sumValorByStatus(RepasseStatus status) {
        Query query = Query.query(Criteria.where("status").is(status));
        return mongoTemplate.find(query, DeveloperRepasse.class)
                .map(repasse -> repasse.getValorUnitario() != null ? repasse.getValorUnitario() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    @Override
    public Flux<Map<String, Object>> countValidacoesGroupedByMesReferencia() {
        Aggregation aggregation = Aggregation.newAggregation(
                Aggregation.match(Criteria.where("mesReferencia").ne(null)),
                Aggregation.group("mesReferencia").count().as("total"),
                Aggregation.sort(Sort.Direction.ASC, "_id"));

        return mongoTemplate.aggregate(aggregation, "developer_repasses", Map.class)
                .map(doc -> Map.<String, Object>of(
                        "mes", doc.get("_id").toString(),
                        "total", ((Number) doc.get("total")).longValue()));
    }

    @Override
    public Flux<Map<String, Object>> countPagosGroupedByMes() {
        Aggregation aggregation = Aggregation.newAggregation(
                Aggregation.match(Criteria.where("status").is(RepasseStatus.PAGO).and("pagoEm").ne(null)),
                Aggregation.project()
                        .and(DateOperators.DateToString.dateOf("pagoEm")
                                .toString("%Y-%m")
                                .withTimezone(DateOperators.Timezone.valueOf("America/Sao_Paulo")))
                        .as("mes"),
                Aggregation.group("mes").count().as("total"),
                Aggregation.sort(Sort.Direction.ASC, "_id"));

        return mongoTemplate.aggregate(aggregation, "developer_repasses", Map.class)
                .map(doc -> Map.<String, Object>of(
                        "mes", doc.get("_id").toString(),
                        "total", ((Number) doc.get("total")).longValue()));
    }

    @Override
    public Flux<DeveloperRepasse> findByStatus(RepasseStatus status) {
        return mongoTemplate.find(Query.query(Criteria.where("status").is(status)), DeveloperRepasse.class);
    }

    private Query buildFilterQuery(RepasseListFilter filter) {
        List<Criteria> parts = new ArrayList<>();

        if (filter.status() != null) {
            parts.add(Criteria.where("status").is(filter.status()));
        }
        if (filter.mesReferencia() != null && !filter.mesReferencia().isBlank()) {
            parts.add(Criteria.where("mesReferencia").is(filter.mesReferencia()));
        }
        if (filter.tenantId() != null && !filter.tenantId().isBlank()) {
            parts.add(Criteria.where("tenantId").is(filter.tenantId()));
        }
        if (filter.validadoDe() != null) {
            parts.add(Criteria.where("validadoEm").gte(filter.validadoDe()));
        }
        if (filter.validadoAte() != null) {
            parts.add(Criteria.where("validadoEm").lte(filter.validadoAte()));
        }
        if (filter.pagoDe() != null) {
            parts.add(Criteria.where("pagoEm").gte(filter.pagoDe()));
        }
        if (filter.pagoAte() != null) {
            parts.add(Criteria.where("pagoEm").lte(filter.pagoAte()));
        }

        if (parts.isEmpty()) {
            return new Query();
        }
        if (parts.size() == 1) {
            return Query.query(parts.get(0));
        }
        return Query.query(new Criteria().andOperator(parts.toArray(new Criteria[0])));
    }
}
