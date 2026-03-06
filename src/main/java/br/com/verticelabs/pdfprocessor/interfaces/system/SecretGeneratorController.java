package br.com.verticelabs.pdfprocessor.interfaces.system;

import br.com.verticelabs.pdfprocessor.domain.service.SecretGeneratorService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Controller para geração de chaves criptográficas fortes.
 * Delega toda lógica ao {@link SecretGeneratorService}.
 */
@Slf4j
@RestController
@RequestMapping("/system/secrets")
@RequiredArgsConstructor
@Tag(name = "Geração de Secrets", description = "API para gerar chaves criptográficas fortes (JWT, API keys, tokens)")
@SecurityRequirement(name = "bearerAuth")
public class SecretGeneratorController {

    private final SecretGeneratorService secretGeneratorService;

    @GetMapping("/generate")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @Operation(
            summary = "Gerar chave criptográfica forte",
            description = "Gera chaves usando SecureRandom (DRBG). Ideal para JWT_SECRET, API keys, tokens. Requer SUPER_ADMIN.")
    public Mono<ResponseEntity<Map<String, Object>>> generateSecret(
            @Parameter(description = "Tamanho em bits (256, 384, 512, 1024)", example = "512")
            @RequestParam(defaultValue = "512") int bits,

            @Parameter(description = "Formato: base64, base64url, hex", example = "base64url")
            @RequestParam(defaultValue = "base64url") String format,

            @Parameter(description = "Quantidade de chaves (1-10)", example = "1")
            @RequestParam(defaultValue = "1") int count) {

        try {
            secretGeneratorService.validateParams(bits, format, count);
        } catch (IllegalArgumentException e) {
            return Mono.just(ResponseEntity.badRequest().body(Map.of("error", e.getMessage())));
        }

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("algorithm", secretGeneratorService.getAlgorithm());
        response.put("bits", bits);
        response.put("bytes", bits / 8);
        response.put("format", format);
        response.put("generatedAt", Instant.now().toString());

        if (count == 1) {
            response.put("secret", secretGeneratorService.generate(bits, format));
        } else {
            response.put("secrets", secretGeneratorService.generateBatch(bits, format, count));
        }

        log.info("Secret gerada: {}bits, formato={}, count={}", bits, format, count);
        return Mono.just(ResponseEntity.ok(response));
    }

    @GetMapping("/generate/preset/{type}")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @Operation(
            summary = "Gerar chave com preset",
            description = "Gera chave otimizada para caso de uso: jwt, apikey, refresh, encryption. Requer SUPER_ADMIN.")
    public Mono<ResponseEntity<Map<String, Object>>> generatePreset(
            @Parameter(description = "Tipo: jwt, apikey, refresh, encryption")
            @PathVariable String type) {

        try {
            Map<String, Object> result = secretGeneratorService.generatePreset(type);
            log.info("Secret preset gerada: type={}", type);
            return Mono.just(ResponseEntity.ok(result));
        } catch (IllegalArgumentException e) {
            return Mono.just(ResponseEntity.badRequest().body(Map.of("error", e.getMessage())));
        }
    }
}
