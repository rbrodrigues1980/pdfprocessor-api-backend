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
 * Implementação do serviço de extração de PDFs usando Google Gemini (Vertex
 * AI).
 * 
 * Fluxo:
 * 1. Converte página do PDF para imagem PNG
 * 2. Envia imagem para Gemini Vision
 * 3. Processa resposta e retorna dados estruturados
 * 
 * A habilitação pode ser controlada via:
 * 1. application.yml (gemini.enabled) - configuração estática
 * 2. API /api/v1/config/ai - configuração dinâmica via frontend
 */
@Slf4j
@Service
public class GeminiPdfServiceImpl implements AiPdfExtractionService {

    private final GeminiConfig config;
    private final SystemConfigRepository configRepository;
    private VertexAI vertexAI;
    private GenerativeModel model;
    private boolean clientInitialized = false;

    public GeminiPdfServiceImpl(GeminiConfig config, SystemConfigRepository configRepository) {
        this.config = config;
        this.configRepository = configRepository;
        initializeClient();
    }

    private void initializeClient() {
        // Verificar se há projeto configurado
        if (config.getProjectId() == null || config.getProjectId().isEmpty()) {
            log.info("Gemini AI: Project ID não configurado. Cliente não será inicializado.");
            return;
        }

        try {
            log.info("Inicializando cliente Gemini AI...");
            log.info("  - Project ID: {}", config.getProjectId());
            log.info("  - Location: {}", config.getLocation());
            log.info("  - Model: {}", config.getModel());

            this.vertexAI = new VertexAI(config.getProjectId(), config.getLocation());

            GenerationConfig generationConfig = GenerationConfig.newBuilder()
                    .setMaxOutputTokens(config.getMaxOutputTokens())
                    .setTemperature((float) config.getTemperature())
                    .build();

            this.model = new GenerativeModel(config.getModel(), vertexAI)
                    .withGenerationConfig(generationConfig);

            this.clientInitialized = true;
            log.info("✅ Cliente Gemini AI inicializado com sucesso!");
        } catch (Exception e) {
            log.error("❌ Erro ao inicializar cliente Gemini AI: {}", e.getMessage());
            log.warn("O serviço de IA ficará desabilitado. Verifique as credenciais do Google Cloud.");
        }
    }

    /**
     * Verifica se o serviço está habilitado.
     * Consulta tanto a configuração estática (application.yml) quanto
     * a configuração dinâmica (banco de dados via API).
     */
    @Override
    public boolean isEnabled() {
        // 1. Verificar se cliente foi inicializado
        if (!clientInitialized || model == null) {
            return false;
        }

        // 2. Consultar configuração do banco de dados (com cache de 5 segundos)
        try {
            Boolean dbEnabled = configRepository.findByKeyAndTenantIdIsNull(SystemConfig.KEY_AI_ENABLED)
                    .map(cfg -> Boolean.parseBoolean(cfg.getValue()))
                    .defaultIfEmpty(false) // Default: desabilitado se não existe no DB
                    .block(Duration.ofSeconds(2));

            return Boolean.TRUE.equals(dbEnabled);
        } catch (Exception e) {
            log.warn("Erro ao consultar configuração de IA do banco: {}. Usando configuração padrão (desabilitado).",
                    e.getMessage());
            return false;
        }
    }

    @Override
    public Mono<String> extractTextFromScannedPage(byte[] pdfBytes, int pageNumber) {
        return processWithGemini(pdfBytes, pageNumber, GeminiPrompts.EXTRACAO_TEXTO_GENERICO);
    }

    @Override
    public Mono<String> extractPayrollData(byte[] pdfBytes, int pageNumber) {
        return processWithGemini(pdfBytes, pageNumber, GeminiPrompts.CONTRACHEQUE_EXTRACTION);
    }

    @Override
    public Mono<String> extractIncomeTaxData(byte[] pdfBytes, int pageNumber) {
        return processWithGemini(pdfBytes, pageNumber, GeminiPrompts.IR_RESUMO_EXTRACTION);
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
                GenerateContentResponse response = model.generateContent(prompt);
                return ResponseHandler.getText(response);
            } catch (Exception e) {
                log.error("Erro ao validar dados com Gemini: {}", e.getMessage());
                return "{\"valido\": true, \"inconsistencias\": [], \"sugestoes\": [], \"erro\": \"" + e.getMessage()
                        + "\"}";
            }
        }).subscribeOn(Schedulers.boundedElastic());
    }

    /**
     * Processa uma página do PDF com Gemini Vision.
     */
    private Mono<String> processWithGemini(byte[] pdfBytes, int pageNumber, String prompt) {
        if (!isEnabled()) {
            log.warn("Gemini AI desabilitado. Retornando vazio para página {}.", pageNumber);
            return Mono.just("");
        }

        return Mono.fromCallable(() -> {
            log.info("🤖 Processando página {} com Gemini AI...", pageNumber);
            long startTime = System.currentTimeMillis();

            try {
                // 1. Converter página do PDF para imagem
                byte[] imageBytes = convertPdfPageToImage(pdfBytes, pageNumber);
                log.debug("  - Imagem gerada: {} bytes", imageBytes.length);

                // 2. Enviar para Gemini Vision
                GenerateContentResponse response = model.generateContent(
                        ContentMaker.fromMultiModalData(
                                prompt,
                                PartMaker.fromMimeTypeAndData("image/png", imageBytes)));

                String result = ResponseHandler.getText(response);
                long duration = System.currentTimeMillis() - startTime;

                log.info("✅ Gemini processou página {} em {} ms", pageNumber, duration);
                log.debug("  - Resposta: {} caracteres", result != null ? result.length() : 0);

                // Limpar resposta (remover markdown code blocks se presentes)
                return cleanResponse(result);

            } catch (Exception e) {
                log.error("❌ Erro ao processar página {} com Gemini: {}", pageNumber, e.getMessage());
                throw new RuntimeException("Falha ao processar PDF com Gemini AI", e);
            }
        }).subscribeOn(Schedulers.boundedElastic());
    }

    /**
     * Converte uma página do PDF para imagem PNG.
     */
    private byte[] convertPdfPageToImage(byte[] pdfBytes, int pageNumber) throws Exception {
        try (PDDocument document = Loader.loadPDF(pdfBytes)) {
            PDFRenderer renderer = new PDFRenderer(document);

            // pageNumber é 1-indexed, mas PDFRenderer usa 0-indexed
            int pageIndex = pageNumber - 1;

            if (pageIndex < 0 || pageIndex >= document.getNumberOfPages()) {
                throw new IllegalArgumentException(
                        "Página " + pageNumber + " não existe. O PDF tem " +
                                document.getNumberOfPages() + " páginas.");
            }

            // Renderizar página como imagem (300 DPI para boa qualidade)
            BufferedImage image = renderer.renderImageWithDPI(pageIndex, 300, ImageType.RGB);

            // Converter para PNG
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(image, "PNG", baos);

            return baos.toByteArray();
        }
    }

    /**
     * Limpa a resposta do Gemini removendo markdown code blocks.
     */
    private String cleanResponse(String response) {
        if (response == null) {
            return "";
        }

        String cleaned = response.trim();

        // Remover blocos de código markdown
        if (cleaned.startsWith("```json")) {
            cleaned = cleaned.substring(7);
        } else if (cleaned.startsWith("```")) {
            cleaned = cleaned.substring(3);
        }

        if (cleaned.endsWith("```")) {
            cleaned = cleaned.substring(0, cleaned.length() - 3);
        }

        return cleaned.trim();
    }
}
