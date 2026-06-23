package br.com.verticelabs.pdfprocessor.infrastructure.mongodb;

import br.com.verticelabs.pdfprocessor.domain.model.Person;
import br.com.verticelabs.pdfprocessor.domain.repository.PersonRepository;
import br.com.verticelabs.pdfprocessor.interfaces.dashboard.dto.DashboardChartItem;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.DateOperators;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Map;

@Component
@RequiredArgsConstructor
public class MongoPersonRepositoryAdapter implements PersonRepository {

    private final SpringDataPersonRepository repository;
    private final ReactiveMongoTemplate mongoTemplate;

    @Override
    public Mono<Person> findById(String id) {
        return repository.findById(id);
    }

    @Override
    public Mono<Person> findByTenantIdAndCpf(String tenantId, String cpf) {
        return repository.findByTenantIdAndCpf(tenantId, cpf);
    }

    @Override
    public Mono<Person> save(Person person) {
        return repository.save(person);
    }

    @Override
    public Mono<Boolean> existsByTenantIdAndCpf(String tenantId, String cpf) {
        return repository.existsByTenantIdAndCpf(tenantId, cpf);
    }

    @Override
    public Flux<Person> findAllByTenantId(String tenantId) {
        return repository.findAllByTenantId(tenantId);
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
    public Flux<DashboardChartItem> countPessoasPorAno(String tenantId) {
        Criteria criteria = Criteria.where("createdAt").ne(null);
        if (tenantId != null) {
            criteria = criteria.and("tenantId").is(tenantId);
        }

        Aggregation aggregation = Aggregation.newAggregation(
                Aggregation.match(criteria),
                Aggregation.project()
                        .and(DateOperators.Year.yearOf("createdAt")).as("ano"),
                Aggregation.match(Criteria.where("ano").ne(null)),
                Aggregation.group("ano").count().as("valor"),
                Aggregation.sort(Sort.Direction.ASC, "_id"),
                Aggregation.project("valor").and("_id").as("label")
        );

        return mongoTemplate.aggregate(aggregation, "persons", Map.class)
                .map(doc -> DashboardChartItem.builder()
                        .label(doc.get("label").toString())
                        .valor(((Number) doc.get("valor")).longValue())
                        .build());
    }

    @Override
    public Mono<Person> findByTenantIdAndId(String tenantId, String id) {
        return repository.findByTenantIdAndId(tenantId, id);
    }

    @Override
    public Mono<Void> deleteById(String id) {
        return repository.deleteById(id).then();
    }

    @Override
    public Mono<Long> countByEmpresaId(String empresaId) {
        return repository.countByEmpresaId(empresaId);
    }

    @Override
    public Flux<Person> findByValidadoTrue() {
        return repository.findByValidadoTrue();
    }

    @Override
    public Mono<Long> countByValidadoTrue() {
        return repository.countByValidadoTrue();
    }

    @Override
    public Mono<Long> countByValidadoFalseOrNull() {
        Criteria criteria = new Criteria().orOperator(
                Criteria.where("validado").is(false),
                Criteria.where("validado").is(null),
                Criteria.where("validado").exists(false));
        return mongoTemplate.count(Query.query(criteria), Person.class);
    }

    @Override
    @Deprecated
    public Mono<Person> findByCpf(String cpf) {
        return repository.findByCpf(cpf);
    }

    @Override
    @Deprecated
    public Mono<Boolean> existsByCpf(String cpf) {
        return repository.existsByCpf(cpf);
    }
}
