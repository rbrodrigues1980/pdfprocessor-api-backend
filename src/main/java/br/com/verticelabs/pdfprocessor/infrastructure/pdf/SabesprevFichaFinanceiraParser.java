package br.com.verticelabs.pdfprocessor.infrastructure.pdf;

import br.com.verticelabs.pdfprocessor.infrastructure.pdf.PdfLineParser.ParsedLine;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parser da Ficha Financeira SABESPREV (PDFBox scramble).
 * <p>
 * Layout típico extraído:
 * {@code DESCRIÇÃO 0,00 … 50,70 573,0150,707400 D}
 * → 11 meses + TOTAL + DEZ + código + P/D.
 * <p>
 * Whitelist final = tabela {@code rubricas}. Só emite mês com valor &gt; 0.
 */
@Slf4j
@Component
public class SabesprevFichaFinanceiraParser {

    private static final Pattern MONEY = Pattern.compile(
            "([0-9]{1,3}(?:\\.[0-9]{3})*,[0-9]{2})");

    /**
     * Código + P/D. Pode vir colado ao valor: {@code 50,707400 D}.
     */
    private static final Pattern CODE_PD = Pattern.compile(
            "(\\d{4})\\s*([PDpd])\\b");

    private final PdfNormalizer normalizer;

    public SabesprevFichaFinanceiraParser(PdfNormalizer normalizer) {
        this.normalizer = normalizer;
    }

    public List<ParsedLine> parse(String pageText, String anoCalendario) {
        List<ParsedLine> result = new ArrayList<>();
        if (pageText == null || pageText.isBlank()) {
            return result;
        }

        String ano = anoCalendario;
        if (ano == null || ano.isBlank()) {
            ano = SabesprevFichaMetadataExtractor.extract(pageText)
                    .map(SabesprevFichaMetadataExtractor.SabesprevFichaMetadata::ano)
                    .orElse(null);
        }
        if (ano == null || ano.isBlank()) {
            log.warn("Ficha SABESPREV: ano não encontrado; não é possível gerar referências YYYY-MM");
            return result;
        }

        // Corta rodapé TOTAIS / paginação
        String work = pageText;
        int totais = indexOfIgnoreCase(work, "TOTAIS");
        if (totais > 0) {
            work = work.substring(0, totais);
        }

        Map<String, RowData> byCode = new LinkedHashMap<>();
        Matcher codeM = CODE_PD.matcher(work);
        List<int[]> matches = new ArrayList<>();
        List<String> codes = new ArrayList<>();
        List<String> pds = new ArrayList<>();
        while (codeM.find()) {
            int start = codeM.start(1);
            // Aceita código colado após valor (…50,707400 D) ou isolado
            if (start > 0 && Character.isDigit(work.charAt(start - 1))) {
                if (start < 3 || !work.substring(start - 3, start).matches(",\\d{2}")) {
                    continue;
                }
            }
            matches.add(new int[]{codeM.start(), codeM.end()});
            codes.add(codeM.group(1));
            pds.add(codeM.group(2).toUpperCase());
        }

        for (int i = 0; i < codes.size(); i++) {
            int bodyStart = (i == 0) ? 0 : matches.get(i - 1)[1];
            int bodyEnd = matches.get(i)[0];
            String body = work.substring(bodyStart, bodyEnd);
            List<String> months = extractTwelveMonths(body);
            if (months == null) {
                continue;
            }
            String codigo = codes.get(i);
            String descricao = normalizer.normalizeDescription(stripMoneyAndNoise(body));
            RowData row = new RowData(codigo, descricao, pds.get(i), months);
            RowData existing = byCode.get(codigo);
            if (existing == null || countPositive(months) >= countPositive(existing.months)) {
                byCode.put(codigo, row);
            }
        }

        for (RowData row : byCode.values()) {
            for (int mes = 1; mes <= 12; mes++) {
                String valorStr = row.months.get(mes - 1);
                if (isZero(valorStr)) {
                    continue;
                }
                String ref = ano + "-" + String.format("%02d", mes);
                result.add(new ParsedLine(row.codigo, row.descricao, ref, valorStr));
            }
        }

        log.info("Ficha SABESPREV ano {}: {} entries mensais (valor > 0) de {} rubricas",
                ano, result.size(), byCode.size());
        return result;
    }

    /**
     * PDFBox: frequentemente {@code 11 meses + TOTAL + DEZ} (13 valores).
     * Fallback: primeiros 12 = JAN–DEZ.
     */
    static List<String> extractTwelveMonths(String body) {
        List<String> money = extractMoneyValues(body);
        if (money.size() > 13) {
            money = money.subList(money.size() - 13, money.size());
        }
        if (money.size() >= 13) {
            // indices 0..10 = JAN..NOV, 11 = TOTAL (ignorar), 12 = DEZ
            List<String> months = new ArrayList<>(money.subList(0, 11));
            months.add(money.get(12));
            String total = money.get(11);
            // Linhas esparsas: TOTAL colado como NOV e DEZ veio 0 (ex. 7401)
            if (isZero(months.get(11))
                    && !isZero(months.get(10))
                    && months.get(10).equals(total)) {
                months.set(11, months.get(10));
                months.set(10, "0,00");
            }
            return months;
        }
        if (money.size() == 12) {
            return new ArrayList<>(money);
        }
        if (money.isEmpty()) {
            return null;
        }
        List<String> months = new ArrayList<>(money);
        while (months.size() < 12) {
            months.add("0,00");
        }
        return months;
    }

    private static List<String> extractMoneyValues(String text) {
        List<String> values = new ArrayList<>();
        Matcher m = MONEY.matcher(text);
        while (m.find()) {
            values.add(m.group(1));
        }
        return values;
    }

    private static String stripMoneyAndNoise(String body) {
        return body
                .replaceAll(MONEY.pattern(), " ")
                .replaceAll("\\b[PDpd]\\b", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private static int countPositive(List<String> months) {
        int n = 0;
        for (String m : months) {
            if (!isZero(m)) {
                n++;
            }
        }
        return n;
    }

    private static boolean isZero(String valorStr) {
        try {
            String normalized = valorStr.replace(".", "").replace(",", ".");
            return new BigDecimal(normalized).compareTo(BigDecimal.ZERO) == 0;
        } catch (Exception e) {
            return true;
        }
    }

    private static int indexOfIgnoreCase(String haystack, String needle) {
        return haystack.toUpperCase().indexOf(needle.toUpperCase());
    }

    private record RowData(String codigo, String descricao, String pd, List<String> months) {
    }
}
