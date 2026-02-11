package br.com.verticelabs.pdfprocessor.application.consolidation;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.regex.Pattern;

@Slf4j
@Component
public class ReferenceNormalizer {

    // Padrões para identificar formato de data
    private static final Pattern MM_YYYY_PATTERN = Pattern.compile("^(\\d{2})/(\\d{4})$");
    private static final Pattern YYYY_MM_PATTERN = Pattern.compile("^(\\d{4})/(\\d{2})$");
    private static final Pattern YYYY_MM_ISO_PATTERN = Pattern.compile("^(\\d{4})-(\\d{2})$");

    /**
     * Normaliza uma referência de data para o formato YYYY-MM.
     * Aceita formatos: "01/2017", "2017/01", "2017-01"
     * 
     * @param referencia String no formato original
     * @return String normalizada no formato YYYY-MM ou null se inválida
     */
    public String normalize(String referencia) {
        if (referencia == null || referencia.trim().isEmpty()) {
            return null;
        }

        String trimmed = referencia.trim();

        // Já está no formato YYYY-MM
        if (YYYY_MM_ISO_PATTERN.matcher(trimmed).matches()) {
            return trimmed;
        }

        // Formato MM/YYYY -> YYYY-MM
        var mmYyyyMatcher = MM_YYYY_PATTERN.matcher(trimmed);
        if (mmYyyyMatcher.matches()) {
            String mes = mmYyyyMatcher.group(1);
            String ano = mmYyyyMatcher.group(2);
            return String.format("%s-%s", ano, mes);
        }

        // Formato YYYY/MM -> YYYY-MM
        var yyyyMmMatcher = YYYY_MM_PATTERN.matcher(trimmed);
        if (yyyyMmMatcher.matches()) {
            String ano = yyyyMmMatcher.group(1);
            String mes = yyyyMmMatcher.group(2);
            return String.format("%s-%s", ano, mes);
        }

        log.warn("Formato de referência não reconhecido: {}", referencia);
        return null;
    }

    /**
     * Extrai o ano de uma referência normalizada (YYYY-MM).
     */
    public String extractYear(String referenciaNormalizada) {
        if (referenciaNormalizada == null || !referenciaNormalizada.contains("-")) {
            return null;
        }
        return referenciaNormalizada.split("-")[0];
    }

    /**
     * Extrai o mês de uma referência normalizada (YYYY-MM).
     */
    public String extractMonth(String referenciaNormalizada) {
        if (referenciaNormalizada == null || !referenciaNormalizada.contains("-")) {
            return null;
        }
        return referenciaNormalizada.split("-")[1];
    }

    /**
     * Valida se uma referência está no formato YYYY-MM válido.
     */
    public boolean isValid(String referencia) {
        if (referencia == null) {
            return false;
        }
        return YYYY_MM_ISO_PATTERN.matcher(referencia.trim()).matches();
    }
}

