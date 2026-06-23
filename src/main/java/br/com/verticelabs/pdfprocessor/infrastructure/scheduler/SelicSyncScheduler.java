package br.com.verticelabs.pdfprocessor.infrastructure.scheduler;

import br.com.verticelabs.pdfprocessor.application.selic.TaxaSelicService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Scheduler para sincronização periódica de taxas SELIC com o BCB.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SelicSyncScheduler {

    private final TaxaSelicService taxaSelicService;

    /**
     * Sincroniza ao iniciar a aplicação.
     */
    @EventListener(ApplicationReadyEvent.class)
    public void syncOnStartup() {
        log.info("Iniciando sincronização de taxas SELIC ao startup...");

        taxaSelicService.contarRegistros()
                .subscribe(count -> {
                    if (count == 0) {
                        log.info("Nenhum registro de SELIC COPOM encontrado. Executando sincronização inicial...");
                        taxaSelicService.sincronizarCompleto()
                                .subscribe(result -> log.info(
                                        "Sincronização inicial concluída: {} COPOM, {} mensais (série 4390)",
                                        result.getRegistrosProcessados(),
                                        result.getRegistrosMensaisProcessados()));
                    } else {
                        log.info("Já existem {} registros de SELIC COPOM. Atualizando mensais...", count);
                        taxaSelicService.sincronizarSelicMensalComBcb()
                                .subscribe(total -> log.info("SELIC mensal atualizada no startup: {} registros", total));
                    }
                });
    }

    /**
     * Sincroniza diariamente às 06:00 (horário de Brasília).
     */
    @Scheduled(cron = "0 0 6 * * *", zone = "America/Sao_Paulo")
    public void syncDaily() {
        log.info("Executando sincronização diária de taxas SELIC...");

        taxaSelicService.sincronizarCompleto()
                .subscribe(result -> log.info(
                        "Sincronização diária concluída: {} COPOM ({} erros), {} mensais (série 4390)",
                        result.getRegistrosProcessados(),
                        result.getErros(),
                        result.getRegistrosMensaisProcessados()));
    }
}
