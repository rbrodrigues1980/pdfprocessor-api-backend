package br.com.verticelabs.pdfprocessor.infrastructure.config;

import jakarta.annotation.PostConstruct;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Configuração de CORS.
 * 
 * As origens permitidas podem ser configuradas de duas formas:
 * 
 * 1) Via application.yml (lista):
 *    app.cors.allowed-origins:
 *      - http://localhost:5173
 *      - https://meu-frontend.run.app
 * 
 * 2) Via variável de ambiente (vírgula separada) — ideal para Cloud Run:
 *    APP_CORS_EXTRA_ORIGINS=https://meu-frontend.run.app,https://meu-dominio.com
 * 
 * Ambas são combinadas automaticamente.
 */
@Data
@Slf4j
@Configuration
@EnableConfigurationProperties
@ConfigurationProperties(prefix = "app.cors")
public class CorsProperties {
    
    /**
     * Lista de origens permitidas definida no application.yml.
     */
    private List<String> allowedOrigins = new ArrayList<>();

    /**
     * Combina as origens do application.yml com as da variável de ambiente APP_CORS_EXTRA_ORIGINS.
     */
    public List<String> getAllowedOrigins() {
        List<String> combined = new ArrayList<>(allowedOrigins);

        String extraOrigins = System.getenv("APP_CORS_EXTRA_ORIGINS");
        if (extraOrigins != null && !extraOrigins.isBlank()) {
            List<String> extras = Arrays.stream(extraOrigins.split(","))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .collect(Collectors.toList());
            combined.addAll(extras);
        }

        return combined.stream().distinct().collect(Collectors.toList());
    }

    @PostConstruct
    public void logCorsConfig() {
        List<String> origins = getAllowedOrigins();
        log.info("CORS allowed origins: {}", origins);
    }
}

