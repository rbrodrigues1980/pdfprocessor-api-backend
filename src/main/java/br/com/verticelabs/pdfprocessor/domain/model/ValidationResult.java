package br.com.verticelabs.pdfprocessor.domain.model;

import java.util.List;

/**
 * Resultado da validação de dados extraídos de um documento.
 * Contém um score de confiança (0.0 a 1.0) e lista de problemas encontrados.
 *
 * <h3>Classificação por score:</h3>
 * <ul>
 *   <li><strong>>= 0.85</strong>: ACCEPT — dados confiáveis</li>
 *   <li><strong>0.60 a 0.84</strong>: REVIEW — revisar manualmente</li>
 *   <li><strong>&lt; 0.60</strong>: REJECT — dados não confiáveis, reprocessar</li>
 * </ul>
 *
 * @param confidenceScore  Score de confiança (0.0 a 1.0)
 * @param isValid          true se score >= 0.85
 * @param issues           Lista de problemas encontrados
 * @param recommendation   "ACCEPT", "REVIEW" ou "REJECT"
 */
public record ValidationResult(
        double confidenceScore,
        boolean isValid,
        List<ValidationIssue> issues,
        String recommendation
) {

    public static final String ACCEPT = "ACCEPT";
    public static final String REVIEW = "REVIEW";
    public static final String REJECT = "REJECT";

    public static final double THRESHOLD_ACCEPT = 0.85;
    public static final double THRESHOLD_REVIEW = 0.60;

    /**
     * Cria um ValidationResult a partir de um score calculado.
     *
     * @param score  score de confiança (0.0 a 1.0)
     * @param issues lista de problemas encontrados
     * @return ValidationResult com recommendation calculado
     */
    public static ValidationResult fromScore(double score, List<ValidationIssue> issues) {
        String recommendation;
        if (score >= THRESHOLD_ACCEPT) {
            recommendation = ACCEPT;
        } else if (score >= THRESHOLD_REVIEW) {
            recommendation = REVIEW;
        } else {
            recommendation = REJECT;
        }
        return new ValidationResult(score, score >= THRESHOLD_ACCEPT, issues, recommendation);
    }

    /**
     * Cria um resultado perfeito (sem problemas).
     */
    public static ValidationResult perfect() {
        return new ValidationResult(1.0, true, List.of(), ACCEPT);
    }
}
