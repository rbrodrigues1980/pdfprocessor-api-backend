package br.com.verticelabs.pdfprocessor.interfaces.logs;

import br.com.verticelabs.pdfprocessor.application.logs.ListSystemLogsUseCase;
import br.com.verticelabs.pdfprocessor.interfaces.logs.dto.SystemLogListResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.time.Instant;

@RestController
@RequestMapping("/admin/logs")
@RequiredArgsConstructor
@Tag(name = "Logs do Sistema", description = "Consulta de logs armazenados no MongoDB")
@SecurityRequirement(name = "bearerAuth")
public class SystemLogController {

    private final ListSystemLogsUseCase listSystemLogsUseCase;

    @GetMapping
    @Operation(summary = "Listar logs do sistema")
    public Mono<ResponseEntity<SystemLogListResponse>> list(
            @RequestParam(required = false) String level,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        int safeSize = Math.min(Math.max(size, 1), 200);
        return listSystemLogsUseCase.execute(level, search, from, to, page, safeSize)
                .map(ResponseEntity::ok);
    }
}
