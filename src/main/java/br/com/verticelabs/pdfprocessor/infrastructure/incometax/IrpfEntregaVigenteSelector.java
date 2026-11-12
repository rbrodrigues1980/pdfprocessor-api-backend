package br.com.verticelabs.pdfprocessor.infrastructure.incometax;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Seleciona a entrega DIRPF vigente em PDFs que concatenam Original e Retificadora
 * (qualquer ordem e qualquer quantidade de páginas por bloco).
 *
 * <p>Regra: Retificadora &gt; Original; empate → Data/Hora da Entrega mais recente;
 * residual → último bloco.</p>
 */
public final class IrpfEntregaVigenteSelector {

    private static final Pattern PAGINA_MARKER = Pattern.compile(
            "(?m)^===\\s*PAGINA\\s+(\\d+)\\s*===",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern TIPO_DECLARACAO = Pattern.compile(
            "(?i)tipo\\s+de\\s+declara..o[\\s:\\r\\n]*((?:declara..o|retifica)[^\\n\\r]+)",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

    private static final Pattern DATA_ENTREGA = Pattern.compile(
            "(?i)data/hora\\s+da\\s+entrega[\\s:]*([\\d]{2}/[\\d]{2}/[\\d]{4}\\s+.s\\s+[\\d]{2}:[\\d]{2}:[\\d]{2})",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

    private static final Pattern CONTROLE = Pattern.compile(
            "(?i)controle[:\\s]*(\\d{10,20})",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

    private static final DateTimeFormatter DATA_ENTREGA_FMT =
            DateTimeFormatter.ofPattern("dd/MM/yyyy 'às' HH:mm:ss", Locale.forLanguageTag("pt-BR"));

    private IrpfEntregaVigenteSelector() {
    }

    public record EntregaBloco(
            int index,
            int startPage,
            int endPage,
            int identPage,
            Integer resumoPage,
            String tipoDeclaracao,
            boolean retificadora,
            LocalDateTime dataEntrega,
            String controle,
            String text) {
    }

    /**
     * Divide o texto completo ({@code === PAGINA n ===}) em blocos iniciados por
     * IDENTIFICAÇÃO DO CONTRIBUINTE.
     */
    public static List<EntregaBloco> detectarBlocos(String allPagesText) {
        if (allPagesText == null || allPagesText.isBlank()) {
            return List.of();
        }

        List<PageSlice> pages = splitPages(allPagesText);
        if (pages.isEmpty()) {
            return List.of();
        }

        List<Integer> identIndexes = new ArrayList<>();
        for (int i = 0; i < pages.size(); i++) {
            if (contemIdentificacao(pages.get(i).text())) {
                identIndexes.add(i);
            }
        }
        if (identIndexes.isEmpty()) {
            return List.of();
        }

        List<EntregaBloco> blocos = new ArrayList<>();
        for (int b = 0; b < identIndexes.size(); b++) {
            int from = identIndexes.get(b);
            int toExclusive = (b + 1 < identIndexes.size()) ? identIndexes.get(b + 1) : pages.size();
            StringBuilder sb = new StringBuilder();
            Integer resumoPage = null;
            for (int i = from; i < toExclusive; i++) {
                PageSlice p = pages.get(i);
                sb.append("=== PAGINA ").append(p.page()).append(" ===\n");
                sb.append(p.text()).append("\n");
                if (resumoPage == null && contemResumo(p.text())) {
                    resumoPage = p.page();
                }
            }
            String blockText = sb.toString();
            String tipo = extract(blockText, TIPO_DECLARACAO);
            String dataRaw = extract(blockText, DATA_ENTREGA);
            String controle = extract(blockText, CONTROLE);
            blocos.add(new EntregaBloco(
                    b,
                    pages.get(from).page(),
                    pages.get(toExclusive - 1).page(),
                    pages.get(from).page(),
                    resumoPage,
                    tipo,
                    isRetificadora(tipo),
                    parseDataEntrega(dataRaw),
                    controle,
                    blockText));
        }
        return blocos;
    }

    /** Escolhe o bloco vigente pela regra Retificadora → data → último. */
    public static Optional<EntregaBloco> selecionarVigente(List<EntregaBloco> blocos) {
        if (blocos == null || blocos.isEmpty()) {
            return Optional.empty();
        }
        if (blocos.size() == 1) {
            return Optional.of(blocos.get(0));
        }
        return blocos.stream().max(COMPARATOR);
    }

    public static Optional<EntregaBloco> selecionarVigente(String allPagesText) {
        return selecionarVigente(detectarBlocos(allPagesText));
    }

    /**
     * Recorta o texto completo para conter apenas o bloco vigente.
     * Se não houver IDENTIFICAÇÃO, devolve o texto original.
     */
    public static String recortarDeclaracaoVigente(String allPagesText) {
        return selecionarVigente(allPagesText)
                .map(EntregaBloco::text)
                .orElse(allPagesText);
    }

    /**
     * Preferência entre duas declarações já extraídas (docs separados, mesmo ano).
     * Retorna true se {@code candidata} deve substituir {@code atual}.
     */
    public static boolean devePreferir(
            String tipoCandidata,
            String dataCandidata,
            String tipoAtual,
            String dataAtual) {
        boolean retCand = isRetificadora(tipoCandidata);
        boolean retAtual = isRetificadora(tipoAtual);
        if (retCand != retAtual) {
            return retCand;
        }
        LocalDateTime dCand = parseDataEntrega(dataCandidata);
        LocalDateTime dAtual = parseDataEntrega(dataAtual);
        if (dCand != null && dAtual != null) {
            return dCand.isAfter(dAtual);
        }
        if (dCand != null) {
            return true;
        }
        return false;
    }

    public static boolean isRetificadora(String tipo) {
        if (tipo == null || tipo.isBlank()) {
            return false;
        }
        String t = tipo.toUpperCase(Locale.ROOT);
        return t.contains("RETIFIC");
    }

    private static final Comparator<EntregaBloco> COMPARATOR = (a, b) -> {
        if (a.retificadora() != b.retificadora()) {
            return a.retificadora() ? 1 : -1;
        }
        if (a.dataEntrega() != null && b.dataEntrega() != null) {
            int cmp = a.dataEntrega().compareTo(b.dataEntrega());
            if (cmp != 0) {
                return cmp;
            }
        } else if (a.dataEntrega() != null) {
            return 1;
        } else if (b.dataEntrega() != null) {
            return -1;
        }
        return Integer.compare(a.index(), b.index());
    };

    public static LocalDateTime parseDataEntrega(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String normalized = raw.trim()
                .replaceAll("(?i)\\s+as\\s+", " às ")
                .replaceAll("\\s+", " ");
        try {
            return LocalDateTime.parse(normalized, DATA_ENTREGA_FMT);
        } catch (DateTimeParseException e) {
            // Formato às vezes sem "às" acentuado
            try {
                DateTimeFormatter alt = DateTimeFormatter.ofPattern(
                        "dd/MM/yyyy 'as' HH:mm:ss", Locale.forLanguageTag("pt-BR"));
                return LocalDateTime.parse(normalized.replace('à', 'a'), alt);
            } catch (DateTimeParseException ignored) {
                return null;
            }
        }
    }

    private static boolean contemIdentificacao(String text) {
        if (text == null) {
            return false;
        }
        String upper = text.toUpperCase(Locale.ROOT);
        return upper.contains("IDENTIFICAÇÃO DO CONTRIBUINTE")
                || upper.contains("IDENTIFICACAO DO CONTRIBUINTE");
    }

    private static boolean contemResumo(String text) {
        return text != null && text.toUpperCase(Locale.ROOT).contains("RESUMO");
    }

    private static String extract(String text, Pattern pattern) {
        if (text == null) {
            return null;
        }
        Matcher m = pattern.matcher(text);
        return m.find() ? m.group(1).trim() : null;
    }

    private record PageSlice(int page, String text) {
    }

    private static List<PageSlice> splitPages(String allPagesText) {
        Matcher m = PAGINA_MARKER.matcher(allPagesText);
        List<int[]> starts = new ArrayList<>();
        while (m.find()) {
            starts.add(new int[]{m.start(), Integer.parseInt(m.group(1)), m.end()});
        }
        List<PageSlice> pages = new ArrayList<>();
        for (int i = 0; i < starts.size(); i++) {
            int contentStart = starts.get(i)[2];
            int contentEnd = (i + 1 < starts.size()) ? starts.get(i + 1)[0] : allPagesText.length();
            pages.add(new PageSlice(starts.get(i)[1], allPagesText.substring(contentStart, contentEnd)));
        }
        return pages;
    }
}
