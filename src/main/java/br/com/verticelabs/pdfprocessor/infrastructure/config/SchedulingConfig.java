package br.com.verticelabs.pdfprocessor.infrastructure.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Configuração para habilitar tarefas agendadas (scheduling).
 */
@Configuration
@EnableScheduling
public class SchedulingConfig {
    // Habilita @Scheduled nas classes da aplicação
}
