package br.com.verticelabs.pdfprocessor.infrastructure.pdf;

import br.com.verticelabs.pdfprocessor.domain.model.InformeRendimentosData;
import br.com.verticelabs.pdfprocessor.domain.model.InformeRendimentosData.InformacaoComplementarJudiciaria;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Extrai cabeçalho e seção 7 (Proc.Jud. / Contr. Extr. / IRRF) do comprovante
 * Funcef de rendimentos pagos e retenção de IR na fonte.
 */
@Component
public class InformeRendimentosFuncefExtractor {

    private static final Pattern CNPJ = Pattern.compile(
            "\\b(\\d{2}\\.\\d{3}\\.\\d{3}/\\d{4}-\\d{2})\\b");
    private static final Pattern CPF = Pattern.compile(
            "\\b(\\d{3}\\.\\d{3}\\.\\d{3}-\\d{2})\\b");
    private static final Pattern ANO_CALENDARIO = Pattern.compile(
            "Ano\\s+Calend[aá]rio\\s+(\\d{4})", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    private static final Pattern RAZAO_SOCIAL = Pattern.compile(
            "Raz[aã]o\\s+Social\\s+(FUNDA[CÇ][AÃ]O\\s+DOS\\s+ECONOMI[AÁ]RIOS\\s+FEDERAIS)",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    private static final Pattern NOME_APOS_CPF = Pattern.compile(
            "\\d{3}\\.\\d{3}\\.\\d{3}-\\d{2}\\s+([A-ZÁÉÍÓÚÂÊÔÃÕÇÜ][A-ZÁÉÍÓÚÂÊÔÃÕÇÜ\\s.'-]+?)(?=\\s+NATUREZA|\\s+\\d\\.\\s|$)",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    private static final Pattern NOME_COMPLETO_LABEL = Pattern.compile(
            "NOME\\s+COMPLETO\\s+([A-ZÁÉÍÓÚÂÊÔÃÕÇÜ][A-ZÁÉÍÓÚÂÊÔÃÕÇÜ\\s.'-]+?)(?=\\s+NATUREZA|\\s+\\d\\.\\s|$)",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    private static final Pattern NATUREZA = Pattern.compile(
            "NATUREZA\\s+DO\\s+RENDIMENTO\\s+(.+?)(?=\\s+3\\.\\s+RENDIMENTOS|\\s+\\d\\.\\s|$)",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    private static final Pattern SECAO_7 = Pattern.compile(
            "7\\.\\s*INFORMA[CÇ][OÕ]ES\\s+COMPLEMENTARES\\s*(.+?)(?=\\s*8\\.\\s*RESPONS[AÁ]VEL|$)",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE | Pattern.DOTALL);
    /**
     * Linha judicial após colapso de whitespace. Aceita "13º", "13 °", "13o".
     */
    private static final Pattern PROC_JUD = Pattern.compile(
            "Proc\\.\\s*Jud\\.\\s*"
                    + "([\\d.-]+)\\s*-\\s*"
                    + "(\\d{2}/\\d{2}/\\d{4})\\s*-\\s*"
                    + "(\\d+)\\s*-\\s*"
                    + "([^-]+?)\\s*-\\s*"
                    + "Contr\\.?\\s*Extr\\.?\\s*:?\\s*([\\d.]+,\\d{2})\\s*-\\s*"
                    + "IRRF\\s*:?\\s*([\\d.]+,\\d{2})\\s*-\\s*"
                    + "Contr\\.?\\s*Extr\\.?\\s*13\\s*[º°o]?\\s*:?\\s*([\\d.]+,\\d{2})\\s*-\\s*"
                    + "IRRF\\s*13\\s*[º°o]?\\s*:?\\s*([\\d.]+,\\d{2})",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

    public InformeRendimentosData extract(String pdfText) {
        if (pdfText == null || pdfText.isBlank()) {
            return InformeRendimentosData.builder().informacoesComplementares(List.of()).build();
        }

        String collapsed = collapseWhitespace(pdfText);

        String cnpj = firstGroup(CNPJ, collapsed);
        String razao = firstGroup(RAZAO_SOCIAL, collapsed);
        if (razao == null && collapsed.toUpperCase().contains("FUNDA")) {
            Matcher m = Pattern.compile(
                    "(FUNDA[CÇ][AÃ]O\\s+DOS\\s+ECONOMI[AÁ]RIOS\\s+FEDERAIS)",
                    Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE).matcher(collapsed);
            if (m.find()) {
                razao = m.group(1).trim();
            }
        }

        String ano = firstGroup(ANO_CALENDARIO, collapsed);
        if (ano == null) {
            // Layout com labels e valores em linhas separadas: "Ano Calendário" ... "2018"
            Matcher m = Pattern.compile(
                    "Ano\\s+Calend[aá]rio.*?\\b(20\\d{2})\\b",
                    Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE | Pattern.DOTALL).matcher(collapsed);
            if (m.find()) {
                ano = m.group(1);
            }
        }

        String cpf = firstGroup(CPF, collapsed);
        String nome = firstGroup(NOME_APOS_CPF, collapsed);
        if (nome == null) {
            nome = firstGroup(NOME_COMPLETO_LABEL, collapsed);
        }
        if (nome != null) {
            nome = nome.replaceAll("\\s+", " ").trim();
        }
        String natureza = firstGroup(NATUREZA, collapsed);
        if (natureza != null) {
            natureza = natureza.replaceAll("\\s+", " ").trim();
        }

        String secao7Raw = null;
        List<InformacaoComplementarJudiciaria> infos = new ArrayList<>();
        Matcher secaoMatcher = SECAO_7.matcher(collapsed);
        if (secaoMatcher.find()) {
            secao7Raw = secaoMatcher.group(1).trim();
            Matcher proc = PROC_JUD.matcher(secao7Raw);
            while (proc.find()) {
                infos.add(InformacaoComplementarJudiciaria.builder()
                        .numeroProcesso(proc.group(1).trim())
                        .data(proc.group(2).trim())
                        .codigo(proc.group(3).trim())
                        .varaOuLocal(proc.group(4).trim().replaceAll("\\s+", " "))
                        .contrExtr(parseBrMoney(proc.group(5)))
                        .irrf(parseBrMoney(proc.group(6)))
                        .contrExtr13(parseBrMoney(proc.group(7)))
                        .irrf13(parseBrMoney(proc.group(8)))
                        .build());
            }
        }

        return InformeRendimentosData.builder()
                .cnpjFontePagadora(cnpj)
                .razaoSocialFontePagadora(razao)
                .anoCalendario(ano)
                .cpfBeneficiario(cpf)
                .nomeBeneficiario(nome)
                .naturezaRendimento(natureza)
                .informacoesComplementaresRaw(secao7Raw)
                .informacoesComplementares(infos)
                .build();
    }

    static String collapseWhitespace(String text) {
        // Normaliza quebras e NBSP; mantém acentos (útil para º).
        String normalized = text.replace('\u00A0', ' ').replace('\uFEFF', ' ');
        return normalized.replaceAll("\\s+", " ").trim();
    }

    static BigDecimal parseBrMoney(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String cleaned = raw.trim().replace(".", "").replace(",", ".");
        return new BigDecimal(cleaned);
    }

    private static String firstGroup(Pattern pattern, String text) {
        Matcher m = pattern.matcher(text);
        return m.find() ? m.group(1).trim() : null;
    }

    /** Normaliza texto para comparação de detecção (sem acentos). */
    public static String stripAccents(String input) {
        if (input == null) {
            return "";
        }
        String n = Normalizer.normalize(input, Normalizer.Form.NFD);
        return n.replaceAll("\\p{M}+", "");
    }
}
