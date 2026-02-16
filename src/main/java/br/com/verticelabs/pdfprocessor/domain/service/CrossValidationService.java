package br.com.verticelabs.pdfprocessor.domain.service;

import br.com.verticelabs.pdfprocessor.domain.model.CrossValidationResult;
import br.com.verticelabs.pdfprocessor.domain.model.PayrollEntry;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * Serviço de cross-validation (Fase 3).
 *
 * <p>Quando a validação da Fase 2 retorna {@code confidenceScore < 0.85},
 * este serviço executa uma segunda extração com prompt alternativo e compara
 * os resultados campo a campo.</p>
 *
 * <h3>Fluxo:</h3>
 * <ol>
 *   <li>Recebe o resultado da primeira extração (prompt principal)</li>
 *   <li>Executa segunda extração com prompt alternativo (bottom-up)</li>
 *   <li>Compara campo a campo (nome, CPF, salários, rubricas)</li>
 *   <li>Campos que coincidem → alta confiança</li>
 *   <li>Campos que divergem → flag para revisão manual</li>
 * </ol>
 *
 * <h3>Custo:</h3>
 * <p>~$0.003 extra por página reprocessada (só quando necessário).</p>
 *
 * @see CrossValidationResult
 */
public interface CrossValidationService {

    /**
     * Executa dupla extração e compara resultados.
     * Chamado apenas quando a primeira extração tem confiança &lt; 0.85.
     *
     * @param pdfBytes       bytes do PDF
     * @param pageNumber     número da página (1-indexed)
     * @param firstEntries   entries da primeira extração
     * @param firstJson      JSON bruto da primeira extração (para comparação de metadados)
     * @param documentId     ID do documento
     * @param tenantId       ID do tenant
     * @param origem         origem (CAIXA, FUNCEF)
     * @return resultado consolidado com comparações campo a campo
     */
    Mono<CrossValidationResult> crossValidate(
            byte[] pdfBytes,
            int pageNumber,
            List<PayrollEntry> firstEntries,
            String firstJson,
            String documentId,
            String tenantId,
            String origem
    );
}
