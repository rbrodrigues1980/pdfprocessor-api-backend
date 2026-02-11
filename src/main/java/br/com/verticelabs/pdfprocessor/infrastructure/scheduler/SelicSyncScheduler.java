package br.com.verticelabs.pdfprocessor.infrastructure.scheduler;

import br.com.verticelabs.pdfprocessor.application.selic.TaxaSelicService;
import br.com.verticelabs.pdfprocessor.domain.model.SelicMensalEntity;
import br.com.verticelabs.pdfprocessor.infrastructure.bcb.BcbSelicMensalClient;
import br.com.verticelabs.pdfprocessor.infrastructure.mongodb.SpringDataSelicMensalRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Scheduler para sincronização periódica de taxas SELIC com o BCB.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SelicSyncScheduler {

    private final TaxaSelicService taxaSelicService;
    private final BcbSelicMensalClient bcbSelicMensalClient;
    private final SpringDataSelicMensalRepository selicMensalRepository;

    /**
     * Sincroniza ao iniciar a aplicação.
     */
    @EventListener(ApplicationReadyEvent.class)
    public void syncOnStartup() {
        log.info("Iniciando sincronização de taxas SELIC ao startup...");

        // Sincronizar SELIC por período COPOM
        taxaSelicService.contarRegistros()
                .subscribe(count -> {
                    if (count == 0) {
                        log.info("Nenhum registro de SELIC COPOM encontrado. Executando sincronização inicial...");
                        taxaSelicService.sincronizarCompleto()
                                .subscribe(result -> log.info("Sincronização COPOM concluída: {} registros",
                                        result.getRegistrosProcessados()));
                    } else {
                        log.info("Já existem {} registros de SELIC COPOM.", count);
                    }
                });

        // Sincronizar SELIC mensal (série 4390)
        sincronizarSelicMensal();
    }

    /**
     * Sincroniza taxas SELIC mensais do BCB (série 4390).
     * Utilizada para cálculos da Receita Federal.
     */
    private void sincronizarSelicMensal() {
        log.info("Sincronizando taxas SELIC mensais (série 4390)...");

        selicMensalRepository.count()
                .subscribe(count -> {
                    if (count < 400) { // Se tiver menos que ~33 anos de dados
                        log.info("Sincronizando SELIC mensal... ({} registros existentes)", count);

                        bcbSelicMensalClient.fetchSelicMensal()
                                .flatMap(this::salvarOuAtualizarMensal)
                                .count()
                                .subscribe(total -> log.info("SELIC mensal sincronizada: {} registros processados",
                                        total));
                    } else {
                        log.info("SELIC mensal já sincronizada: {} registros", count);
                    }
                });
    }

    /**
     * Salva ou atualiza taxa SELIC mensal.
     */
    private reactor.core.publisher.Mono<SelicMensalEntity> salvarOuAtualizarMensal(SelicMensalEntity taxa) {
        return selicMensalRepository.findByAnoAndMes(taxa.getAno(), taxa.getMes())
                .flatMap(existing -> {
                    taxa.setId(existing.getId());
                    return selicMensalRepository.save(taxa);
                })
                .switchIfEmpty(reactor.core.publisher.Mono.defer(() -> {
                    taxa.setId(UUID.randomUUID().toString());
                    return selicMensalRepository.save(taxa);
                }));
    }

    /**
     * Sincroniza diariamente às 06:00 (horário de Brasília).
     * Cron: segundo minuto hora dia mês dia-semana
     */
    @Scheduled(cron = "0 0 6 * * *", zone = "America/Sao_Paulo")
    public void syncDaily() {
        log.info("Executando sincronização diária de taxas SELIC...");

        taxaSelicService.sincronizarComBcb()
                .subscribe(result -> {
                    log.info("Sincronização COPOM diária concluída: {} registros processados, {} erros",
                            result.getRegistrosProcessados(), result.getErros());
                });

        // Atualizar SELIC mensal
        sincronizarSelicMensal();
    }
}
