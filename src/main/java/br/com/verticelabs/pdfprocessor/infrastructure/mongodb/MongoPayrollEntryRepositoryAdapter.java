package br.com.verticelabs.pdfprocessor.infrastructure.mongodb;

import br.com.verticelabs.pdfprocessor.domain.model.PayrollEntry;
import br.com.verticelabs.pdfprocessor.domain.repository.PayrollEntryRepository;
import br.com.verticelabs.pdfprocessor.interfaces.dashboard.dto.TotalPorAno;
import br.com.verticelabs.pdfprocessor.interfaces.dashboard.dto.TotalPorMes;
import lombok.RequiredArgsConstructor;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.AggregationResults;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.*;
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
    public Flux<TotalPorAno> getTotalPorAno(String tenantId) {
        // Agregação MongoDB para somar valores agrupados por referencia primeiro
        // Depois processamos em Java para extrair o ano e agrupar
        Aggregation aggregation = Aggregation.newAggregation(
                Aggregation.match(Criteria.where("tenantId").is(tenantId).and("valor").ne(null).and("referencia").ne(null)),
                Aggregation.group("referencia")
                        .sum("valor").as("valorTotal"),
                Aggregation.project("valorTotal")
                        .and("_id").as("referencia")
                        .andExclude("_id")
        );

        return mongoTemplate.aggregate(aggregation, "payroll_entries", Map.class)
                .collectList()
                .flatMapMany(list -> {
                    // Agrupar por ano em Java
                    Map<Integer, Double> porAno = new HashMap<>();
                    for (Map<String, Object> doc : list) {
                        String referencia = doc.get("referencia").toString();
                        if (referencia != null && referencia.length() >= 4) {
                            try {
                                Integer ano = Integer.parseInt(referencia.substring(0, 4));
                                Double valorTotal = ((Number) doc.get("valorTotal")).doubleValue();
                                porAno.merge(ano, valorTotal, Double::sum);
                            } catch (NumberFormatException e) {
                                // Ignorar referencias inválidas
                            }
                        }
                    }
                    // Converter para lista ordenada
                    return Flux.fromIterable(porAno.entrySet().stream()
                            .sorted(Map.Entry.comparingByKey())
                            .map(entry -> TotalPorAno.builder()
                                    .ano(entry.getKey())
                                    .valorTotal(entry.getValue())
                                    .build())
                            .collect(Collectors.toList()));
                });
    }

    @Override
    public Flux<TotalPorAno> getTotalPorAnoAll() {
        // Agregação MongoDB para somar valores agrupados por referencia primeiro
        // Depois processamos em Java para extrair o ano e agrupar
        Aggregation aggregation = Aggregation.newAggregation(
                Aggregation.match(Criteria.where("valor").ne(null).and("referencia").ne(null)),
                Aggregation.group("referencia")
                        .sum("valor").as("valorTotal"),
                Aggregation.project("valorTotal")
                        .and("_id").as("referencia")
                        .andExclude("_id")
        );

        return mongoTemplate.aggregate(aggregation, "payroll_entries", Map.class)
                .collectList()
                .flatMapMany(list -> {
                    // Agrupar por ano em Java
                    Map<Integer, Double> porAno = new HashMap<>();
                    for (Map<String, Object> doc : list) {
                        String referencia = doc.get("referencia").toString();
                        if (referencia != null && referencia.length() >= 4) {
                            try {
                                Integer ano = Integer.parseInt(referencia.substring(0, 4));
                                Double valorTotal = ((Number) doc.get("valorTotal")).doubleValue();
                                porAno.merge(ano, valorTotal, Double::sum);
                            } catch (NumberFormatException e) {
                                // Ignorar referencias inválidas
                            }
                        }
                    }
                    // Converter para lista ordenada
                    return Flux.fromIterable(porAno.entrySet().stream()
                            .sorted(Map.Entry.comparingByKey())
                            .map(entry -> TotalPorAno.builder()
                                    .ano(entry.getKey())
                                    .valorTotal(entry.getValue())
                                    .build())
                            .collect(Collectors.toList()));
                });
    }

    @Override
    public Flux<TotalPorMes> getTotalPorMes(String tenantId) {
        // Agregação MongoDB para somar valores agrupados por mês/ano
        Aggregation aggregation = Aggregation.newAggregation(
                Aggregation.match(Criteria.where("tenantId").is(tenantId).and("valor").ne(null).and("referencia").ne(null)),
                Aggregation.group("referencia")
                        .sum("valor").as("valorTotal"),
                Aggregation.project("valorTotal")
                        .and("_id").as("mesAno")
                        .andExclude("_id"),
                Aggregation.sort(org.springframework.data.domain.Sort.Direction.ASC, "mesAno")
        );

        return mongoTemplate.aggregate(aggregation, "payroll_entries", Map.class)
                .map(doc -> TotalPorMes.builder()
                        .mesAno(doc.get("mesAno").toString())
                        .valorTotal(((Number) doc.get("valorTotal")).doubleValue())
                        .build());
    }

    @Override
    public Flux<TotalPorMes> getTotalPorMesAll() {
        // Agregação MongoDB para somar valores agrupados por mês/ano (todos os tenants)
        Aggregation aggregation = Aggregation.newAggregation(
                Aggregation.match(Criteria.where("valor").ne(null).and("referencia").ne(null)),
                Aggregation.group("referencia")
                        .sum("valor").as("valorTotal"),
                Aggregation.project("valorTotal")
                        .and("_id").as("mesAno")
                        .andExclude("_id"),
                Aggregation.sort(org.springframework.data.domain.Sort.Direction.ASC, "mesAno")
        );

        return mongoTemplate.aggregate(aggregation, "payroll_entries", Map.class)
                .map(doc -> TotalPorMes.builder()
                        .mesAno(doc.get("mesAno").toString())
                        .valorTotal(((Number) doc.get("valorTotal")).doubleValue())
                        .build());
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
        // Spring Data MongoDB pode não suportar deleteByDocumentoId automaticamente
        // Então buscamos todas as entries e deletamos uma por uma
        return repository.findByDocumentoId(documentoId)
                .flatMap(repository::delete)
                .then();
    }
}

