package br.com.verticelabs.pdfprocessor.domain.repository;

import br.com.verticelabs.pdfprocessor.application.repasse.RepasseListFilter;
import br.com.verticelabs.pdfprocessor.domain.model.DeveloperRepasse;
import br.com.verticelabs.pdfprocessor.domain.model.RepasseStatus;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.util.Map;

public interface DeveloperRepasseRepository {

    Mono<DeveloperRepasse> save(DeveloperRepasse repasse);

    Mono<DeveloperRepasse> findById(String id);

    Mono<DeveloperRepasse> findByPersonId(String personId);

    Mono<Boolean> existsByPersonId(String personId);

    Flux<DeveloperRepasse> findByFilters(RepasseListFilter filter, int page, int size);

    Mono<Long> countByFilters(RepasseListFilter filter);

    Mono<Long> countByStatus(RepasseStatus status);

    Mono<BigDecimal> sumValorByStatus(RepasseStatus status);

    Flux<Map<String, Object>> countValidacoesGroupedByMesReferencia();

    Flux<Map<String, Object>> countPagosGroupedByMes();

    Flux<DeveloperRepasse> findByStatus(RepasseStatus status);
}
