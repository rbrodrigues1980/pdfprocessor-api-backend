package br.com.verticelabs.pdfprocessor.application.users;

import br.com.verticelabs.pdfprocessor.domain.model.User;
import br.com.verticelabs.pdfprocessor.infrastructure.security.ReactiveSecurityContextHelper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ListUsersUseCase {

    private final ReactiveMongoTemplate mongoTemplate;

    public Mono<ListUsersResult> execute(String tenantIdFilter, String roleFilter, Boolean ativoFilter,
            String emailFilter, String nomeFilter, int page, int size) {
        return ReactiveSecurityContextHelper.isSuperAdmin()
                .flatMap(isSuperAdmin -> {
                    if (isSuperAdmin) {
                        return listAsSuperAdmin(tenantIdFilter, roleFilter, ativoFilter, emailFilter, nomeFilter, page,
                                size);
                    } else {
                        return ReactiveSecurityContextHelper.getTenantId()
                                .flatMap(currentTenantId -> listAsTenantAdmin(currentTenantId, roleFilter, ativoFilter,
                                        emailFilter, nomeFilter, page, size));
                    }
                });
    }

    private Mono<ListUsersResult> listAsSuperAdmin(String tenantIdFilter, String roleFilter, Boolean ativoFilter,
            String emailFilter, String nomeFilter, int page, int size) {
        Query query = buildQuery(tenantIdFilter, roleFilter, ativoFilter, emailFilter, nomeFilter);

        Pageable pageable = PageRequest.of(page, size);
        query.with(pageable);

        Mono<Long> countMono = Mono.from(mongoTemplate.count(query, User.class));
        Flux<User> usersFlux = mongoTemplate.find(query, User.class);

        return Mono.zip(usersFlux.collectList(), countMono)
                .map(tuple -> {
                    List<User> users = tuple.getT1();
                    // Garantir que todos os Users tenham Sets mutáveis
                    Long total = tuple.getT2();
                    int totalPages = (int) Math.ceil((double) total / size);
                    return new ListUsersResult(users, total, totalPages, page, size);
                });
    }

    private Mono<ListUsersResult> listAsTenantAdmin(String tenantId, String roleFilter, Boolean ativoFilter,
            String emailFilter, String nomeFilter, int page, int size) {
        // TENANT_ADMIN só vê usuários do seu tenant
        Query query = buildQuery(tenantId, roleFilter, ativoFilter, emailFilter, nomeFilter);

        Pageable pageable = PageRequest.of(page, size);
        query.with(pageable);

        Mono<Long> countMono = Mono.from(mongoTemplate.count(query, User.class));
        Flux<User> usersFlux = mongoTemplate.find(query, User.class);

        return Mono.zip(usersFlux.collectList(), countMono)
                .map(tuple -> {
                    List<User> users = tuple.getT1();
                    // Garantir que todos os Users tenham Sets mutáveis
                    Long total = tuple.getT2();
                    int totalPages = (int) Math.ceil((double) total / size);
                    return new ListUsersResult(users, total, totalPages, page, size);
                });
    }

    private Query buildQuery(String tenantIdFilter, String roleFilter, Boolean ativoFilter,
            String emailFilter, String nomeFilter) {
        Query query = new Query();

        if (tenantIdFilter != null && !tenantIdFilter.isEmpty()) {
            query.addCriteria(Criteria.where("tenantId").is(tenantIdFilter));
        }

        if (roleFilter != null && !roleFilter.isEmpty()) {
            query.addCriteria(Criteria.where("roles").in(roleFilter));
        }

        if (ativoFilter != null) {
            query.addCriteria(Criteria.where("ativo").is(ativoFilter));
        } else {
            // Por padrão, mostrar apenas usuários ativos
            query.addCriteria(Criteria.where("ativo").is(true));
        }

        if (emailFilter != null && !emailFilter.isEmpty()) {
            query.addCriteria(Criteria.where("email").regex(emailFilter, "i"));
        }

        if (nomeFilter != null && !nomeFilter.isEmpty()) {
            query.addCriteria(Criteria.where("nome").regex(nomeFilter, "i"));
        }

        return query;
    }

    public record ListUsersResult(List<User> users, Long total, Integer totalPages, Integer page, Integer size) {
    }
}
