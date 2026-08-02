package br.com.verticelabs.pdfprocessor.domain.service;

/**
 * Matrícula CAIXA/FUNCEF (7), SABESP ativa (8) ou SABESPREV ficha (9 dígitos).
 */
public final class MatriculaNormalizer {

    private MatriculaNormalizer() {
    }

    /** Apenas dígitos, ou null se vazio. */
    public static String toDigitsOrNull(String matricula) {
        if (matricula == null || matricula.isBlank()) {
            return null;
        }
        String digits = matricula.replaceAll("\\D", "");
        return digits.isEmpty() ? null : digits;
    }

    public static boolean isValid(String matriculaDigitos) {
        if (matriculaDigitos == null) {
            return false;
        }
        int len = matriculaDigitos.length();
        return len >= 7 && len <= 9;
    }

    /**
     * Normaliza e valida. Vazio → null. Valor presente inválido → {@link IllegalArgumentException}.
     */
    public static String normalizeOptional(String matricula) {
        String digits = toDigitsOrNull(matricula);
        if (digits == null) {
            return null;
        }
        if (!isValid(digits)) {
            throw new IllegalArgumentException(
                    "Matrícula deve conter 7 a 9 dígitos (CAIXA/FUNCEF/SABESP/SABESPREV): " + matricula);
        }
        return digits;
    }
}
