package br.com.verticelabs.pdfprocessor.infrastructure.ai;

import br.com.verticelabs.pdfprocessor.domain.model.SystemConfig;
import br.com.verticelabs.pdfprocessor.domain.repository.SystemConfigRepository;
import br.com.verticelabs.pdfprocessor.domain.service.AiPdfExtractionService;
import br.com.verticelabs.pdfprocessor.infrastructure.config.GeminiConfig;
import com.google.cloud.vertexai.VertexAI;
import com.google.cloud.vertexai.api.GenerateContentResponse;
import com.google.cloud.vertexai.api.GenerationConfig;
import com.google.cloud.vertexai.generativeai.ContentMaker;
import com.google.cloud.vertexai.generativeai.GenerativeModel;
import com.google.cloud.vertexai.generativeai.PartMaker;
import com.google.cloud.vertexai.generativeai.ResponseHandler;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.time.Duration;

/**
 * Implementação do serviço de extração de PDFs usando Google Gemini 2.5 (Vertex AI).
 *
 * <h3>Arquitetura de Modelos</h3>
 * <ul>
 *   <li><strong>Modelo Principal (Flash)</strong>: {@code gemini-2.5-flash} — rápido e econômico (~$0.003/pg)</li>
 *   <li><strong>Modelo Fallback (Pro)</strong>: {@code gemini-2.5-pro} — mais preciso (~$0.011/pg)</li>
 * </ul>
 *
 * <h3>Fluxo de Processamento</h3>
 * <ol>
 *   <li>Converte página do PDF para imagem PNG (300 DPI)</li>
 *   <li>Envia imagem para Gemini Vision com prompt específico por tipo de documento</li>
 *   <li>Processa resposta JSON e retorna dados estruturados</li>
 *   <li>Se o modelo principal falhar, o método {@code processWithFallbackModel} usa o modelo Pro</li>
 * </ol>
 *
 * <h3>Controle de Habilitação</h3>
 * <ol>
 *   <li>{@code application.yml} → {@code gemini.enabled} (configuração estática)</li>
 *   <li>API {@code /api/v1/config/ai} → configuração dinâmica via frontend (MongoDB)</li>
 * </ol>
 *
 * <p><strong>NOTA:</strong> O SDK {@code google-cloud-vertexai} será descontinuado em Junho/2026.
 * Migração futura necessária para {@code com.google.genai:google-genai}.
 * Ver: docs/PLANO_UPGRADE_GEMINI_AI.md</p>
 *
 * @see GeminiConfig
 * @see GeminiPrompts
 * @see AiPdfExtractionService
 */
@Slf4j
@Service
public class GeminiPdfServiceImpl implements AiPdfExtractionService {

    private final GeminiConfig config;
    private final SystemConfigRepository configRepository;
    private VertexAI vertexAI;
    private GenerativeModel primaryModel;
    private GenerativeModel fallbackModel;
    private boolean clientInitialized = false;

    public GeminiPdfServiceImpl(GeminiConfig config, SystemConfigRepository configRepository) {
        this.config = config;
        this.configRepository = configRepository;
        initializeClient();
    }

