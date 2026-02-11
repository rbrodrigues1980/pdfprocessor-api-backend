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
     * Retorna Mono<Rubrica> se encontrada e ativa, ou Mono.empty() se não
     * encontrada ou inativa.
     */
    public Mono<Rubrica> validateRubrica(String codigo, String descricaoExtraida) {
        if (codigo == null || codigo.trim().isEmpty()) {
            return Mono.empty();
        }

        // Normalizar código: remover espaços (ex: "4 416" → "4416")
        String codigoNormalizado = codigo.trim().replaceAll("\\s+", "");

        // Buscar rubrica GLOBAL primeiro (todas as rubricas padrão são GLOBAL)
        return rubricaRepository.findByCodigo(codigoNormalizado, "GLOBAL")
                .flatMap(rubrica -> {
                    if (rubrica.getAtivo() == null || !rubrica.getAtivo()) {
                        log.warn("Rubrica {} encontrada mas está inativa", codigoNormalizado);
                        return Mono.empty();
                    }

                    // Aceita apenas pelo código - descrição pode variar entre CAIXA e FUNCEF
                    log.debug("✅ Rubrica {} validada (código encontrado no banco). " +
                            "Descrição no banco: '{}', Descrição extraída: '{}'",
                            codigoNormalizado, rubrica.getDescricao(), descricaoExtraida);
                    return Mono.just(rubrica);
                })
                .switchIfEmpty(Mono.defer(() -> {
                    log.warn("⚠️ Rubrica {} não encontrada no banco de dados. " +
                            "Cadastre a rubrica na API 1 (POST /api/v1/rubricas) para que seja salva.",
                            codigoNormalizado);
                    return Mono.empty();
                }));
    }
}
