package br.com.verticelabs.pdfprocessor.application.logs;

import br.com.verticelabs.pdfprocessor.domain.exceptions.ForbiddenOperationException;
import br.com.verticelabs.pdfprocessor.domain.model.SystemLogEntry;
import br.com.verticelabs.pdfprocessor.infrastructure.security.ReactiveSecurityContextHelper;
import br.com.verticelabs.pdfprocessor.interfaces.logs.dto.SystemLogEntryResponse;
import br.com.verticelabs.pdfprocessor.interfaces.logs.dto.SystemLogListResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class ListSystemLogsUseCase {

    private final ReactiveMongoTemplate mongoTemplate;

    public Mono<SystemLogListResponse> execute(
            String level,
            String search,
            Instant from,
            Instant to,
            int page,
            int size) {
        return requireSuperAdmin()
                .then(buildQuery(level, search, from, to))
                .flatMap(query -> {
                    Query countQuery = Query.of(query);
                    Query pageQuery = Query.of(query)
                            .with(PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "timestamp")));

                    return mongoTemplate.count(countQuery, SystemLogEntry.class)
                            .flatMap(total -> mongoTemplate.find(pageQuery, SystemLogEntry.class)
                                    .map(this::toResponse)
                                    .collectList()
                                    .map(content -> buildResponse(content, total, page, size)));
                });
    }

    private Mono<Query> buildQuery(String level, String search, Instant from, Instant to) {
        return Mono.fromCallable(() -> {
            List<Criteria> criteriaList = new ArrayList<>();

            if (StringUtils.hasText(level) && !"ALL".equalsIgnoreCase(level)) {
                criteriaList.add(Criteria.where("level").is(level.toUpperCase()));
            }
            if (StringUtils.hasText(search)) {
                criteriaList.add(Criteria.where("message").regex(Pattern.quote(search.trim()), "i"));
            }
            if (from != null) {
                criteriaList.add(Criteria.where("timestamp").gte(Date.from(from)));
            }
            if (to != null) {
                criteriaList.add(Criteria.where("timestamp").lte(Date.from(to)));
            }

            Query query = new Query();
            if (!criteriaList.isEmpty()) {
                query.addCriteria(new Criteria().andOperator(criteriaList.toArray(Criteria[]::new)));
            }
            return query;
        });
    }

    private SystemLogEntryResponse toResponse(SystemLogEntry entry) {
        Instant timestamp = entry.getTimestamp() != null ? entry.getTimestamp().toInstant() : null;
        return new SystemLogEntryResponse(
                entry.getId(),
                timestamp,
                entry.getLevel(),
                entry.getLogger(),
                entry.getThread(),
                entry.getMessage(),
                entry.getException(),
                entry.getContext() != null ? entry.getContext() : Map.of());
    }

    private SystemLogListResponse buildResponse(
            List<SystemLogEntryResponse> content,
            long total,
            int page,
            int size) {
        int totalPages = size > 0 ? (int) Math.ceil((double) total / size) : 0;
        return SystemLogListResponse.builder()
                .content(content)
                .totalElements(total)
                .totalPages(totalPages)
                .currentPage(page)
                .pageSize(size)
                .hasNext(page < totalPages - 1)
                .hasPrevious(page > 0)
                .build();
    }

    private Mono<Void> requireSuperAdmin() {
        return ReactiveSecurityContextHelper.isSuperAdmin()
                .flatMap(isSuperAdmin -> Boolean.TRUE.equals(isSuperAdmin)
                        ? Mono.empty()
                        : Mono.error(new ForbiddenOperationException("Apenas SUPER_ADMIN pode consultar logs do sistema")));
    }
}
