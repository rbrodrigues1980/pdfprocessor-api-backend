package br.com.verticelabs.pdfprocessor.infrastructure.mongodb;

import br.com.verticelabs.pdfprocessor.domain.model.TaxaSelic;
import br.com.verticelabs.pdfprocessor.domain.repository.TaxaSelicRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class MongoTaxaSelicRepositoryAdapter implements TaxaSelicRepository {

    private final SpringDataTaxaSelicRepository repository;
    private final ReactiveMongoTemplate mongoTemplate;

    @Override
    public Mono<TaxaSelic> save(TaxaSelic taxaSelic) {
        if (taxaSelic.getId() == null) {
            taxaSelic.setId(UUID.randomUUID().toString());
        }
        taxaSelic.setSyncedAt(LocalDateTime.now());
        return repository.save(taxaSelic);
    }

    @Override
    public Mono<TaxaSelic> findByNumeroReuniaoCopom(Integer numeroReuniao) {
        return repository.findByNumeroReuniaoCopom(numeroReuniao);
    }

    @Override
    public Mono<TaxaSelic> findVigenteEmData(Instant data) {
        Query query = new Query()
                .addCriteria(Criteria.where("dataInicioVigencia").lte(data))
                .addCriteria(new Criteria().orOperator(
                        Criteria.where("dataFimVigencia").gte(data),
                        Criteria.where("dataFimVigencia").isNull()))
                .with(Sort.by(Sort.Direction.DESC, "dataInicioVigencia"))
                .limit(1);

        return mongoTemplate.findOne(query, TaxaSelic.class);
    }

    @Override
    public Mono<TaxaSelic> findVigenteAtual() {
        return repository.findByDataFimVigenciaIsNull();
    }

    @Override
    public Flux<TaxaSelic> findAllOrderByDataDesc() {
        return repository.findAllByOrderByNumeroReuniaoCopomDesc();
    }

    @Override
    public Flux<TaxaSelic> findByPeriodo(Instant inicio, Instant fim) {
        return repository.findByDataInicioVigenciaBetweenOrderByDataInicioVigenciaDesc(inicio, fim);
    }

    @Override
    public Mono<Long> count() {
        return repository.count();
    }

    @Override
    public Mono<Integer> findMaxNumeroReuniao() {
        Aggregation aggregation = Aggregation.newAggregation(
                Aggregation.group().max("numeroReuniaoCopom").as("maxReuniao"),
                Aggregation.project("maxReuniao"));

        return mongoTemplate.aggregate(aggregation, "taxa_selic", org.bson.Document.class)
                .next()
                .map(doc -> doc.getInteger("maxReuniao"))
                .defaultIfEmpty(0);
    }

    @Override
    public Mono<Boolean> existsByNumeroReuniao(Integer numeroReuniao) {
        return repository.existsByNumeroReuniaoCopom(numeroReuniao);
    }
}
