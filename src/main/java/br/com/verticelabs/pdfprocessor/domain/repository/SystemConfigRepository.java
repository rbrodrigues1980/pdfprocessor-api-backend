package br.com.verticelabs.pdfprocessor.domain.repository;

import br.com.verticelabs.pdfprocessor.domain.model.SystemConfig;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

/**
 * Repositório para configurações do sistema.
 */
@Repository
public interface SystemConfigRepository extends ReactiveMongoRepository<SystemConfig, String> {

    /**
     * Busca configuração por chave (global).
     */
    Mono<SystemConfig> findByKeyAndTenantIdIsNull(String key);

    /**
     * Busca configuração por chave e tenant.
     */
    Mono<SystemConfig> findByKeyAndTenantId(String key, String tenantId);

    /**
     * Busca configuração por chave (global ou específica do tenant).
     */
    default Mono<SystemConfig> findConfig(String key, String tenantId) {
        if (tenantId != null) {
            return findByKeyAndTenantId(key, tenantId)
                    .switchIfEmpty(findByKeyAndTenantIdIsNull(key));
        }
        return findByKeyAndTenantIdIsNull(key);
    }
}
