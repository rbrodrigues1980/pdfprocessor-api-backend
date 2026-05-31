package br.com.verticelabs.pdfprocessor.infrastructure.pdf;

import br.com.verticelabs.pdfprocessor.domain.model.Rubrica;
import br.com.verticelabs.pdfprocessor.domain.repository.RubricaRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Slf4j
@Component
@RequiredArgsConstructor
public class RubricaValidator {

    private final RubricaRepository rubricaRepository;

    /**
     * Valida se uma rubrica existe e está ativa no banco de dados.
     * Retorna Mono<Rubrica> se encontrada e ativa, ou Mono.empty() caso contrário.
     */
    public Mono<Rubrica> validateRubrica(String codigo, String descricaoExtraida) {
        if (codigo == null || codigo.trim().isEmpty()) {
            return Mono.empty();
        }

        String codigoNormalizado = codigo.trim().replaceAll("\\s+", "");

        return rubricaRepository.findByCodigo(codigoNormalizado)
                .flatMap(rubrica -> {
                    if (rubrica.getAtivo() == null || !rubrica.getAtivo()) {
                        log.warn("Rubrica {} encontrada mas está inativa", codigoNormalizado);
                        return Mono.empty();
                    }
                    log.debug("✅ Rubrica {} validada: '{}'", codigoNormalizado, rubrica.getDescricao());
                    return Mono.just(rubrica);
                })
                .switchIfEmpty(Mono.defer(() -> {
                    log.warn("⚠️ Rubrica {} não encontrada no banco. Cadastre via POST /api/v1/rubricas.", codigoNormalizado);
                    return Mono.empty();
                }));
    }
}
