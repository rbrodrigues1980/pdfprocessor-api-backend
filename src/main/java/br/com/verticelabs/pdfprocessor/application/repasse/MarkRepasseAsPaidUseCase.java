package br.com.verticelabs.pdfprocessor.application.repasse;

import br.com.verticelabs.pdfprocessor.domain.exceptions.ForbiddenOperationException;
import br.com.verticelabs.pdfprocessor.domain.exceptions.RepasseAlreadyPaidException;
import br.com.verticelabs.pdfprocessor.domain.exceptions.RepasseNotFoundException;
import br.com.verticelabs.pdfprocessor.domain.model.DeveloperRepasse;
import br.com.verticelabs.pdfprocessor.domain.model.RepasseStatus;
import br.com.verticelabs.pdfprocessor.domain.repository.DeveloperRepasseRepository;
import br.com.verticelabs.pdfprocessor.infrastructure.security.ReactiveSecurityContextHelper;
import br.com.verticelabs.pdfprocessor.interfaces.repasse.DeveloperRepasseMapper;
import br.com.verticelabs.pdfprocessor.interfaces.repasse.dto.DeveloperRepasseResponse;
import br.com.verticelabs.pdfprocessor.interfaces.repasse.dto.MarkRepassePaidRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Slf4j
@Service
@RequiredArgsConstructor
public class MarkRepasseAsPaidUseCase {

    private final DeveloperRepasseRepository repasseRepository;
    private final DeveloperRepasseMapper mapper;
    private final RepassePaymentApplier paymentApplier;

    public Mono<DeveloperRepasseResponse> execute(String repasseId, MarkRepassePaidRequest request) {
        return requireSuperAdmin()
                .then(repasseRepository.findById(repasseId)
                        .switchIfEmpty(Mono.error(new RepasseNotFoundException(repasseId))))
                .flatMap(repasse -> {
                    if (repasse.getStatus() == RepasseStatus.PAGO) {
                        return Mono.error(new RepasseAlreadyPaidException(repasseId));
                    }
                    return ReactiveSecurityContextHelper.getUserId()
                            .defaultIfEmpty("SUPER_ADMIN")
                            .flatMap(userId -> markAsPaid(repasse, userId, request));
                });
    }

    private Mono<DeveloperRepasseResponse> markAsPaid(
            DeveloperRepasse repasse,
            String userId,
            MarkRepassePaidRequest request) {
        paymentApplier.apply(repasse, userId, request);

        return repasseRepository.save(repasse)
                .doOnSuccess(r -> log.info("Repasse pago: id={}, pagoPor={}", r.getId(), userId))
                .map(mapper::toResponse);
    }

    private Mono<Void> requireSuperAdmin() {
        return ReactiveSecurityContextHelper.isSuperAdmin()
                .flatMap(isSuperAdmin -> Boolean.TRUE.equals(isSuperAdmin)
                        ? Mono.empty()
                        : Mono.error(new ForbiddenOperationException("Apenas SUPER_ADMIN pode gerenciar repasses")));
    }
}
