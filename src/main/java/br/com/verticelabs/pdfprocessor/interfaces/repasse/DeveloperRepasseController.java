package br.com.verticelabs.pdfprocessor.interfaces.repasse;

import br.com.verticelabs.pdfprocessor.application.repasse.*;
import br.com.verticelabs.pdfprocessor.domain.model.RepasseStatus;
import br.com.verticelabs.pdfprocessor.interfaces.repasse.dto.DeveloperRepasseListResponse;
import br.com.verticelabs.pdfprocessor.interfaces.repasse.dto.DeveloperRepasseResponse;
import br.com.verticelabs.pdfprocessor.interfaces.repasse.dto.DeveloperRepasseSummaryResponse;
import br.com.verticelabs.pdfprocessor.interfaces.repasse.dto.MarkRepasseBulkPaidRequest;
import br.com.verticelabs.pdfprocessor.interfaces.repasse.dto.MarkRepasseBulkPaidResponse;
import br.com.verticelabs.pdfprocessor.interfaces.repasse.dto.MarkRepassePaidRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.time.Instant;

@Slf4j
@RestController
@RequestMapping("/admin/repasse")
@RequiredArgsConstructor
public class DeveloperRepasseController {

    private final ListDeveloperRepasseUseCase listDeveloperRepasseUseCase;
    private final GetDeveloperRepasseSummaryUseCase getDeveloperRepasseSummaryUseCase;
    private final MarkRepasseAsPaidUseCase markRepasseAsPaidUseCase;
    private final MarkRepasseBulkAsPaidUseCase markRepasseBulkAsPaidUseCase;
    private final SyncDeveloperRepasseUseCase syncDeveloperRepasseUseCase;

    @GetMapping
    public Mono<ResponseEntity<DeveloperRepasseListResponse>> list(
            @RequestParam(required = false) RepasseStatus status,
            @RequestParam(required = false) String mesReferencia,
            @RequestParam(required = false) String tenantId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant validadoDe,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant validadoAte,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant pagoDe,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant pagoAte,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        RepasseListFilter filter = new RepasseListFilter(
                status, mesReferencia, tenantId, validadoDe, validadoAte, pagoDe, pagoAte);
        return listDeveloperRepasseUseCase.execute(filter, page, size)
                .map(ResponseEntity::ok);
    }

    @GetMapping("/summary")
    public Mono<ResponseEntity<DeveloperRepasseSummaryResponse>> summary() {
        return getDeveloperRepasseSummaryUseCase.execute()
                .map(ResponseEntity::ok);
    }

    @PatchMapping("/{id}/pay")
    public Mono<ResponseEntity<DeveloperRepasseResponse>> markAsPaid(
            @PathVariable String id,
            @RequestBody(required = false) MarkRepassePaidRequest request) {
        return markRepasseAsPaidUseCase.execute(id, request)
                .map(ResponseEntity::ok);
    }

    @PatchMapping("/pay-bulk")
    public Mono<ResponseEntity<MarkRepasseBulkPaidResponse>> markManyAsPaid(
            @RequestBody MarkRepasseBulkPaidRequest request) {
        return markRepasseBulkAsPaidUseCase.execute(request)
                .map(ResponseEntity::ok);
    }

    @PostMapping("/sync")
    public Mono<ResponseEntity<SyncDeveloperRepasseUseCase.SyncResult>> sync() {
        return syncDeveloperRepasseUseCase.execute()
                .map(result -> ResponseEntity.ok(result));
    }
}
