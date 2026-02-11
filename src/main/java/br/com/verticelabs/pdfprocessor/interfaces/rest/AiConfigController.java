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
 * Controller para gerenciar configurações de IA.
 * Permite habilitar/desabilitar o uso de IA para PDFs escaneados via frontend.
 */
@Slf4j
@RestController
@RequestMapping("/config/ai")
@RequiredArgsConstructor
@Tag(name = "Configuração de IA", description = "APIs para gerenciar configurações de Inteligência Artificial")
@SecurityRequirement(name = "bearerAuth")
public class AiConfigController {

    private final SystemConfigRepository configRepository;
    private final GeminiConfig geminiConfig;

    /**
     * Obtém o status atual da configuração de IA.
     */
    @GetMapping
    @Operation(summary = "Obter configuração de IA", description = "Retorna o status atual da configuração de IA para PDFs escaneados")
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
    @Operation(summary = "Atualizar configuração de IA", description = "Habilita ou desabilita o uso de IA. Requer role SUPER_ADMIN.")
    public Mono<ResponseEntity<AiConfigResponse>> updateConfig(
            @RequestBody AiConfigRequest request,
            @AuthenticationPrincipal UserDetails userDetails) {

        log.info("Atualizando configuração de IA: enabled={}, model={}, user={}",
                request.enabled(), request.model(),
                userDetails != null ? userDetails.getUsername() : "unknown");

        return configRepository.findByKeyAndTenantIdIsNull(SystemConfig.KEY_AI_ENABLED)
                .switchIfEmpty(Mono.just(SystemConfig.builder()
                        .key(SystemConfig.KEY_AI_ENABLED)
                        .createdAt(Instant.now())
                        .build()))
                .flatMap(config -> {
                    config.setValue(String.valueOf(request.enabled()));
                    config.setDescription("Habilita uso de IA para PDFs escaneados");
                    config.setUpdatedAt(Instant.now());
                    config.setUpdatedBy(userDetails != null ? userDetails.getUsername() : "system");
                    return configRepository.save(config);
                })
                .flatMap(savedConfig -> {
                    // Se também tiver modelo, salvar
                    if (request.model() != null && !request.model().isEmpty()) {
                        return saveModelConfig(request.model(), userDetails)
                                .thenReturn(savedConfig);
                    }
                    return Mono.just(savedConfig);
                })
                .map(config -> {
                    log.info("✅ Configuração de IA atualizada com sucesso");
                    return ResponseEntity.ok(buildResponse(config, true));
                });
    }

    /**
     * Salva configuração do modelo.
     */
    private Mono<SystemConfig> saveModelConfig(String model, UserDetails userDetails) {
        return configRepository.findByKeyAndTenantIdIsNull(SystemConfig.KEY_AI_MODEL)
                .switchIfEmpty(Mono.just(SystemConfig.builder()
                        .key(SystemConfig.KEY_AI_MODEL)
                        .createdAt(Instant.now())
                        .build()))
                .flatMap(config -> {
                    config.setValue(model);
                    config.setDescription("Modelo de IA a ser usado");
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

        // Verificar se credenciais estão configuradas
        boolean credentialsConfigured = geminiConfig.getProjectId() != null
                && !geminiConfig.getProjectId().isEmpty();

        String statusMessage;
        if (!enabled) {
            statusMessage = "IA desabilitada. PDFs escaneados não serão processados.";
        } else if (!credentialsConfigured) {
            statusMessage = "⚠️ IA habilitada, mas credenciais do Google Cloud não configuradas.";
        } else {
            statusMessage = "✅ IA habilitada e pronta para uso.";
        }

        return new AiConfigResponse(
                enabled,
                geminiConfig.getModel(),
                credentialsConfigured,
                geminiConfig.getProjectId(),
                geminiConfig.getLocation(),
                updatedAt,
                updatedBy,
                statusMessage);
    }
}
