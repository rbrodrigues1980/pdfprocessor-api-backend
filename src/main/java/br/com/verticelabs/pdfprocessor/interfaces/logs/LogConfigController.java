package br.com.verticelabs.pdfprocessor.interfaces.logs;

import br.com.verticelabs.pdfprocessor.application.logs.LogRetentionService;
import br.com.verticelabs.pdfprocessor.domain.model.LogRetentionPeriod;
import br.com.verticelabs.pdfprocessor.interfaces.rest.dto.LogConfigRequest;
import br.com.verticelabs.pdfprocessor.interfaces.rest.dto.LogConfigResponse;
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

@Slf4j
@RestController
@RequestMapping("/config/logs")
@RequiredArgsConstructor
@Tag(name = "Configuração de Logs", description = "Retenção dos logs do sistema")
@SecurityRequirement(name = "bearerAuth")
public class LogConfigController {

    private final LogRetentionService logRetentionService;

    @GetMapping
    @Operation(summary = "Obter configuração de retenção de logs")
    public Mono<ResponseEntity<LogConfigResponse>> getConfig() {
        return logRetentionService.getConfigResponse()
                .map(ResponseEntity::ok);
    }

    @PutMapping
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @Operation(summary = "Atualizar retenção de logs", description = "Requer role SUPER_ADMIN")
    public Mono<ResponseEntity<LogConfigResponse>> updateConfig(
            @RequestBody LogConfigRequest request,
            @AuthenticationPrincipal UserDetails userDetails) {

        String user = userDetails != null ? userDetails.getUsername() : "system";
        LogRetentionPeriod period = LogRetentionPeriod.fromValue(request.retention());
        log.info("Atualizando retenção de logs: period={}, user={}", period, user);

        return logRetentionService.updateRetention(period, user)
                .map(ResponseEntity::ok);
    }
}
