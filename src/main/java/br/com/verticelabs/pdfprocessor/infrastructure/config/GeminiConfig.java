package br.com.verticelabs.pdfprocessor.infrastructure.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Configuração para integração com Google Gemini (Vertex AI).
 * 
 * Propriedades configuráveis via application.yml:
 * - gemini.enabled: Habilita/desabilita o uso do Gemini
 * - gemini.project-id: ID do projeto no Google Cloud
 * - gemini.location: Região do Vertex AI (default: us-central1)
 * - gemini.model: Modelo a ser usado (default: gemini-1.5-flash)
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "gemini")
public class GeminiConfig {

    /**
     * Habilita ou desabilita o uso do Gemini AI.
     * Quando desabilitado, PDFs escaneados não serão processados.
     */
    private boolean enabled = false;

    /**
     * ID do projeto no Google Cloud.
     * Obrigatório quando enabled=true.
     */
    private String projectId;

    /**
     * Região do Vertex AI.
     * Recomendado usar us-central1 para menor latência.
     */
    private String location = "us-central1";

    /**
     * Modelo Gemini a ser usado.
     * - gemini-1.5-flash: Rápido e barato (recomendado para alto volume)
     * - gemini-1.5-pro: Mais preciso (para casos complexos)
     */
    private String model = "gemini-1.5-flash-002";

    /**
     * Número máximo de tokens na resposta.
     * 8192 é suficiente para extração de dados estruturados.
     */
    private int maxOutputTokens = 8192;

    /**
     * Temperatura para geração de texto.
     * Valor baixo (0.1) para respostas mais determinísticas e precisas.
     */
    private double temperature = 0.1;

    /**
     * Timeout em segundos para chamadas à API.
     */
    private int timeoutSeconds = 60;
}
