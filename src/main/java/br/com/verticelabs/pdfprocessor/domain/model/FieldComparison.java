package br.com.verticelabs.pdfprocessor.domain.model;

/**
 * Comparação campo a campo entre duas extrações (Cross-Validation — Fase 3).
 *
 * <p>Quando o score de confiança da primeira extração é < 0.85,
 * uma segunda extração é feita com prompt alternativo. Os resultados
 * são comparados campo a campo.</p>
 *
 * @param field      Nome do campo comparado (ex: "nome", "cpf", "salarioBruto", "rubrica_001")
 * @param valueA     Valor da 1ª extração (prompt principal)
 * @param valueB     Valor da 2ª extração (prompt alternativo)
 * @param match      true se os valores são equivalentes
 * @param finalValue Valor escolhido para o resultado consolidado
 */
public record FieldComparison(
        String field,
        String valueA,
        String valueB,
        boolean match,
        String finalValue
) {

    /**
     * Cria uma comparação onde os valores coincidem.
     */
    public static FieldComparison matched(String field, String value) {
        return new FieldComparison(field, value, value, true, value);
    }

    /**
     * Cria uma comparação onde os valores divergem.
     * Usa o valor da primeira extração como default.
     */
    public static FieldComparison diverged(String field, String valueA, String valueB) {
        return new FieldComparison(field, valueA, valueB, false, valueA);
    }
}
