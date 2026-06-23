package br.com.verticelabs.pdfprocessor.interfaces.rest;

import br.com.verticelabs.pdfprocessor.application.repasse.RepasseValorService;
import br.com.verticelabs.pdfprocessor.interfaces.rest.dto.RepasseConfigRequest;
import br.com.verticelabs.pdfprocessor.interfaces.rest.dto.RepasseConfigResponse;
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
@RequestMapping("/config/repasse")
@RequiredArgsConstructor
@Tag(name = "Configuração de Repasse", description = "Valor unitário e ano base do repasse ao desenvolvedor")
@SecurityRequirement(name = "bearerAuth")
public class RepasseConfigController {

    private final RepasseValorService repasseValorService;

    @GetMapping
    @Operation(summary = "Obter configuração de repasse")
    public Mono<ResponseEntity<RepasseConfigResponse>> getConfig() {
        return repasseValorService.getConfigResponse()
                .map(ResponseEntity::ok);
    }

    @PutMapping
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @Operation(summary = "Atualizar configuração de repasse", description = "Requer role SUPER_ADMIN")
    public Mono<ResponseEntity<RepasseConfigResponse>> updateConfig(
            @RequestBody RepasseConfigRequest request,
            @AuthenticationPrincipal UserDetails userDetails) {

        String user = userDetails != null ? userDetails.getUsername() : "system";
        log.info("Atualizando config de repasse: valor={}, anoBase={}, vigenciaDe={}, user={}",
                request.valorUnitario(), request.anoBase(), request.vigenciaDe(), user);

        int anoBase = request.anoBase() != null ? request.anoBase() : java.time.Year.now().getValue();

        return repasseValorService.updateConfig(
                        request.valorUnitario(), anoBase, request.vigenciaDe(), user)
                .map(ResponseEntity::ok);
    }
}
