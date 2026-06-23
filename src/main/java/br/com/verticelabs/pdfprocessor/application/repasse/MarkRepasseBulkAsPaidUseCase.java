package br.com.verticelabs.pdfprocessor.application.repasse;

import br.com.verticelabs.pdfprocessor.domain.exceptions.ForbiddenOperationException;
import br.com.verticelabs.pdfprocessor.domain.model.DeveloperRepasse;
import br.com.verticelabs.pdfprocessor.domain.model.RepasseStatus;
import br.com.verticelabs.pdfprocessor.domain.repository.DeveloperRepasseRepository;
import br.com.verticelabs.pdfprocessor.infrastructure.security.ReactiveSecurityContextHelper;
import br.com.verticelabs.pdfprocessor.interfaces.repasse.dto.MarkRepasseBulkPaidRequest;
import br.com.verticelabs.pdfprocessor.interfaces.repasse.dto.MarkRepasseBulkPaidResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class MarkRepasseBulkAsPaidUseCase {

    private final DeveloperRepasseRepository repasseRepository;
    private final RepassePaymentApplier paymentApplier;

    public Mono<MarkRepasseBulkPaidResponse> execute(MarkRepasseBulkPaidRequest request) {
        List<String> ids = request != null ? request.getIds() : null;
        if (ids == null || ids.isEmpty()) {
            return Mono.error(new IllegalArgumentException("Informe ao menos um repasse para dar baixa"));
        }

        List<String> uniqueIds = ids.stream().distinct().toList();

        return requireSuperAdmin()
                .then(ReactiveSecurityContextHelper.getUserId().defaultIfEmpty("SUPER_ADMIN"))
                .flatMap(userId -> Flux.fromIterable(uniqueIds)
                        .concatMap(id -> processOne(id, userId, request))
                        .reduce(new BulkAccumulator(), BulkAccumulator::add)
                        .map(acc -> MarkRepasseBulkPaidResponse.builder()
                                .pagos(acc.pagos())
                                .valorTotal(acc.valorTotal())
                                .build()));
    }

    private Mono<BulkItemResult> processOne(String id, String userId, MarkRepasseBulkPaidRequest request) {
        return repasseRepository.findById(id)
                .flatMap(repasse -> {
                    if (repasse.getStatus() == RepasseStatus.PAGO) {
                        return Mono.just(BulkItemResult.skipped());
                    }
                    return markAsPaid(repasse, userId, request);
                })
                .defaultIfEmpty(BulkItemResult.skipped());
    }

    private Mono<BulkItemResult> markAsPaid(
            DeveloperRepasse repasse,
            String userId,
            MarkRepasseBulkPaidRequest request) {
        BigDecimal valor = repasse.getValorUnitario() != null ? repasse.getValorUnitario() : BigDecimal.ZERO;
        paymentApplier.apply(repasse, userId, request);

        return repasseRepository.save(repasse)
                .doOnSuccess(r -> log.info("Repasse pago em lote: id={}, pagoPor={}", r.getId(), userId))
                .thenReturn(BulkItemResult.paid(valor));
    }

    private Mono<Void> requireSuperAdmin() {
        return ReactiveSecurityContextHelper.isSuperAdmin()
                .flatMap(isSuperAdmin -> Boolean.TRUE.equals(isSuperAdmin)
                        ? Mono.empty()
                        : Mono.error(new ForbiddenOperationException("Apenas SUPER_ADMIN pode gerenciar repasses")));
    }

    private record BulkItemResult(boolean paid, BigDecimal valor) {
        static BulkItemResult paid(BigDecimal valor) {
            return new BulkItemResult(true, valor);
        }

        static BulkItemResult skipped() {
            return new BulkItemResult(false, BigDecimal.ZERO);
        }
    }

    private record BulkAccumulator(int pagos, BigDecimal valorTotal) {
        BulkAccumulator() {
            this(0, BigDecimal.ZERO);
        }

        BulkAccumulator add(BulkItemResult item) {
            if (!item.paid()) {
                return this;
            }
            return new BulkAccumulator(pagos + 1, valorTotal.add(item.valor()));
        }
    }
}
