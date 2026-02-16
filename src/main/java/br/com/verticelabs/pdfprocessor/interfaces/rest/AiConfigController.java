package br.com.verticelabs.pdfprocessor.interfaces.rest;

import br.com.verticelabs.pdfprocessor.domain.model.SystemConfig;
import br.com.verticelabs.pdfprocessor.domain.repository.SystemConfigRepository;
import br.com.verticelabs.pdfprocessor.infrastructure.config.GeminiConfig;
import br.com.verticelabs.pdfprocessor.interfaces.rest.dto.AiConfigRequest;
import br.com.verticelabs.pdfprocessor.interfaces.rest.dto.AiConfigResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.time.Instant;

/**
 * Controller para gerenciar configurações de IA (Gemini 2.5).
 *
 * <p>Permite habilitar/desabilitar o uso de IA para PDFs escaneados via frontend,
 * e configurar os modelos principal e fallback.</p>
 *
 * <p><strong>Modelos disponíveis:</strong></p>
 * <ul>
 *   <li>{@code gemini-2.5-flash} — Modelo principal (rápido e econômico)</li>
 *   <li>{@code gemini-2.5-pro} — Modelo fallback (mais preciso, mais caro)</li>
 * </ul>
 */
@Slf4j
@RestController
@RequestMapping("/config/ai")
@RequiredArgsConstructor
@Tag(name = "Configuração de IA", description = "APIs para gerenciar configurações de Inteligência Artificial (Gemini 2.5)")
@SecurityRequirement(name = "bearerAuth")
public class AiConfigController {

    private final SystemConfigRepository configRepository;
    private final GeminiConfig geminiConfig;

    /**
     * Obtém o status atual da configuração de IA.
     */
    @GetMapping
    @Operation(summary = "Obter configuração de IA",
            description = "Retorna o status atual da configuração de IA, incluindo modelos principal e fallback")
    public Mono<ResponseEntity<AiConfigResponse>> getConfig() {
        return configRepository.findByKeyAndTenantIdIsNull(SystemConfig.KEY_AI_ENABLED)
                .map(config -> buildResponse(config, true))
                .defaultIfEmpty(buildResponse(null, false))
                .map(ResponseEntity::ok);
    }

    /**
     * Atualiza a configuração de IA.
     * Requer role SUPER_ADMIN.
     */
    @PutMapping
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @Operation(summary = "Atualizar configuração de IA",
            description = "Habilita ou desabilita o uso de IA e configura modelos. Requer role SUPER_ADMIN.")
    public Mono<ResponseEntity<AiConfigResponse>> updateConfig(
            @RequestBody AiConfigRequest request,
            @AuthenticationPrincipal UserDetails userDetails) {

        log.info("Atualizando configuração de IA: enabled={}, model={}, fallbackModel={}, user={}",
                request.enabled(), request.model(), request.fallbackModel(),
                userDetails != null ? userDetails.getUsername() : "unknown");

        return configRepository.findByKeyAndTenantIdIsNull(SystemConfig.KEY_AI_ENABLED)
                .switchIfEmpty(Mono.just(SystemConfig.builder()
                        .key(SystemConfig.KEY_AI_ENABLED)
                        .createdAt(Instant.now())
                        .build()))
                .flatMap(config -> {
                    config.setValue(String.valueOf(request.enabled()));
                    config.setDescription("Habilita uso de IA (Gemini 2.5) para PDFs escaneados e extração inteligente");
                    config.setUpdatedAt(Instant.now());
                    config.setUpdatedBy(userDetails != null ? userDetails.getUsername() : "system");
                    return configRepository.save(config);
                })
                .flatMap(savedConfig -> {
                    // Salvar modelo principal se fornecido
                    Mono<SystemConfig> modelSave = Mono.just(savedConfig);
                    if (request.model() != null && !request.model().isEmpty()) {
                        modelSave = saveModelConfig(SystemConfig.KEY_AI_MODEL,
                                request.model(), "Modelo principal de IA (Flash)", userDetails)
                                .thenReturn(savedConfig);
                    }
                    return modelSave;
                })
                .flatMap(savedConfig -> {
                    // Salvar modelo fallback se fornecido
                    Mono<SystemConfig> fallbackSave = Mono.just(savedConfig);
                    if (request.fallbackModel() != null && !request.fallbackModel().isEmpty()) {
                        fallbackSave = saveModelConfig(SystemConfig.KEY_AI_FALLBACK_MODEL,
                                request.fallbackModel(), "Modelo fallback de IA (Pro)", userDetails)
                                .thenReturn(savedConfig);
                    }
                    return fallbackSave;
                })
                .map(config -> {
                    log.info("Configuração de IA atualizada com sucesso");
                    return ResponseEntity.ok(buildResponse(config, true));
                });
    }

    /**
     * Salva configuração de modelo no banco de dados.
     */
    private Mono<SystemConfig> saveModelConfig(String key, String value,
                                                String description, UserDetails userDetails) {
        return configRepository.findByKeyAndTenantIdIsNull(key)
                .switchIfEmpty(Mono.just(SystemConfig.builder()
                        .key(key)
                        .createdAt(Instant.now())
                        .build()))
                .flatMap(config -> {
                    config.setValue(value);
                    config.setDescription(description);
                    config.setUpdatedAt(Instant.now());
                    config.setUpdatedBy(userDetails != null ? userDetails.getUsername() : "system");
                    return configRepository.save(config);
                });
    }

    /**
     * Constrói a resposta com status detalhado.
     */
    private AiConfigResponse buildResponse(SystemConfig config, boolean fromDb) {
        boolean enabled = false;
        Instant updatedAt = null;
        String updatedBy = null;

        if (config != null && config.getValue() != null) {
            enabled = Boolean.parseBoolean(config.getValue());
            updatedAt = config.getUpdatedAt();
            updatedBy = config.getUpdatedBy();
        }

        boolean credentialsConfigured = geminiConfig.getProjectId() != null
                && !geminiConfig.getProjectId().isEmpty();

        String statusMessage;
        if (!enabled) {
            statusMessage = "IA desabilitada. PDFs escaneados não serão processados pela IA.";
        } else if (!credentialsConfigured) {
            statusMessage = "IA habilitada, mas credenciais do Google Cloud não configuradas. "
                    + "Configure GOOGLE_CLOUD_PROJECT e GOOGLE_APPLICATION_CREDENTIALS.";
        } else {
            statusMessage = "IA habilitada e pronta. Modelo principal: " + geminiConfig.getModel()
                    + ", Fallback: " + geminiConfig.getFallbackModel() + ".";
        }

        return new AiConfigResponse(
                enabled,
                geminiConfig.getModel(),
                geminiConfig.getFallbackModel(),
                credentialsConfigured,
                geminiConfig.getProjectId(),
                geminiConfig.getLocation(),
                updatedAt,
                updatedBy,
                statusMessage);
    }
}
