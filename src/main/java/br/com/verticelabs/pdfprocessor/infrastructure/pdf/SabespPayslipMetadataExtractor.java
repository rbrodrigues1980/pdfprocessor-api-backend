package br.com.verticelabs.pdfprocessor.infrastructure.pdf;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Metadados do Demonstrativo de Pagamento SABESP (PERÍODO, MATRÍC, NOME, CPF opcional).
 */
public final class SabespPayslipMetadataExtractor {

    /**
     * Linha de dados: {@code 01/2020 00008079 ANSELMO DA FONSECA 00052717/00240 ...}
     */
    private static final Pattern DATA_LINE = Pattern.compile(
            "(?m)^(\\d{2})/(\\d{4})\\s+(\\d{7,8})\\s+([A-ZÁÉÍÓÚÂÊÔÃÕÇÜ][A-ZÁÉÍÓÚÂÊÔÃÕÇÜ\\s.'-]+?)\\s+\\d",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

    private static final Pattern PERIODO_NEAR_LABEL = Pattern.compile(
            "PER[IÍ]ODO\\s+(?:MATR[IÍ]C[AÁ]?\\s+)?(?:NOME\\s+DO\\s+EMPREGADO\\s+)?[\\s\\S]{0,120}?"
                    + "(\\d{2})/(\\d{4})",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

    private static final Pattern CPF_MASCARADO = Pattern.compile(
            "\\b(\\d{3}\\.\\d{3}\\.\\d{3}-\\d{2})\\b");

    private static final Pattern CPF_DIGITS = Pattern.compile(
            "(?i)(?:CPF[:\\s]*)(\\d{11})\\b");

    private SabespPayslipMetadataExtractor() {
    }

    /**
     * @param cpf dígitos normalizados (11) ou null se o PDF não trouxer CPF
     */
    public record SabespMetadata(String periodoYm, String matricula, String nome, String cpf) {
    }

    public static Optional<SabespMetadata> extract(String pageText) {
        if (pageText == null || pageText.isBlank()) {
            return Optional.empty();
        }

        String cpf = extractCpfDigits(pageText);

        Matcher data = DATA_LINE.matcher(pageText);
        if (data.find()) {
            String ym = toYm(data.group(2), data.group(1));
            String matricula = data.group(3).trim();
            String nome = data.group(4).replaceAll("\\s+", " ").trim().toUpperCase();
            return Optional.of(new SabespMetadata(ym, matricula, nome, cpf));
        }

        Matcher periodo = PERIODO_NEAR_LABEL.matcher(pageText);
        if (periodo.find()) {
            String ym = toYm(periodo.group(2), periodo.group(1));
            return Optional.of(new SabespMetadata(ym, null, null, cpf));
        }

        if (cpf != null) {
            return Optional.of(new SabespMetadata(null, null, null, cpf));
        }

        return Optional.empty();
    }

    public static Optional<String> detectPeriodoYm(String pageText) {
        return extract(pageText).map(SabespMetadata::periodoYm).filter(p -> p != null && !p.isBlank());
    }

    /**
     * CPF com 11 dígitos, ou null.
     */
    public static String extractCpfDigits(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        Matcher masked = CPF_MASCARADO.matcher(text);
        if (masked.find()) {
            String digits = masked.group(1).replaceAll("\\D", "");
            return digits.length() == 11 ? digits : null;
        }
        Matcher labeled = CPF_DIGITS.matcher(text);
        if (labeled.find()) {
            return labeled.group(1);
        }
        return null;
    }

    private static String toYm(String year, String month) {
        int m = Integer.parseInt(month.trim());
        return year.trim() + "-" + String.format("%02d", m);
    }
}
