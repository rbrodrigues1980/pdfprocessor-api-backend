package br.com.verticelabs.pdfprocessor.application.repasse;

import br.com.verticelabs.pdfprocessor.domain.model.RepasseStatus;
import br.com.verticelabs.pdfprocessor.domain.model.RepasseValorConfig;
import br.com.verticelabs.pdfprocessor.domain.model.SystemConfig;
import br.com.verticelabs.pdfprocessor.domain.repository.DeveloperRepasseRepository;
import br.com.verticelabs.pdfprocessor.domain.repository.RepasseValorConfigRepository;
import br.com.verticelabs.pdfprocessor.domain.repository.SystemConfigRepository;
import br.com.verticelabs.pdfprocessor.interfaces.rest.dto.RepasseConfigResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.Year;
import java.time.ZoneId;

@Slf4j
@Service
@RequiredArgsConstructor
public class RepasseValorService {

    private static final ZoneId ZONE_BR = ZoneId.of("America/Sao_Paulo");
    private static final BigDecimal DEFAULT_VALOR = new BigDecimal("10.00");
    private static final int DEFAULT_ANO_BASE = 2026;
    private static final Instant DEFAULT_VIGENCIA = Instant.parse("2026-01-01T03:00:00Z");

    private final RepasseValorConfigRepository valorConfigRepository;
    private final DeveloperRepasseRepository repasseRepository;
    private final SystemConfigRepository systemConfigRepository;

    public Mono<BigDecimal> getValorUnitario() {
        return getValorForInstant(Instant.now());
    }

    public Mono<BigDecimal> getValorForInstant(Instant validadoEm) {
        Instant reference = validadoEm != null ? validadoEm : Instant.now();
        return ensureSeeded()
                .then(valorConfigRepository.findEffectiveAt(reference))
                .map(RepasseValorConfig::getValorUnitario)
                .defaultIfEmpty(DEFAULT_VALOR);
    }

    public Mono<Integer> getAnoBase() {
        return getAnoBaseForInstant(Instant.now());
    }

    public Mono<Integer> getAnoBaseForInstant(Instant validadoEm) {
        Instant reference = validadoEm != null ? validadoEm : Instant.now();
        return ensureSeeded()
                .then(valorConfigRepository.findEffectiveAt(reference))
                .map(RepasseValorConfig::getAnoBase)
                .defaultIfEmpty(DEFAULT_ANO_BASE);
    }

    public Mono<RepasseConfigInfo> getConfigInfo() {
        return ensureSeeded()
                .then(valorConfigRepository.findEffectiveAt(Instant.now()))
                .map(config -> new RepasseConfigInfo(
                        config.getValorUnitario(),
                        config.getAnoBase(),
                        Year.now(ZONE_BR).getValue(),
                        config.getVigenciaDe()))
                .defaultIfEmpty(new RepasseConfigInfo(DEFAULT_VALOR, DEFAULT_ANO_BASE, Year.now(ZONE_BR).getValue(), DEFAULT_VIGENCIA));
    }

    public Mono<RepasseConfigResponse> getConfigResponse() {
        return ensureSeeded()
                .then(valorConfigRepository.findLatest())
                .map(this::toResponse);
    }

    public Mono<RepasseConfigResponse> updateConfig(
            BigDecimal valorUnitario,
            int anoBase,
            Instant vigenciaDe,
            String updatedBy) {

        if (valorUnitario == null || valorUnitario.compareTo(BigDecimal.ZERO) <= 0) {
            return Mono.error(new IllegalArgumentException("O valor unitário deve ser maior que zero"));
        }
        if (anoBase < 2000 || anoBase > Year.now(ZONE_BR).getValue() + 1) {
            return Mono.error(new IllegalArgumentException("Ano base inválido"));
        }
        if (vigenciaDe == null) {
            return Mono.error(new IllegalArgumentException("Informe a data de corte (vigência) do novo valor"));
        }

        Instant now = Instant.now();
        BigDecimal valorNormalizado = valorUnitario.stripTrailingZeros();

        return ensureSeeded()
                .then(valorConfigRepository.existsByVigenciaDe(vigenciaDe))
                .flatMap(exists -> Boolean.TRUE.equals(exists)
                        ? Mono.error(new IllegalArgumentException("Já existe uma configuração com esta data de corte"))
                        : Mono.just(true))
                .then(Mono.defer(() -> {
                    RepasseValorConfig config = RepasseValorConfig.builder()
                            .valorUnitario(valorNormalizado)
                            .anoBase(anoBase)
                            .vigenciaDe(vigenciaDe)
                            .createdAt(now)
                            .createdBy(updatedBy)
                            .build();
                    return valorConfigRepository.save(config);
                }))
                .flatMap(saved -> applyValorToPendingRepasse(saved.getVigenciaDe(), saved.getValorUnitario())
                        .map(updated -> toResponse(saved, updated)));
    }

    private Mono<Long> applyValorToPendingRepasse(Instant vigenciaDe, BigDecimal novoValor) {
        return repasseRepository.findByStatus(RepasseStatus.PENDENTE)
                .filter(repasse -> repasse.getValidadoEm() != null
                        && !repasse.getValidadoEm().isBefore(vigenciaDe))
                .flatMap(repasse -> {
                    repasse.setValorUnitario(novoValor);
                    repasse.setUpdatedAt(Instant.now());
                    return repasseRepository.save(repasse);
                })
                .count()
                .doOnSuccess(count -> log.info(
                        "Valor de repasse atualizado para {} pendente(s) com validação >= {}",
                        count, vigenciaDe));
    }

    private Mono<Void> ensureSeeded() {
        return valorConfigRepository.findLatest()
                .switchIfEmpty(seedInitialConfig())
                .then();
    }

    private Mono<RepasseValorConfig> seedInitialConfig() {
        return systemConfigRepository.findByKeyAndTenantIdIsNull(SystemConfig.KEY_REPASSE_VALOR_UNITARIO)
                .map(config -> new BigDecimal(config.getValue()))
                .defaultIfEmpty(DEFAULT_VALOR)
                .zipWith(systemConfigRepository.findByKeyAndTenantIdIsNull(SystemConfig.KEY_REPASSE_ANO_BASE)
                        .map(config -> Integer.parseInt(config.getValue()))
                        .defaultIfEmpty(DEFAULT_ANO_BASE))
                .flatMap(tuple -> {
                    Instant now = Instant.now();
                    RepasseValorConfig initial = RepasseValorConfig.builder()
                            .valorUnitario(tuple.getT1())
                            .anoBase(tuple.getT2())
                            .vigenciaDe(DEFAULT_VIGENCIA)
                            .createdAt(now)
                            .createdBy("system")
                            .build();
                    return valorConfigRepository.save(initial);
                });
    }

    private RepasseConfigResponse toResponse(RepasseValorConfig config) {
        return toResponse(config, null);
    }

    private RepasseConfigResponse toResponse(RepasseValorConfig config, Long pendentesAtualizados) {
        return new RepasseConfigResponse(
                config.getValorUnitario(),
                config.getAnoBase(),
                Year.now(ZONE_BR).getValue(),
                config.getVigenciaDe(),
                config.getCreatedAt(),
                config.getCreatedBy(),
                true,
                pendentesAtualizados);
    }

    public record RepasseConfigInfo(
            BigDecimal valorUnitario,
            int anoBase,
            int anoAtual,
            Instant vigenciaDe) {}
}
