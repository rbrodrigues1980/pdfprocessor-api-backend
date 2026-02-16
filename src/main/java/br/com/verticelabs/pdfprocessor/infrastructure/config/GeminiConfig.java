package br.com.verticelabs.pdfprocessor.infrastructure.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Configuração para integração com Google Gemini (Vertex AI).
 *
 * <p>Propriedades configuráveis via application.yml ou variáveis de ambiente:</p>
 * <ul>
 *   <li>{@code gemini.enabled} — Habilita/desabilita o uso do Gemini</li>
 *   <li>{@code gemini.project-id} — ID do projeto no Google Cloud (env: GOOGLE_CLOUD_PROJECT)</li>
 *   <li>{@code gemini.location} — Região do Vertex AI (env: GEMINI_LOCATION, default: us-central1)</li>
 *   <li>{@code gemini.model} — Modelo principal (env: GEMINI_MODEL, default: gemini-2.5-flash)</li>
 *   <li>{@code gemini.fallback-model} — Modelo fallback (env: GEMINI_FALLBACK_MODEL, default: gemini-2.5-pro)</li>
 * </ul>
 *
 * <p><strong>Modelos disponíveis:</strong></p>
 * <ul>
 *   <li>{@code gemini-2.5-flash} — Rápido e barato (~$0.003/página). Recomendado para alto volume.</li>
 *   <li>{@code gemini-2.5-pro} — Mais preciso (~$0.011/página). Usado como fallback quando Flash falha.</li>
 * </ul>
 *
 * @see <a href="https://cloud.google.com/vertex-ai/generative-ai/docs/models/gemini/2-5-flash">Gemini 2.5 Flash</a>
 * @see <a href="https://cloud.google.com/vertex-ai/generative-ai/docs/models/gemini/2-5-pro">Gemini 2.5 Pro</a>
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "gemini")
public class GeminiConfig {

    /**
     * Habilita ou desabilita o uso do Gemini AI.
     * Quando desabilitado, PDFs escaneados não serão processados pela IA.
     */
    private boolean enabled = false;

    /**
     * ID do projeto no Google Cloud.
     * Obrigatório quando enabled=true.
     * Configurável via variável de ambiente: GOOGLE_CLOUD_PROJECT
     */
    private String projectId;

    /**
     * Região do Vertex AI.
     * Recomendado usar us-central1 para menor latência e maior disponibilidade de modelos.
     * Configurável via variável de ambiente: GEMINI_LOCATION
     */
    private String location = "us-central1";

    /**
     * Modelo Gemini principal (rápido e econômico).
     * Usado como primeira opção para todas as extrações.
     * Configurável via variável de ambiente: GEMINI_MODEL
     *
     * <p>Custo estimado: ~$0.003 por página de PDF.</p>
     */
    private String model = "gemini-2.5-flash";

    /**
     * Modelo Gemini de fallback (mais preciso, mais caro).
     * Usado automaticamente quando o modelo principal falha na validação
     * ou quando a confiança da extração é baixa.
     * Configurável via variável de ambiente: GEMINI_FALLBACK_MODEL
     *
     * <p>Custo estimado: ~$0.011 por página de PDF.</p>
     */
    private String fallbackModel = "gemini-2.5-pro";

    /**
     * Número máximo de tokens na resposta.
     * 8192 é suficiente para extração de dados estruturados de qualquer tipo de documento.
     */
    private int maxOutputTokens = 8192;

    /**
     * Temperatura para geração de texto.
     * Valor baixo (0.1) para respostas mais determinísticas e precisas na extração de dados.
     * Valores mais altos (0.5+) aumentam variabilidade — NÃO recomendado para extração.
     */
    private double temperature = 0.1;

    /**
     * Timeout em segundos para chamadas à API.
     * Gemini 2.5 pode ser mais lento que 1.5 em documentos complexos.
     * 120 segundos é um valor seguro para a maioria dos casos.
     */
    private int timeoutSeconds = 120;
}
