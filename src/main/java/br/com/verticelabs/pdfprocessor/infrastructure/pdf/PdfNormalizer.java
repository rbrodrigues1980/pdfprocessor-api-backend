package br.com.verticelabs.pdfprocessor.infrastructure.pdf;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Component
public class PdfNormalizer {

    /**
     * Normaliza valor monetário de formato brasileiro para double.
     * Ex: "1.385,66" -> 1385.66
     * Ex: "885,47" -> 885.47
     */
    public Double normalizeValue(String valueStr) {
        if (valueStr == null || valueStr.trim().isEmpty()) {
            return null;
        }

        try {
            // Remove R$ e espaços
            String cleaned = valueStr.replaceAll("[R$\\s]", "").trim();
            
            // Remove pontos (separadores de milhar) e substitui vírgula por ponto
            cleaned = cleaned.replace(".", "").replace(",", ".");
            
            return Double.parseDouble(cleaned);
        } catch (NumberFormatException e) {
            log.warn("Erro ao normalizar valor: {}", valueStr, e);
            return null;
        }
    }

    /**
     * Normaliza data/referência para formato "YYYY-MM".
     * Ex: "01/2017" -> "2017-01"
     * Ex: "2017/01" -> "2017-01"
     * Ex: "2017-01" -> "2017-01" (já normalizado)
     */
    public String normalizeReference(String reference) {
        if (reference == null || reference.trim().isEmpty()) {
            return null;
        }

        String cleaned = reference.trim();
        
        // Se já está no formato YYYY-MM, retorna
        if (cleaned.matches("\\d{4}-\\d{2}")) {
            return cleaned;
        }
        
        // Formato M/YYYY, MM/YYYY ou YYYY/MM, YYYY/M
        Pattern pattern1 = Pattern.compile("(\\d{1,2})/(\\d{4})"); // M/YYYY ou MM/YYYY
        Pattern pattern2 = Pattern.compile("(\\d{4})/(\\d{1,2})"); // YYYY/M ou YYYY/MM
        
        Matcher matcher1 = pattern1.matcher(cleaned);
        if (matcher1.matches()) {
            String mes = matcher1.group(1);
            String ano = matcher1.group(2);
            // Garantir que o mês tenha 2 dígitos
            if (mes.length() == 1) {
                mes = "0" + mes;
            }
            return String.format("%s-%s", ano, mes);
        }
        
        Matcher matcher2 = pattern2.matcher(cleaned);
        if (matcher2.matches()) {
            String ano = matcher2.group(1);
            String mes = matcher2.group(2);
            // Garantir que o mês tenha 2 dígitos
            if (mes.length() == 1) {
                mes = "0" + mes;
            }
            return String.format("%s-%s", ano, mes);
        }
        
        log.warn("Formato de referência não reconhecido: {}", reference);
        return null;
    }

    /**
     * Normaliza descrição removendo espaços extras e caracteres especiais.
     */
    public String normalizeDescription(String description) {
        if (description == null) {
            return null;
        }
        return description.trim().replaceAll("\\s+", " ");
    }
}

