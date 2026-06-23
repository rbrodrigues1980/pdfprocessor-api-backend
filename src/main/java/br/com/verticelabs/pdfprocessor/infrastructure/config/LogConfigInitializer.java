package br.com.verticelabs.pdfprocessor.infrastructure.config;

import br.com.verticelabs.pdfprocessor.application.logs.LogRetentionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@Order(2)
@RequiredArgsConstructor
public class LogConfigInitializer implements CommandLineRunner {

    private final LogRetentionService logRetentionService;

    @Override
    public void run(String... args) {
        logRetentionService.ensureQueryIndexes()
                .then(logRetentionService.getRetentionPeriod())
                .flatMap(logRetentionService::applyTtlIndex)
                .doOnSuccess(v -> log.info("Configuração de logs inicializada"))
                .doOnError(error -> log.warn("Aviso ao inicializar configuração de logs: {}", error.getMessage()))
                .onErrorComplete()
                .subscribe();
    }
}
