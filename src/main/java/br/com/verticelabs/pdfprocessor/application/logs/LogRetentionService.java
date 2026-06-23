package br.com.verticelabs.pdfprocessor.application.logs;

import br.com.verticelabs.pdfprocessor.domain.model.LogRetentionPeriod;
import br.com.verticelabs.pdfprocessor.domain.model.SystemConfig;
import br.com.verticelabs.pdfprocessor.domain.repository.SystemConfigRepository;
import br.com.verticelabs.pdfprocessor.interfaces.rest.dto.LogConfigResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.data.mongodb.core.index.ReactiveIndexOperations;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class LogRetentionService {

    private final SystemConfigRepository configRepository;
    private final ReactiveMongoTemplate mongoTemplate;

    @Value("${app.logging.mongo.enabled:false}")
    private boolean mongoLoggingEnabled;

    public Mono<LogRetentionPeriod> getRetentionPeriod() {
        return configRepository.findByKeyAndTenantIdIsNull(SystemConfig.KEY_LOGS_RETENTION)
                .map(config -> LogRetentionPeriod.fromValue(config.getValue()))
                .defaultIfEmpty(LogRetentionPeriod.MONTH);
    }

    public Mono<LogConfigResponse> getConfigResponse() {
        return getRetentionPeriod()
                .flatMap(period -> configRepository.findByKeyAndTenantIdIsNull(SystemConfig.KEY_LOGS_RETENTION)
                        .map(config -> toResponse(period, config.getUpdatedAt(), config.getUpdatedBy(), true))
                        .defaultIfEmpty(toResponse(period, null, null, false)));
    }

    public Mono<LogConfigResponse> updateRetention(LogRetentionPeriod period, String updatedBy) {
        Instant now = Instant.now();
        return configRepository.findByKeyAndTenantIdIsNull(SystemConfig.KEY_LOGS_RETENTION)
                .switchIfEmpty(Mono.just(SystemConfig.builder()
                        .key(SystemConfig.KEY_LOGS_RETENTION)
                        .description("Período de retenção dos logs do sistema")
                        .createdAt(now)
                        .build()))
                .flatMap(config -> {
                    config.setValue(period.name());
                    config.setUpdatedAt(now);
                    config.setUpdatedBy(updatedBy);
                    return configRepository.save(config);
                })
                .flatMap(saved -> applyTtlIndex(period).thenReturn(toResponse(period, saved.getUpdatedAt(), saved.getUpdatedBy(), true)));
    }

    public Mono<Void> applyTtlIndex(LogRetentionPeriod period) {
        ReactiveIndexOperations indexOps = mongoTemplate.indexOps("logs");

        return indexOps.getIndexInfo()
                .filter(index -> index.getIndexFields().stream()
                        .anyMatch(field -> "timestamp".equals(field.getKey())))
                .flatMap(index -> indexOps.dropIndex(index.getName())
                        .doOnSuccess(v -> log.info("Índice TTL antigo removido da collection logs: {}", index.getName()))
                        .onErrorResume(error -> {
                            log.warn("Não foi possível remover índice TTL antigo: {}", error.getMessage());
                            return Mono.empty();
                        }))
                .then(Mono.defer(() -> {
                    Index ttlIndex = new Index()
                            .on("timestamp", Sort.Direction.ASC)
                            .expire(period.getDays(), TimeUnit.DAYS);
                    return indexOps.ensureIndex(ttlIndex)
                            .doOnSuccess(name -> log.info(
                                    "Índice TTL aplicado na collection logs: {} dias ({})",
                                    period.getDays(),
                                    period.getLabel()));
                }))
                .then();
    }

    public Mono<Void> ensureQueryIndexes() {
        ReactiveIndexOperations indexOps = mongoTemplate.indexOps("logs");
        Index levelIndex = new Index().on("level", Sort.Direction.ASC);
        Index timestampDescIndex = new Index().on("timestamp", Sort.Direction.DESC);

        return Mono.when(
                        indexOps.ensureIndex(levelIndex),
                        indexOps.ensureIndex(timestampDescIndex))
                .then();
    }

    private LogConfigResponse toResponse(
            LogRetentionPeriod period,
            Instant updatedAt,
            String updatedBy,
            boolean fromDb) {
        return new LogConfigResponse(
                period.name(),
                period.getLabel(),
                period.getDays(),
                mongoLoggingEnabled,
                updatedAt,
                updatedBy,
                fromDb);
    }
}