    /**
     * Inicializa os clientes Gemini (modelo principal e fallback).
     * Ambos compartilham a mesma instância de VertexAI e GenerationConfig.
     */
    private void initializeClient() {
        if (config.getProjectId() == null || config.getProjectId().isEmpty()) {
            log.info("Gemini AI: Project ID não configurado. Cliente não será inicializado.");
            return;
        }

        try {
            log.info("Inicializando clientes Gemini AI...");
            log.info("  Project ID: {}", config.getProjectId());
            log.info("  Location: {}", config.getLocation());
            log.info("  Modelo principal: {}", config.getModel());
            log.info("  Modelo fallback: {}", config.getFallbackModel());
            log.info("  Max output tokens: {}", config.getMaxOutputTokens());
            log.info("  Temperature: {}", config.getTemperature());
            log.info("  Timeout: {}s", config.getTimeoutSeconds());

            this.vertexAI = new VertexAI(config.getProjectId(), config.getLocation());

            GenerationConfig generationConfig = GenerationConfig.newBuilder()
                    .setMaxOutputTokens(config.getMaxOutputTokens())
                    .setTemperature((float) config.getTemperature())
                    .build();

            // Modelo principal (Flash) — rápido e econômico
            this.primaryModel = new GenerativeModel(config.getModel(), vertexAI)
                    .withGenerationConfig(generationConfig);

            // Modelo fallback (Pro) — mais preciso, usado quando Flash falha
            this.fallbackModel = new GenerativeModel(config.getFallbackModel(), vertexAI)
                    .withGenerationConfig(generationConfig);

            this.clientInitialized = true;
            log.info("Cliente Gemini AI inicializado com sucesso - modelos: [{}] e [{}]",
                    config.getModel(), config.getFallbackModel());
        } catch (Exception e) {
            log.error("Erro ao inicializar cliente Gemini AI: {}", e.getMessage());
            log.warn("O serviço de IA ficará desabilitado. Verifique as credenciais do Google Cloud.");
        }
    }

    /**
     * Verifica se o serviço está habilitado.
     * Consulta tanto a configuração estática (application.yml) quanto
     * a configuração dinâmica (banco de dados via API).
     *
     * @return true se o serviço está disponível e habilitado
     */
    @Override
    public boolean isEnabled() {
        if (!clientInitialized || primaryModel == null) {
            return false;
        }

        try {
            Boolean dbEnabled = configRepository.findByKeyAndTenantIdIsNull(SystemConfig.KEY_AI_ENABLED)
                    .map(cfg -> Boolean.parseBoolean(cfg.getValue()))
                    .defaultIfEmpty(false)
                    .block(Duration.ofSeconds(2));

            return Boolean.TRUE.equals(dbEnabled);
        } catch (Exception e) {
            log.warn("Erro ao consultar configuração de IA do banco: {}. Usando configuração padrão (desabilitado).",
                    e.getMessage());
            return false;
        }
    }

    // ==========================================
    // MÉTODOS PÚBLICOS — MODELO PRINCIPAL (FLASH)
    // ==========================================

    @Override
    public Mono<String> extractTextFromScannedPage(byte[] pdfBytes, int pageNumber) {
        return processWithModel(primaryModel, config.getModel(), pdfBytes, pageNumber,
                GeminiPrompts.EXTRACAO_TEXTO_GENERICO);
    }

    @Override
    public Mono<String> extractPayrollData(byte[] pdfBytes, int pageNumber) {
        return processWithModel(primaryModel, config.getModel(), pdfBytes, pageNumber,
                GeminiPrompts.CONTRACHEQUE_EXTRACTION);
    }

    @Override
    public Mono<String> extractIncomeTaxData(byte[] pdfBytes, int pageNumber) {
        return processWithModel(primaryModel, config.getModel(), pdfBytes, pageNumber,
                GeminiPrompts.IR_RESUMO_EXTRACTION);
    }

    @Override
    public Mono<String> validatePayrollData(String extractedDataJson) {
        if (!isEnabled()) {
            log.warn("Gemini AI desabilitado. Validação não será executada.");
            return Mono.just("{\"valido\": true, \"inconsistencias\": [], \"sugestoes\": []}");
        }

        String prompt = String.format(GeminiPrompts.VALIDACAO_CONTRACHEQUE, extractedDataJson);

        return Mono.fromCallable(() -> {
            try {
                log.info("Validando dados com Gemini [{}]...", config.getModel());
                long startTime = System.currentTimeMillis();

                GenerateContentResponse response = primaryModel.generateContent(prompt);
                String result = ResponseHandler.getText(response);

                long duration = System.currentTimeMillis() - startTime;
                log.info("Validação concluída com Gemini [{}] em {}ms", config.getModel(), duration);

                return cleanResponse(result);
            } catch (Exception e) {
                log.error("Erro ao validar dados com Gemini [{}]: {}", config.getModel(), e.getMessage());
                return "{\"valido\": true, \"inconsistencias\": [], \"sugestoes\": [], \"erro\": \""
                        + e.getMessage() + "\"}";
            }
        }).subscribeOn(Schedulers.boundedElastic());
    }

