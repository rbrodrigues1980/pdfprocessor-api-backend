package br.com.verticelabs.pdfprocessor.infrastructure.pdf;

import br.com.verticelabs.pdfprocessor.domain.model.Rubrica;
import br.com.verticelabs.pdfprocessor.domain.repository.RubricaRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.Locale;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class RubricaValidator {

    /**
     * Rubricas em que código e descrição extraídos precisam bater juntos.
     * Se o código existir mas a descrição divergir, o valor não é considerado.
     */
    private static final Map<String, String> RUBRICAS_CODIGO_E_DESCRICAO = Map.of(
            "3396", "REP TAXA ADMINISTRATIVA BUA NOVO PLANO",
            "4432", "FUNCEF CONTR. EQUACIONAMENTO2 SALDADO",
            "4436", "FUNCEF CONTRIB EQU SALDADO 02 GRT NATAL"
    );

    private final RubricaRepository rubricaRepository;

    /**
     * Valida se uma rubrica existe e está ativa no banco de dados.
     * Para 3396, 4432 e 4436, exige também que a descrição extraída bata com a esperada.
     * Retorna a rubrica se válida, ou Mono.empty() caso contrário.
     */
    public Mono<Rubrica> validateRubrica(String codigo, String descricaoExtraida) {
        if (codigo == null || codigo.trim().isEmpty()) {
            return Mono.empty();
        }

        String codigoNormalizado = codigo.trim().replaceAll("\\s+", "");

        return rubricaRepository.findByCodigo(codigoNormalizado)
                .switchIfEmpty(Mono.defer(() -> {
                    log.warn(
                            "⚠️ Rubrica {} não encontrada no banco. Cadastre via POST /api/v1/rubricas.",
                            codigoNormalizado
                    );
                    return Mono.empty();
                }))
                .flatMap(rubrica -> {
                    if (rubrica.getAtivo() == null || !rubrica.getAtivo()) {
                        log.warn("Rubrica {} encontrada mas está inativa", codigoNormalizado);
                        return Mono.empty();
                    }

                    if (!descricaoBateQuandoObrigatoria(codigoNormalizado, descricaoExtraida)) {
                        log.warn(
                                "⚠️ Rubrica {} ignorada: descrição extraída '{}' não bate com a esperada '{}'",
                                codigoNormalizado,
                                descricaoExtraida,
                                RUBRICAS_CODIGO_E_DESCRICAO.get(codigoNormalizado)
                        );
                        return Mono.empty();
                    }

                    log.debug("✅ Rubrica {} validada: '{}'", codigoNormalizado, rubrica.getDescricao());
                    return Mono.just(rubrica);
                });
    }

    private boolean descricaoBateQuandoObrigatoria(String codigoNormalizado, String descricaoExtraida) {
        String esperada = RUBRICAS_CODIGO_E_DESCRICAO.get(codigoNormalizado);
        if (esperada == null) {
            return true;
        }
        return normalizeForMatch(esperada).equals(normalizeForMatch(descricaoExtraida));
    }

    static String normalizeForMatch(String descricao) {
        if (descricao == null) {
            return "";
        }
        return descricao.trim().replaceAll("\\s+", " ").toUpperCase(Locale.ROOT);
    }
}
