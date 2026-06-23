package br.com.verticelabs.pdfprocessor.application.dashboard;

import br.com.verticelabs.pdfprocessor.domain.repository.PayrollDocumentRepository;
import br.com.verticelabs.pdfprocessor.domain.repository.PayrollEntryRepository;
import br.com.verticelabs.pdfprocessor.domain.repository.PersonRepository;
import br.com.verticelabs.pdfprocessor.domain.repository.RubricaRepository;
import br.com.verticelabs.pdfprocessor.infrastructure.security.ReactiveSecurityContextHelper;
import br.com.verticelabs.pdfprocessor.interfaces.dashboard.dto.DashboardChartItem;
import br.com.verticelabs.pdfprocessor.interfaces.dashboard.dto.DashboardMetric;
import br.com.verticelabs.pdfprocessor.interfaces.dashboard.dto.DashboardResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.Collections;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class DashboardUseCase {

    private static final int TOP_RUBRICAS_LIMIT = 10;

    private final PayrollDocumentRepository documentRepository;
    private final PayrollEntryRepository entryRepository;
    private final PersonRepository personRepository;
    private final RubricaRepository rubricaRepository;

    public Mono<DashboardResponse> getDashboardMetrics() {
        log.info("=== DashboardUseCase.getDashboardMetrics() INICIADO ===");

        return ReactiveSecurityContextHelper.isSuperAdmin()
                .flatMap(isSuperAdmin -> {
                    if (Boolean.TRUE.equals(isSuperAdmin)) {
                        log.info("Usuário é SUPER_ADMIN - buscando métricas agregadas de todos os tenants");
                        return getMetrics(null,
                                "Documentos processados (todos os tenants)",
                                "Total de lançamentos (todos os tenants)",
                                "Pessoas cadastradas (todos os tenants)",
                                "Rubricas ativas (todos os tenants)");
                    }
                    return ReactiveSecurityContextHelper.getTenantId()
                            .flatMap(tenantId -> {
                                log.info("Buscando métricas para tenant: {}", tenantId);
                                return getMetrics(tenantId,
                                        "Documentos processados",
                                        "Total de lançamentos",
                                        "Pessoas cadastradas",
                                        "Rubricas ativas");
                            });
                })
                .doOnSuccess(response -> log.info("✓ Dashboard metrics obtidas com sucesso"))
                .doOnError(error -> log.error("Erro ao obter métricas do dashboard", error));
    }

    private Mono<DashboardResponse> getMetrics(
            String tenantId,
            String descDocumentos,
            String descLancamentos,
            String descPessoas,
            String descRubricas
    ) {
        Mono<Long> totalDocumentosMono = tenantId == null
                ? documentRepository.countAll().defaultIfEmpty(0L)
                : documentRepository.countByTenantId(tenantId).defaultIfEmpty(0L);

        Mono<Long> totalLancamentosMono = tenantId == null
                ? entryRepository.countAll().defaultIfEmpty(0L)
                : entryRepository.countByTenantId(tenantId).defaultIfEmpty(0L);

        Mono<Long> totalPessoasMono = tenantId == null
                ? personRepository.countAll().defaultIfEmpty(0L)
                : personRepository.countByTenantId(tenantId).defaultIfEmpty(0L);

        Mono<Long> totalRubricasMono = rubricaRepository.countAllAtivoTrue().defaultIfEmpty(0L);

        Mono<List<DashboardChartItem>> graficoRubricasMono = entryRepository
                .countTopRubricas(tenantId, TOP_RUBRICAS_LIMIT)
                .collectList()
                .defaultIfEmpty(Collections.emptyList());

        Mono<List<DashboardChartItem>> graficoPessoasMono = personRepository
                .countPessoasPorAno(tenantId)
                .collectList()
                .defaultIfEmpty(Collections.emptyList());

        Mono<List<DashboardChartItem>> graficoDocumentosMono = documentRepository
                .countDocumentosPorAno(tenantId)
                .collectList()
                .defaultIfEmpty(Collections.emptyList());

        Mono<List<DashboardChartItem>> graficoLancamentosMono = entryRepository
                .countLancamentosPorAno(tenantId)
                .collectList()
                .defaultIfEmpty(Collections.emptyList());

        return Mono.zip(
                totalDocumentosMono,
                totalLancamentosMono,
                totalPessoasMono,
                totalRubricasMono,
                graficoRubricasMono,
                graficoPessoasMono,
                graficoDocumentosMono,
                graficoLancamentosMono
        ).map(tuple -> DashboardResponse.builder()
                .rubricas(buildMetric("Rubricas", tuple.getT4(), descRubricas))
                .pessoas(buildMetric("Pessoas", tuple.getT3(), descPessoas))
                .totalDocumentos(buildMetric("Total de Documentos", tuple.getT1(), descDocumentos))
                .lancamentos(buildMetric("Lançamentos", tuple.getT2(), descLancamentos))
                .graficoRubricas(tuple.getT5())
                .graficoPessoas(tuple.getT6())
                .graficoDocumentos(tuple.getT7())
                .graficoLancamentos(tuple.getT8())
                .build());
    }

    private DashboardMetric buildMetric(String titulo, Long valor, String descricao) {
        return DashboardMetric.builder()
                .titulo(titulo)
                .valor(valor)
                .descricao(descricao)
                .trend(null)
                .trendPositive(null)
                .build();
    }
}
