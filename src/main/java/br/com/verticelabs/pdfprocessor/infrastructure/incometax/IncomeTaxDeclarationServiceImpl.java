package br.com.verticelabs.pdfprocessor.infrastructure.incometax;

import br.com.verticelabs.pdfprocessor.domain.service.IncomeTaxDeclarationService;
import br.com.verticelabs.pdfprocessor.domain.service.PdfService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.io.InputStream;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class IncomeTaxDeclarationServiceImpl implements IncomeTaxDeclarationService {

    private final PdfService pdfService;

    // Padrão para encontrar "Ano-Calendário" seguido de um ano (ex: "Ano-Calendário
    // 2016")
    private static final Pattern ANO_CALENDARIO_PATTERN = Pattern.compile(
            "(?i)ano[-\\s]*calend[áa]rio\\s+(\\d{4})",
            Pattern.CASE_INSENSITIVE);

    // Padrão para encontrar "EXERCÍCIO" seguido de um ano (ex: "EXERCÍCIO 2017")
    private static final Pattern EXERCICIO_PATTERN = Pattern.compile(
            "(?i)exerc[ií]cio\\s+(\\d{4})",
            Pattern.CASE_INSENSITIVE);

    // Padrão para encontrar "NOME:" seguido do nome completo
    private static final Pattern NOME_PATTERN = Pattern.compile(
            "(?i)nome\\s*:?\\s*([A-ZÁÉÍÓÚÀÈÌÒÙÂÊÎÔÛÃÕÇ][A-ZÁÉÍÓÚÀÈÌÒÙÂÊÎÔÛÃÕÇ\\s]{2,}?)(?=\\s*(?:CPF|$|\\n))",
            Pattern.MULTILINE);

    // Padrão para encontrar CPF no formato XXX.XXX.XXX-XX
    private static final Pattern CPF_PATTERN = Pattern.compile(
            "CPF\\s*:?\\s*(\\d{3}\\.\\d{3}\\.\\d{3}-\\d{2})",
            Pattern.CASE_INSENSITIVE);

    // Padrões para encontrar valores da seção IMPOSTO DEVIDO
    private static final Pattern BASE_CALCULO_IMPOSTO_PATTERN = Pattern.compile(
            "(?i)base\\s+de\\s+c[aá]lculo\\s+do\\s+imposto[\\s:]*[R$\\s]*([\\d]{1,3}(?:[.\\s]?[\\d]{3})*(?:,\\d{2})?)",
            Pattern.CASE_INSENSITIVE);

    // Padrão para "Imposto devido" (sem I, II ou RRA)
    // Usa negative lookahead para excluir variantes I, II, RRA
    // PDF é extraído em colunas: label em uma linha, valor na próxima
    private static final Pattern IMPOSTO_DEVIDO_PATTERN = Pattern.compile(
            "(?i)imposto\\s+devido(?!\\s+(?:I|II|RRA))[\\s\\r\\n]+([\\d]{1,3}(?:[.]?[\\d]{3})*[,][\\d]{2})",
            Pattern.CASE_INSENSITIVE | Pattern.MULTILINE);

    private static final Pattern DEDUCAO_INCENTIVO_PATTERN = Pattern.compile(
            "(?i)dedu[çc][ãa]o\\s+de\\s+incentivo[\\s:]*[R$\\s]*([\\d]{1,3}(?:[.\\s]?[\\d]{3})*(?:,\\d{2})?)",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern IMPOSTO_DEVIDO_I_PATTERN = Pattern.compile(
            "(?i)imposto\\s+devido\\s+I[\\s:]*[R$\\s]*([\\d]{1,3}(?:[.\\s]?[\\d]{3})*(?:,\\d{2})?)",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern CONTRIBUICAO_PREV_EMPREGADOR_DOMESTICO_PATTERN = Pattern.compile(
            "(?i)contribui[çc][ãa]o\\s+prev\\.?\\s+empregador\\s+dom[eé]stico[\\s:]*[R$\\s]*([\\d]{1,3}(?:[.\\s]?[\\d]{3})*(?:,\\d{2})?)",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern IMPOSTO_DEVIDO_II_PATTERN = Pattern.compile(
            "(?i)imposto\\s+devido\\s+II[\\s:]*[R$\\s]*([\\d]{1,3}(?:[.\\s]?[\\d]{3})*(?:,\\d{2})?)",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern IMPOSTO_DEVIDO_RRA_PATTERN = Pattern.compile(
            "(?i)imposto\\s+devido\\s+RRA[\\s:]*[R$\\s]*([\\d]{1,3}(?:[.\\s]?[\\d]{3})*(?:,\\d{2})?)",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern TOTAL_IMPOSTO_DEVIDO_PATTERN = Pattern.compile(
            "(?i)total\\s+do\\s+imposto\\s+devido[\\s:]*[R$\\s]*([\\d]{1,3}(?:[.\\s]?[\\d]{3})*(?:,\\d{2})?)",
            Pattern.CASE_INSENSITIVE);

    // Padrão para "Saldo de imposto a pagar" - aceita quebras de linha entre label
    // e valor
    // (PDF pode ter duas colunas: label na primeira, valor na segunda)
    private static final Pattern SALDO_IMPOSTO_PAGAR_PATTERN = Pattern.compile(
            "(?i)saldo\\s+de\\s+imposto\\s+a\\s+pagar[\\s\\r\\n:]*[R$\\s]*([\\d]{1,3}(?:[.]?[\\d]{3})*[,][\\d]{2})",
            Pattern.CASE_INSENSITIVE | Pattern.MULTILINE);

    @Override
    public Mono<IncomeTaxInfo> extractIncomeTaxInfo(InputStream inputStream) {
        log.info("Iniciando extração de informações da declaração de IR");

        // Ler todos os bytes do inputStream primeiro (pois vamos precisar ler múltiplas
        // vezes)
        return Mono.fromCallable(() -> {
            byte[] bytes = inputStream.readAllBytes();
            inputStream.close();
            return bytes;
        })
                .subscribeOn(Schedulers.boundedElastic())
                .flatMap(bytes -> {
                    // Primeiro, encontrar a página RESUMO e extrair informações da primeira página
                    return pdfService.getTotalPages(new java.io.ByteArrayInputStream(bytes))
                            .flatMap(totalPages -> {
                                log.info("PDF tem {} páginas. Extraindo informações...", totalPages);

                                // Extrair texto da primeira página (onde geralmente estão nome, CPF e
                                // exercício)
                                Mono<String> primeiraPaginaText = pdfService.extractTextFromPage(
                                        new java.io.ByteArrayInputStream(bytes), 1);

                                // Encontrar página RESUMO
                                Mono<Integer> resumoPageNumber = findResumoPage(bytes, totalPages);

                                return Mono.zip(primeiraPaginaText, resumoPageNumber);
                            })
                            .flatMap(tuple -> {
                                String primeiraPaginaText = tuple.getT1();
                                Integer resumoPageNumber = tuple.getT2();

                                log.info("Página RESUMO encontrada: página {}", resumoPageNumber);

                                // Extrair texto da página RESUMO
                                return pdfService.extractTextFromPage(
                                        new java.io.ByteArrayInputStream(bytes), resumoPageNumber)
                                        .map(resumoPageText -> {
                                            // Extrair informações da primeira página
                                            String nome = extractNome(primeiraPaginaText);
                                            String cpf = extractCpf(primeiraPaginaText);
                                            String exercicio = extractExercicio(primeiraPaginaText);

                                            // Extrair informações da página RESUMO
                                            String anoCalendario = extractAnoCalendario(resumoPageText);

                                            // Extrair todos os valores da seção IMPOSTO DEVIDO
                                            Double baseCalculoImposto = extractValorMonetario(resumoPageText,
                                                    BASE_CALCULO_IMPOSTO_PATTERN);

                                            // DEBUG: Mostrar parte relevante do texto para diagnóstico
                                            int idxImposto = resumoPageText.toUpperCase().indexOf("IMPOSTO DEVIDO");
                                            if (idxImposto >= 0) {
                                                int endIdx = Math.min(idxImposto + 300, resumoPageText.length());
                                                log.info("🔍 DEBUG - Texto 'IMPOSTO DEVIDO': [{}]",
                                                        resumoPageText.substring(idxImposto, endIdx)
                                                                .replace("\n", "\\n").replace("\r", "\\r"));
                                            }

                                            // Extrair "Imposto devido" com estratégia alternativa se necessário
                                            Double impostoDevido = extractValorMonetario(resumoPageText,
                                                    IMPOSTO_DEVIDO_PATTERN);
                                            if (impostoDevido == null || impostoDevido == 0.0) {
                                                // Tentar estratégia alternativa: buscar todas as ocorrências e pegar a
                                                // primeira que não seja I, II ou RRA
                                                impostoDevido = extractImpostoDevidoAlternativo(resumoPageText);
                                            }

                                            Double deducaoIncentivo = extractValorMonetario(resumoPageText,
                                                    DEDUCAO_INCENTIVO_PATTERN);
                                            Double impostoDevidoI = extractValorMonetario(resumoPageText,
                                                    IMPOSTO_DEVIDO_I_PATTERN);
                                            Double contribuicaoPrevEmpregadorDomestico = extractValorMonetario(
                                                    resumoPageText, CONTRIBUICAO_PREV_EMPREGADOR_DOMESTICO_PATTERN);
                                            Double impostoDevidoII = extractValorMonetario(resumoPageText,
                                                    IMPOSTO_DEVIDO_II_PATTERN);
                                            Double impostoDevidoRRA = extractValorMonetario(resumoPageText,
                                                    IMPOSTO_DEVIDO_RRA_PATTERN);
                                            Double totalImpostoDevido = extractValorMonetario(resumoPageText,
                                                    TOTAL_IMPOSTO_DEVIDO_PATTERN);
                                            Double saldoImpostoPagar = extractValorMonetario(resumoPageText,
                                                    SALDO_IMPOSTO_PAGAR_PATTERN);

                                            // Estratégia alternativa para "Saldo de imposto a pagar" se o padrão
                                            // principal falhar
                                            // (PDF pode ter duas colunas: label na primeira, valor na segunda)
                                            if (saldoImpostoPagar == null) {
                                                saldoImpostoPagar = extractSaldoImpostoPagarAlternativo(resumoPageText);
                                            }

                                            // FALLBACK: Se "Imposto devido" não foi extraído corretamente
                                            // (devido ao PDF ter duas colunas misturadas na extração),
                                            // usar "Total do imposto devido" que tem o mesmo valor
                                            if ((impostoDevido == null || impostoDevido == 0.0)
                                                    && totalImpostoDevido != null && totalImpostoDevido > 0) {
                                                log.info(
                                                        "⚠️ Usando 'Total do imposto devido' como fallback para 'Imposto devido': {}",
                                                        totalImpostoDevido);
                                                impostoDevido = totalImpostoDevido;
                                            }

                                            log.info(
                                                    "Informações extraídas - Nome: {}, CPF: {}, Exercício: {}, Ano-Calendário: {}",
                                                    nome, cpf, exercicio, anoCalendario);
                                            log.info(
                                                    "Valores IMPOSTO DEVIDO - Base: {}, Devido: {}, Dedução: {}, Devido I: {}, Contribuição: {}, Devido II: {}, RRA: {}, Total: {}, Saldo a Pagar: {}",
                                                    baseCalculoImposto, impostoDevido, deducaoIncentivo, impostoDevidoI,
                                                    contribuicaoPrevEmpregadorDomestico, impostoDevidoII,
                                                    impostoDevidoRRA, totalImpostoDevido, saldoImpostoPagar);

                                            return new IncomeTaxInfo(nome, cpf, anoCalendario, exercicio,
                                                    baseCalculoImposto, impostoDevido, deducaoIncentivo, impostoDevidoI,
                                                    contribuicaoPrevEmpregadorDomestico, impostoDevidoII,
                                                    impostoDevidoRRA, totalImpostoDevido, saldoImpostoPagar);
                                        });
                            })
                            .onErrorResume(e -> {
                                log.error("Erro ao extrair informações da declaração de IR", e);
                                return Mono.error(e);
                            });
                });
    }

    /**
     * Encontra o número da página que contém "RESUMO".
     */
    private Mono<Integer> findResumoPage(byte[] pdfBytes, int totalPages) {
        // Processar páginas sequencialmente até encontrar "RESUMO"
        return Flux.range(1, totalPages)
                .concatMap(pageNumber -> {
                    return pdfService.extractTextFromPage(
                            new java.io.ByteArrayInputStream(pdfBytes), pageNumber)
                            .flatMap(pageText -> {
                                if (pageText != null && pageText.toUpperCase().contains("RESUMO")) {
                                    log.debug("Página {} contém 'RESUMO'", pageNumber);
                                    return Mono.just(pageNumber);
                                }
                                return Mono.empty();
                            });
                })
                .next()
                .switchIfEmpty(Mono.error(new IllegalArgumentException("Página RESUMO não encontrada no PDF")));
    }

    /**
     * Extrai o Ano-Calendário do texto.
     */
    private String extractAnoCalendario(String text) {
        Matcher matcher = ANO_CALENDARIO_PATTERN.matcher(text);
        if (matcher.find()) {
            String ano = matcher.group(1);
            log.debug("Ano-Calendário extraído: {}", ano);
            return ano;
        }
        return null;
    }

    /**
     * Extrai um valor monetário do texto usando um padrão específico.
     */
    private Double extractValorMonetario(String text, Pattern pattern) {
        Matcher matcher = pattern.matcher(text);
        if (matcher.find()) {
            String valorStr = matcher.group(1);
            log.debug("Valor encontrado (string): {} (padrão: {})", valorStr, pattern.pattern());

            // Converter para Double, tratando formato brasileiro (ponto como separador de
            // milhar, vírgula como decimal)
            try {
                // Remove pontos (separadores de milhar) e substitui vírgula por ponto
                String valorNormalizado = valorStr.replace(".", "").replace(",", ".");
                Double valor = Double.parseDouble(valorNormalizado);
                log.debug("Valor convertido: {}", valor);
                return valor;
            } catch (NumberFormatException e) {
                log.warn("Erro ao converter valor: {}", valorStr, e);
                return null;
            }
        } else {
            log.debug("Nenhum valor encontrado para o padrão: {}", pattern.pattern());
        }
        return null;
    }

    /**
     * Extrai "Imposto devido" usando estratégia alternativa: busca todas as
     * ocorrências e pega a primeira que não seja I, II ou RRA.
     */
    private Double extractImpostoDevidoAlternativo(String text) {
        // Padrão para encontrar todas as ocorrências de "Imposto devido" seguidas de um
        // valor (PDF é extraído em colunas, então valor pode estar na próxima linha)
        Pattern pattern = Pattern.compile(
                "(?i)imposto\\s+devido(?:\\s+(I|II|RRA))?[\\s\\r\\n:]*[R$\\s]*([\\d]{1,3}(?:[.\\s]?[\\d]{3})*(?:,\\d{2})?)",
                Pattern.CASE_INSENSITIVE | Pattern.MULTILINE);

        Matcher matcher = pattern.matcher(text);
        while (matcher.find()) {
            String variante = matcher.group(1); // I, II, RRA ou null
            String valorStr = matcher.group(2);

            // Se não for I, II ou RRA, este é o "Imposto devido" que queremos
            if (variante == null || variante.trim().isEmpty()) {
                log.info("Imposto devido encontrado (alternativo): {}", valorStr);
                try {
                    String valorNormalizado = valorStr.replace(".", "").replace(",", ".");
                    Double valor = Double.parseDouble(valorNormalizado);
                    log.info("Valor convertido (alternativo): {}", valor);
                    return valor;
                } catch (NumberFormatException e) {
                    log.warn("Erro ao converter valor (alternativo): {}", valorStr, e);
                }
            }
        }

        log.warn("Nenhum 'Imposto devido' encontrado com estratégia alternativa");
        return null;
    }

    /**
     * Extrai "Saldo de imposto a pagar" usando estratégia alternativa:
     * Busca o texto "SALDO DE IMPOSTO A PAGAR" e procura pelo valor nas linhas
     * próximas.
     * Útil quando o PDF tem duas colunas e o valor está na segunda coluna.
     * 
     * Estratégia: Busca o label e depois procura pelo PRIMEIRO valor monetário
     * próximo,
     * que é o mais provável de estar diretamente relacionado ao label.
     */
    private Double extractSaldoImpostoPagarAlternativo(String text) {
        // Buscar a posição do texto "SALDO DE IMPOSTO A PAGAR"
        Pattern labelPattern = Pattern.compile(
                "(?i)saldo\\s+de\\s+imposto\\s+a\\s+pagar",
                Pattern.CASE_INSENSITIVE);

        Matcher labelMatcher = labelPattern.matcher(text);
        if (labelMatcher.find()) {
            int labelEnd = labelMatcher.end();

            // Extrair uma janela de texto após o label (até 200 caracteres para pegar
            // apenas valores próximos)
            int windowStart = labelEnd;
            int windowEnd = Math.min(windowStart + 200, text.length());
            String window = text.substring(windowStart, windowEnd);

            log.debug("🔍 DEBUG - Janela após 'SALDO DE IMPOSTO A PAGAR': [{}]",
                    window.replace("\n", "\\n").replace("\r", "\\r"));

            // Procurar pelo PRIMEIRO valor monetário na janela (formato brasileiro:
            // X.XXX,XX)
            Pattern valuePattern = Pattern.compile(
                    "([\\d]{1,3}(?:[.]?[\\d]{3})*[,][\\d]{2})",
                    Pattern.CASE_INSENSITIVE);

            Matcher valueMatcher = valuePattern.matcher(window);

            // Retornar o PRIMEIRO valor encontrado (mais próximo do label)
            if (valueMatcher.find()) {
                String valorStr = valueMatcher.group(1);
                try {
                    String valorNormalizado = valorStr.replace(".", "").replace(",", ".");
                    Double valor = Double.parseDouble(valorNormalizado);

                    log.info("Saldo de imposto a pagar encontrado (alternativo): {} (valor: {})",
                            valorStr, valor);
                    return valor;
                } catch (NumberFormatException e) {
                    log.warn("Erro ao converter valor na janela: {}", valorStr, e);
                }
            } else {
                log.warn("Nenhum valor monetário encontrado na janela após 'SALDO DE IMPOSTO A PAGAR'");
            }
        } else {
            log.warn("Texto 'SALDO DE IMPOSTO A PAGAR' não encontrado para estratégia alternativa");
        }

        return null;
    }

    /**
     * Extrai o nome da pessoa do texto.
     */
    private String extractNome(String text) {
        Matcher matcher = NOME_PATTERN.matcher(text);
        if (matcher.find()) {
            String nome = matcher.group(1).trim();
            log.debug("Nome extraído: {}", nome);
            return nome;
        }
        return null;
    }

    /**
     * Extrai o CPF do texto.
     */
    private String extractCpf(String text) {
        Matcher matcher = CPF_PATTERN.matcher(text);
        if (matcher.find()) {
            String cpf = matcher.group(1);
            log.debug("CPF extraído: {}", cpf);
            return cpf;
        }
        return null;
    }

    /**
     * Extrai o exercício do texto.
     */
    private String extractExercicio(String text) {
        Matcher matcher = EXERCICIO_PATTERN.matcher(text);
        if (matcher.find()) {
            String exercicio = matcher.group(1);
            log.debug("Exercício extraído: {}", exercicio);
            return exercicio;
        }
        return null;
    }
}