    // ==========================================
    // MÉTODOS PÚBLICOS — MODELO FALLBACK (PRO)
    // ==========================================

    /**
     * Extrai texto de PDF escaneado usando o modelo fallback (Pro).
     * Chamado quando o modelo principal (Flash) retorna resultado com baixa confiança.
     *
     * @param pdfBytes   bytes do PDF
     * @param pageNumber número da página (1-indexed)
     * @return Mono contendo o texto extraído
     */
    public Mono<String> extractTextWithFallback(byte[] pdfBytes, int pageNumber) {
        return processWithModel(fallbackModel, config.getFallbackModel(), pdfBytes, pageNumber,
                GeminiPrompts.EXTRACAO_TEXTO_GENERICO);
    }

    /**
     * Extrai dados de contracheque usando o modelo fallback (Pro).
     * Chamado quando o modelo principal (Flash) retorna resultado com baixa confiança.
     *
     * @param pdfBytes   bytes do PDF
     * @param pageNumber número da página (1-indexed)
     * @return Mono contendo JSON com dados estruturados
     */
    public Mono<String> extractPayrollDataWithFallback(byte[] pdfBytes, int pageNumber) {
        return processWithModel(fallbackModel, config.getFallbackModel(), pdfBytes, pageNumber,
                GeminiPrompts.CONTRACHEQUE_EXTRACTION);
    }

    /**
     * Extrai dados de IR usando o modelo fallback (Pro).
     * Chamado quando o modelo principal (Flash) retorna resultado com baixa confiança.
     *
     * @param pdfBytes   bytes do PDF
     * @param pageNumber número da página (1-indexed)
     * @return Mono contendo JSON com dados estruturados do IR
     */
    public Mono<String> extractIncomeTaxDataWithFallback(byte[] pdfBytes, int pageNumber) {
        return processWithModel(fallbackModel, config.getFallbackModel(), pdfBytes, pageNumber,
                GeminiPrompts.IR_RESUMO_EXTRACTION);
    }

    /**
     * Processa uma página com um prompt customizado usando o modelo fallback (Pro).
     * Útil para cross-validation (Fase 3) onde um prompt alternativo é usado.
     *
     * @param pdfBytes   bytes do PDF
     * @param pageNumber número da página (1-indexed)
     * @param prompt     prompt customizado
     * @return Mono contendo a resposta do modelo
     */
    public Mono<String> processWithFallbackModel(byte[] pdfBytes, int pageNumber, String prompt) {
        return processWithModel(fallbackModel, config.getFallbackModel(), pdfBytes, pageNumber, prompt);
    }

    /**
     * Processa uma página com um prompt customizado usando o modelo principal (Flash).
     * Útil para cross-validation (Fase 3) onde um prompt alternativo é usado.
     *
     * @param pdfBytes   bytes do PDF
     * @param pageNumber número da página (1-indexed)
     * @param prompt     prompt customizado
     * @return Mono contendo a resposta do modelo
     */
    public Mono<String> processWithPrimaryModel(byte[] pdfBytes, int pageNumber, String prompt) {
        return processWithModel(primaryModel, config.getModel(), pdfBytes, pageNumber, prompt);
    }

    // ==========================================
    // MÉTODOS DE CONSULTA
    // ==========================================

    /**
     * Retorna o nome do modelo principal configurado.
     *
     * @return nome do modelo (ex: "gemini-2.5-flash")
     */
    public String getPrimaryModelName() {
        return config.getModel();
    }

    /**
     * Retorna o nome do modelo fallback configurado.
     *
     * @return nome do modelo (ex: "gemini-2.5-pro")
     */
    public String getFallbackModelName() {
        return config.getFallbackModel();
    }

    // ==========================================
    // MÉTODOS INTERNOS
    // ==========================================

