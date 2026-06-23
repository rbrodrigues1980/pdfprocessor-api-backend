package br.com.verticelabs.pdfprocessor.application.repasse;

import br.com.verticelabs.pdfprocessor.domain.exceptions.ForbiddenOperationException;
import br.com.verticelabs.pdfprocessor.domain.model.RepasseStatus;
import br.com.verticelabs.pdfprocessor.domain.repository.DeveloperRepasseRepository;
import br.com.verticelabs.pdfprocessor.domain.repository.PersonRepository;
import br.com.verticelabs.pdfprocessor.infrastructure.security.ReactiveSecurityContextHelper;
import br.com.verticelabs.pdfprocessor.interfaces.dashboard.dto.DashboardChartItem;
import br.com.verticelabs.pdfprocessor.interfaces.repasse.dto.DeveloperRepasseSummaryResponse;
import br.com.verticelabs.pdfprocessor.interfaces.repasse.dto.RepasseMonthlyChartItem;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

@Service
@RequiredArgsConstructor
public class GetDeveloperRepasseSummaryUseCase {

    private static final ZoneId ZONE_BR = ZoneId.of("America/Sao_Paulo");
    private static final int MESES_GRAFICO = 12;

    private final DeveloperRepasseRepository repasseRepository;
    private final PersonRepository personRepository;
    private final RepasseValorService repasseValorService;

    public Mono<DeveloperRepasseSummaryResponse> execute() {
        return requireSuperAdmin()
                .then(Mono.zip(
                        personRepository.countAll(),
                        personRepository.countByValidadoTrue(),
                        personRepository.countByValidadoFalseOrNull(),
                        repasseRepository.countByStatus(RepasseStatus.PENDENTE),
                        repasseRepository.countByStatus(RepasseStatus.PAGO),
                        repasseRepository.sumValorByStatus(RepasseStatus.PENDENTE),
                        repasseRepository.sumValorByStatus(RepasseStatus.PAGO),
                        repasseValorService.getConfigInfo()))
                .flatMap(tuple -> buildGraficoPorMes().map(graficoPorMes -> {
                    long totalClientes = tuple.getT1();
                    long totalValidados = tuple.getT2();
                    long totalNaoValidados = tuple.getT3();
                    long totalPendentes = tuple.getT4();
                    long totalPagos = tuple.getT5();
                    BigDecimal valorPendente = tuple.getT6();
                    BigDecimal valorPago = tuple.getT7();
                    var config = tuple.getT8();

                    return DeveloperRepasseSummaryResponse.builder()
                            .totalClientes(totalClientes)
                            .totalValidados(totalValidados)
                            .totalNaoValidados(totalNaoValidados)
                            .totalPendentes(totalPendentes)
                            .totalPagos(totalPagos)
                            .valorPendente(valorPendente)
                            .valorPago(valorPago)
                            .valorUnitario(config.valorUnitario())
                            .anoBase(config.anoBase())
                            .anoAtual(config.anoAtual())
                            .vigenciaDe(config.vigenciaDe())
                            .graficoBase(List.of(
                                    DashboardChartItem.builder().label("Validados").valor(totalValidados).build(),
                                    DashboardChartItem.builder().label("Não validados").valor(totalNaoValidados).build()))
                            .graficoRepasse(List.of(
                                    DashboardChartItem.builder().label("Pagos").valor(totalPagos).build(),
                                    DashboardChartItem.builder().label("Pendentes").valor(totalPendentes).build()))
                            .graficoPorMes(graficoPorMes)
                            .build();
                }));
    }

    private Mono<List<RepasseMonthlyChartItem>> buildGraficoPorMes() {
        return Mono.zip(
                repasseRepository.countValidacoesGroupedByMesReferencia().collectList(),
                repasseRepository.countPagosGroupedByMes().collectList())
                .map(tuple -> mergeMonthlyData(tuple.getT1(), tuple.getT2()));
    }

    private List<RepasseMonthlyChartItem> mergeMonthlyData(
            List<Map<String, Object>> validacoes,
            List<Map<String, Object>> pagos) {
        Map<String, long[]> porMes = new TreeMap<>();

        YearMonth inicio = YearMonth.now(ZONE_BR).minusMonths(MESES_GRAFICO - 1L);
        for (int i = 0; i < MESES_GRAFICO; i++) {
            String mes = inicio.plusMonths(i).toString();
            porMes.put(mes, new long[] { 0L, 0L });
        }

        for (Map<String, Object> item : validacoes) {
            String mes = item.get("mes").toString();
            if (porMes.containsKey(mes)) {
                porMes.get(mes)[0] = ((Number) item.get("total")).longValue();
            }
        }

        for (Map<String, Object> item : pagos) {
            String mes = item.get("mes").toString();
            if (porMes.containsKey(mes)) {
                porMes.get(mes)[1] = ((Number) item.get("total")).longValue();
            }
        }

        List<RepasseMonthlyChartItem> result = new ArrayList<>();
        porMes.forEach((mes, counts) -> result.add(RepasseMonthlyChartItem.builder()
                .mes(mes)
                .validacoes(counts[0])
                .pagos(counts[1])
                .build()));

        return result;
    }

    private Mono<Void> requireSuperAdmin() {
        return ReactiveSecurityContextHelper.isSuperAdmin()
                .flatMap(isSuperAdmin -> Boolean.TRUE.equals(isSuperAdmin)
                        ? Mono.empty()
                        : Mono.error(new ForbiddenOperationException("Apenas SUPER_ADMIN pode gerenciar repasses")));
    }
}
