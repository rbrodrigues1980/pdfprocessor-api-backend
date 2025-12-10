package br.com.verticelabs.pdfprocessor.application.dashboard;

import br.com.verticelabs.pdfprocessor.domain.repository.PayrollDocumentRepository;
import br.com.verticelabs.pdfprocessor.domain.repository.PayrollEntryRepository;
import br.com.verticelabs.pdfprocessor.domain.repository.PersonRepository;
import br.com.verticelabs.pdfprocessor.domain.repository.RubricaRepository;
import br.com.verticelabs.pdfprocessor.infrastructure.security.ReactiveSecurityContextHelper;
import br.com.verticelabs.pdfprocessor.interfaces.dashboard.dto.DashboardMetric;
import br.com.verticelabs.pdfprocessor.interfaces.dashboard.dto.DashboardResponse;
import br.com.verticelabs.pdfprocessor.interfaces.dashboard.dto.TotalPorAno;
import br.com.verticelabs.pdfprocessor.interfaces.dashboard.dto.TotalPorMes;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class DashboardUseCase {

    private final PayrollDocumentRepository documentRepository;
    private final PayrollEntryRepository entryRepository;
    private final PersonRepository personRepository;
    private final RubricaRepository rubricaRepository;

    /**
     * Obtém todas as métricas do dashboard para o tenant do usuário autenticado
     * Se for SUPER_ADMIN sem tenantId, retorna dados agregados de todos os tenants
     */
    public Mono<DashboardResponse> getDashboardMetrics() {
        log.info("=== DashboardUseCase.getDashboardMetrics() INICIADO ===");

        // Verificar se é SUPER_ADMIN
        return ReactiveSecurityContextHelper.isSuperAdmin()
                .flatMap(isSuperAdmin -> {
                    if (Boolean.TRUE.equals(isSuperAdmin)) {
                        log.info("Usuário é SUPER_ADMIN - buscando métricas agregadas de todos os tenants");
                        return getAggregatedMetrics();
                    } else {
                        // Usuário normal - buscar métricas do tenant específico
                        return ReactiveSecurityContextHelper.getTenantId()
                                .flatMap(tenantId -> {
                                    log.info("Buscando métricas para tenant: {}", tenantId);
                                    return getTenantMetrics(tenantId);
                                });
                    }
                })
                .doOnSuccess(response -> {
                    log.info("✓ Dashboard metrics obtidas com sucesso");
                })
                .doOnError(error -> {
                    log.error("Erro ao obter métricas do dashboard", error);
                });
    }

    /**
     * Obtém métricas agregadas de todos os tenants (para SUPER_ADMIN)
     */
    private Mono<DashboardResponse> getAggregatedMetrics() {
        log.info("Buscando métricas agregadas de todos os tenants para SUPER_ADMIN");

        // Buscar todas as métricas em paralelo (sem filtrar por tenant)
        Mono<Long> totalDocumentosMono = documentRepository.countAll()
                .defaultIfEmpty(0L);

        Mono<Long> totalLancamentosMono = entryRepository.countAll()
                .defaultIfEmpty(0L);

        Mono<Long> totalPessoasMono = personRepository.countAll()
                .defaultIfEmpty(0L);

        Mono<Long> totalRubricasMono = rubricaRepository.countAllAtivoTrue()
                .defaultIfEmpty(0L);

        // Buscar totais por ano e por mês
        Mono<List<TotalPorAno>> totalPorAnoMono = entryRepository.getTotalPorAnoAll()
                .collectList()
                .defaultIfEmpty(Collections.emptyList());

        Mono<List<TotalPorMes>> totalPorMesMono = entryRepository.getTotalPorMesAll()
                .collectList()
                .defaultIfEmpty(Collections.emptyList());

        // Combinar todas as métricas
        return Mono.zip(
                totalDocumentosMono,
                totalLancamentosMono,
                totalPessoasMono,
                totalRubricasMono,
                totalPorAnoMono,
                totalPorMesMono
        ).map(tuple -> {
            Long totalDocumentos = tuple.getT1();
            Long totalLancamentos = tuple.getT2();
            Long totalPessoas = tuple.getT3();
            Long totalRubricas = tuple.getT4();
            List<TotalPorAno> totalPorAno = tuple.getT5();
            List<TotalPorMes> totalPorMes = tuple.getT6();

            log.info("Métricas agregadas obtidas - Documentos: {}, Lançamentos: {}, Pessoas: {}, Rubricas: {}",
                    totalDocumentos, totalLancamentos, totalPessoas, totalRubricas);
            log.info("Totais por ano: {} registros, Totais por mês: {} registros", totalPorAno.size(), totalPorMes.size());

            return DashboardResponse.builder()
                    .totalDocumentos(DashboardMetric.builder()
                            .titulo("Total de Documentos")
                            .valor(totalDocumentos)
                            .descricao("Documentos processados (todos os tenants)")
                            .trend(null)
                            .trendPositive(null)
                            .build())
                    .lancamentos(DashboardMetric.builder()
                            .titulo("Lançamentos")
                            .valor(totalLancamentos)
                            .descricao("Total de lançamentos (todos os tenants)")
                            .trend(null)
                            .trendPositive(null)
                            .build())
                    .pessoas(DashboardMetric.builder()
                            .titulo("Pessoas")
                            .valor(totalPessoas)
                            .descricao("Pessoas cadastradas (todos os tenants)")
                            .trend(null)
                            .trendPositive(null)
                            .build())
                    .rubricas(DashboardMetric.builder()
                            .titulo("Rubricas")
                            .valor(totalRubricas)
                            .descricao("Rubricas ativas (todos os tenants)")
                            .trend(null)
                            .trendPositive(null)
                            .build())
                    .totalPorAno(totalPorAno)
                    .totalPorMes(totalPorMes)
                    .build();
        });
    }

    /**
     * Obtém métricas para um tenant específico
     */
    private Mono<DashboardResponse> getTenantMetrics(String tenantId) {
        // Buscar todas as métricas em paralelo
        Mono<Long> totalDocumentosMono = documentRepository.countByTenantId(tenantId)
                .defaultIfEmpty(0L);

        Mono<Long> totalLancamentosMono = entryRepository.countByTenantId(tenantId)
                .defaultIfEmpty(0L);

        Mono<Long> totalPessoasMono = personRepository.countByTenantId(tenantId)
                .defaultIfEmpty(0L);

        Mono<Long> totalRubricasMono = rubricaRepository.countByAtivoTrue(tenantId)
                .defaultIfEmpty(0L);

        // Buscar totais por ano e por mês
        Mono<List<TotalPorAno>> totalPorAnoMono = entryRepository.getTotalPorAno(tenantId)
                .collectList()
                .defaultIfEmpty(Collections.emptyList());

        Mono<List<TotalPorMes>> totalPorMesMono = entryRepository.getTotalPorMes(tenantId)
                .collectList()
                .defaultIfEmpty(Collections.emptyList());

        // Combinar todas as métricas
        return Mono.zip(
                totalDocumentosMono,
                totalLancamentosMono,
                totalPessoasMono,
                totalRubricasMono,
                totalPorAnoMono,
                totalPorMesMono
        ).map(tuple -> {
            Long totalDocumentos = tuple.getT1();
            Long totalLancamentos = tuple.getT2();
            Long totalPessoas = tuple.getT3();
            Long totalRubricas = tuple.getT4();
            List<TotalPorAno> totalPorAno = tuple.getT5();
            List<TotalPorMes> totalPorMes = tuple.getT6();

            log.info("Métricas obtidas - Documentos: {}, Lançamentos: {}, Pessoas: {}, Rubricas: {}",
                    totalDocumentos, totalLancamentos, totalPessoas, totalRubricas);
            log.info("Totais por ano: {} registros, Totais por mês: {} registros", totalPorAno.size(), totalPorMes.size());

            return DashboardResponse.builder()
                    .totalDocumentos(DashboardMetric.builder()
                            .titulo("Total de Documentos")
                            .valor(totalDocumentos)
                            .descricao("Documentos processados")
                            .trend(null)
                            .trendPositive(null)
                            .build())
                    .lancamentos(DashboardMetric.builder()
                            .titulo("Lançamentos")
                            .valor(totalLancamentos)
                            .descricao("Total de lançamentos")
                            .trend(null)
                            .trendPositive(null)
                            .build())
                    .pessoas(DashboardMetric.builder()
                            .titulo("Pessoas")
                            .valor(totalPessoas)
                            .descricao("Pessoas cadastradas")
                            .trend(null)
                            .trendPositive(null)
                            .build())
                    .rubricas(DashboardMetric.builder()
                            .titulo("Rubricas")
                            .valor(totalRubricas)
                            .descricao("Rubricas ativas")
                            .trend(null)
                            .trendPositive(null)
                            .build())
                    .totalPorAno(totalPorAno)
                    .totalPorMes(totalPorMes)
                    .build();
        });
    }
}