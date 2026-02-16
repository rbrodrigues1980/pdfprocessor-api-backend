package br.com.verticelabs.pdfprocessor.domain.model;

import java.util.List;

/**
 * Resultado da cross-validation (Fase 3).
 * Contém os dados consolidados após comparar duas extrações independentes.
 *
 * <h3>Lógica:</h3>
 * <ul>
 *   <li>Campos que coincidem nas duas extrações = alta confiança (1.0)</li>
 *   <li>Campos que divergem = flag para revisão manual</li>
 *   <li>Score consolidado = média ponderada dos matches</li>
 * </ul>
 *
 * @param consolidatedEntries  Entries consolidadas com valores mais confiáveis
 * @param confidenceScore      Score de confiança consolidado (0.0 a 1.0)
 * @param comparisons          Lista de comparações campo a campo
 * @param requiresManualReview true se campos críticos (nome, CPF, valores) divergem
 * @param totalFields          Total de campos comparados
 * @param matchedFields        Campos que coincidem
 * @param divergedFields       Campos que divergem
 */
public record CrossValidationResult(
        List<PayrollEntry> consolidatedEntries,
        double confidenceScore,
        List<FieldComparison> comparisons,
        boolean requiresManualReview,
        int totalFields,
        int matchedFields,
        int divergedFields
) {

    /**
     * Percentual de campos que coincidem.
     */
    public double matchRate() {
        return totalFields > 0 ? (double) matchedFields / totalFields : 0.0;
    }
}