    /**
     * Processa uma página do PDF com um modelo Gemini específico.
     *
     * @param model      instância do GenerativeModel a usar
     * @param modelName  nome do modelo para logging
     * @param pdfBytes   bytes do PDF
     * @param pageNumber número da página (1-indexed)
     * @param prompt     prompt de extração
     * @return Mono contendo a resposta processada
     */
    private Mono<String> processWithModel(GenerativeModel model, String modelName,
                                          byte[] pdfBytes, int pageNumber, String prompt) {
        if (!isEnabled()) {
            log.warn("Gemini AI desabilitado. Retornando vazio para página {}.", pageNumber);
            return Mono.just("");
        }

        return Mono.fromCallable(() -> {
            log.info("Processando página {} com Gemini [{}]...", pageNumber, modelName);
            long startTime = System.currentTimeMillis();

            try {
                // 1. Converter página do PDF para imagem PNG (300 DPI)
                byte[] imageBytes = convertPdfPageToImage(pdfBytes, pageNumber);
                log.debug("  Imagem gerada: {} bytes ({} KB)", imageBytes.length, imageBytes.length / 1024);

                // 2. Enviar para Gemini Vision (imagem + prompt)
                GenerateContentResponse response = model.generateContent(
                        ContentMaker.fromMultiModalData(
                                prompt,
                                PartMaker.fromMimeTypeAndData("image/png", imageBytes)));

                String result = ResponseHandler.getText(response);
                long duration = System.currentTimeMillis() - startTime;

                log.info("Gemini [{}] processou página {} em {}ms ({} chars na resposta)",
                        modelName, pageNumber, duration, result != null ? result.length() : 0);

                return cleanResponse(result);

            } catch (Exception e) {
                long duration = System.currentTimeMillis() - startTime;
                log.error("Erro ao processar página {} com Gemini [{}] após {}ms: {}",
                        pageNumber, modelName, duration, e.getMessage());
                throw new RuntimeException("Falha ao processar PDF com Gemini AI [" + modelName + "]", e);
            }
        }).subscribeOn(Schedulers.boundedElastic());
    }

    /**
     * Converte uma página do PDF para imagem PNG em alta resolução.
     * Usa 300 DPI para garantir boa qualidade na leitura pela IA.
     *
     * @param pdfBytes   bytes do PDF
     * @param pageNumber número da página (1-indexed)
     * @return bytes da imagem PNG
     * @throws Exception se a conversão falhar
     */
    private byte[] convertPdfPageToImage(byte[] pdfBytes, int pageNumber) throws Exception {
        try (PDDocument document = Loader.loadPDF(pdfBytes)) {
            PDFRenderer renderer = new PDFRenderer(document);

            int pageIndex = pageNumber - 1;

            if (pageIndex < 0 || pageIndex >= document.getNumberOfPages()) {
                throw new IllegalArgumentException(
                        "Página " + pageNumber + " não existe. O PDF tem " +
                                document.getNumberOfPages() + " páginas.");
            }

            BufferedImage image = renderer.renderImageWithDPI(pageIndex, 300, ImageType.RGB);

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(image, "PNG", baos);

            return baos.toByteArray();
        }
    }

    /**
     * Limpa a resposta do Gemini removendo markdown code blocks e whitespace extra.
     * O Gemini pode retornar respostas envoltas em {@code ```json ... ```}.
     *
     * @param response resposta bruta do Gemini
     * @return resposta limpa
     */
    private String cleanResponse(String response) {
        if (response == null) {
            return "";
        }

        String cleaned = response.trim();

        // Remover blocos de código markdown (```json ou ``` no início)
        if (cleaned.startsWith("```json")) {
            cleaned = cleaned.substring(7);
        } else if (cleaned.startsWith("```")) {
            cleaned = cleaned.substring(3);
        }

        // Remover ``` no final
        if (cleaned.endsWith("```")) {
            cleaned = cleaned.substring(0, cleaned.length() - 3);
        }

        return cleaned.trim();
    }
}
