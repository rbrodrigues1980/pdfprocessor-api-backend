package br.com.verticelabs.pdfprocessor.infrastructure.pdf;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Metadados da Ficha Financeira de Pagamentos SABESPREV (ANO, MATRÍCULA, NOME).
 */
public final class SabesprevFichaMetadataExtractor {

    private static final Pattern ANO = Pattern.compile(
            "ANO\\s*:\\s*(\\d{4})",
            Pattern.CASE_INSENSITIVE);

    /** Í/encoding quebrado ou espaços no PDF. */
    private static final Pattern MATRICULA = Pattern.compile(
            "MATR.{0,12}?(\\d{7,9})",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE | Pattern.DOTALL);

    private static final Pattern NOME = Pattern.compile(
            "NOME\\s*:\\s*([^\\r\\n]+)",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

    private SabesprevFichaMetadataExtractor() {
    }

    public record SabesprevFichaMetadata(String ano, String matricula, String nome) {
    }

    public static Optional<SabesprevFichaMetadata> extract(String pageText) {
        if (pageText == null || pageText.isBlank()) {
            return Optional.empty();
        }

        String ano = null;
        Matcher anoM = ANO.matcher(pageText);
        if (anoM.find()) {
            ano = anoM.group(1);
        }

        String matricula = null;
        Matcher matM = MATRICULA.matcher(pageText);
        if (matM.find()) {
            matricula = matM.group(1);
        }
        // Fallback: matrícula SABESP/SABESPREV costuma ser 8–9 dígitos após rótulo degradado
        if (matricula == null) {
            Matcher loose = Pattern.compile("(?i)(?:MATR|MATRICULA)[^0-9]{0,20}(\\d{7,9})").matcher(pageText);
            if (loose.find()) {
                matricula = loose.group(1);
            }
        }

        String nome = null;
        Matcher nomeM = NOME.matcher(pageText);
        if (nomeM.find()) {
            nome = nomeM.group(1).replaceAll("\\s+", " ").trim().toUpperCase();
            // Corta rótulos colados na mesma linha
            nome = nome.replaceAll("\\s+(DATA|PROCESSO|RUBRICA|INSCRI).*$", "").trim();
        }

        if (ano == null && matricula == null && nome == null) {
            return Optional.empty();
        }
        return Optional.of(new SabesprevFichaMetadata(ano, matricula, nome));
    }
}
