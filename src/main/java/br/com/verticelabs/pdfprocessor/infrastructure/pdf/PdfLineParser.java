package br.com.verticelabs.pdfprocessor.infrastructure.pdf;

import br.com.verticelabs.pdfprocessor.domain.model.DocumentType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Component
@RequiredArgsConstructor
public class PdfLineParser {

    private final PdfNormalizer normalizer;

    /**
     * Regex para CAIXA - FLEXÍVEL para aceitar múltiplos formatos:
     * 
     * FORMATO 1 (data separada): "2002 SALARIO PADRAO 01/2020 7.521,00"
     * FORMATO 2 (com prazo): "2043 REMUNERACAO 1/3 DAS FERIAS 12/2015 001 3.097,16"
     * FORMATO 3 (com código intermediário): "4346 FUNCEF - NOVO PLANO - 01/2020 999
     * T48,62"
     * FORMATO 4 (linha única com todas colunas): "1120 AC GRAT NATAL - MEDIA HORA
     * EXTRA 12/2015 10,87"
     * FORMATO 5 (data colada): "1034 AC APIP/IP - CONVERSAO 001 1.632,1301/2016"
     * FORMATO 6 (sem prazo): "2002 SALARIO PADRAO 5.518,0001/2016"
     * 
     * FORMATO - Linha única com colunas definidas:
     * Tipo / Rubrica | Discriminação da Rubrica | Competência | Prazo | Valor
     * Exemplos:
     * - "1120 AC GRAT NATAL - MEDIA HORA EXTRA 12/2015 10,87"
     * - "2002 SALARIO PADRAO 01/2016 5.269,00"
     * - "2043 REMUNERACAO 1/3 DAS FERIAS 12/2015 001 3.097,16"
     * - "2049 VP-GRAT SEM/ ADIC TEMPO SERVICO 01/2016 999 228,32"
     * - "4313 INSS - CONTRIBUICAO 01/2016 001 570,88"
     * 
     * Padrões aceitos:
     * - código descrição competência valor (sem prazo)
     * - código descrição competência prazo valor (com prazo)
     * - código descrição código_intermediário valor competência (formato
     * alternativo)
     * - código descrição prazo valor competência_colada (formato nativo)
     * 
     * Nota: Código pode ter espaços (ex: "4 416" → normalizado para "4416")
     * 
     * Captura:
     * 1. Código (3-4 dígitos, pode ter espaços)
     * 2. Descrição
     * 3. Competência/Data (MM/YYYY) - pode estar separada OU colada no valor
     * 4. Prazo/Código intermediário (opcional, 3 dígitos)
     * 5. Valor (com pontos e vírgula)
     */

    // Formato linha única com todas as colunas: código descrição competência
    // [prazo] valor
    // PRIORIDADE: Código + Valor são obrigatórios. Descrição e Competência são
    // opcionais.
    // Exemplos do formato:
    // "1120 AC GRAT NATAL - MEDIA HORA EXTRA 12/2015 10,87"
    // "2002 SALARIO PADRAO 01/2016 5.269,00"
    // "2043 REMUNERACAO 1/3 DAS FERIAS 12/2015 001 3.097,16"
    // "2049 VP-GRAT SEM/ ADIC TEMPO SERVICO 01/2016 999 228,32"
    // "4313 INSS - CONTRIBUICAO 01/2016 001 570,88"
    // "4412 FUNCEF CONTR. EQUACIONAMENTO1 SALDADO 11/2016 001 115,37"
    // "2002 SALARIO PADRAO 5.825,00" (sem competência)
    private static final Pattern CAIXA_PATTERN_LINHA_UNICA = Pattern.compile(
            "^([0-9]\\s*[0-9]\\s*[0-9]\\s*[0-9]?)\\s+" + // Grupo 1: Código (3-4 dígitos, pode ter espaços) -
                                                         // OBRIGATÓRIO
                    "(?:(.+?)\\s+(?=([0-9]{1,2}/[0-9]{4}|[A-Z]?[0-9]{1,3}(?:\\.[0-9]{3})*[,\\.][0-9]{2})))?" + // Grupo
                                                                                                               // 2:
                                                                                                               // Descrição
                                                                                                               // (opcional,
                                                                                                               // até
                                                                                                               // encontrar
                                                                                                               // data
                                                                                                               // ou
                                                                                                               // valor)
                    "(?:([0-9]{1,2}/[0-9]{4})\\s+)?" + // Grupo 3: Competência (opcional: M/YYYY ou MM/YYYY)
                    "(?:([0-9]{3})\\s+)?" + // Grupo 4: Prazo (opcional: "001", "999", "010")
                    "([A-Z]?[0-9]{1,3}(?:\\.[0-9]{3})*[,\\.][0-9]{2})\\s*$", // Grupo 5: Valor - OBRIGATÓRIO (pode ter
                                                                             // letra antes como "T48,62")
            Pattern.MULTILINE);

    // Formato com data separada: código descrição data [código_intermediário] valor
    // PRIORIDADE: Código + Valor são obrigatórios. Descrição e Data são opcionais.
    // Exemplos:
    // "2002 SALARIO PADRAO 01/2020 7.521,00"
    // "4346 FUNCEF - NOVO PLANO - 01/2020 999 T48,62"
    // "4412 FUNCEF CONTR. EQUACIONAMENTO1 SALDADO 01/2020 001 102,61"
    private static final Pattern CAIXA_PATTERN_DATA_SEPARADA = Pattern.compile(
            "^([0-9]\\s*[0-9]\\s*[0-9]\\s*[0-9]?)\\s+" + // Grupo 1: Código (3-4 dígitos) - OBRIGATÓRIO
                    "(?:(.+?)\\s+(?=([0-9]{1,2}/[0-9]{4}|[A-Z]?[0-9]{1,3}(?:\\.[0-9]{3})*[,\\.][0-9]{2})))?" + // Grupo
                                                                                                               // 2:
                                                                                                               // Descrição
                                                                                                               // (opcional)
                    "(?:([0-9]{1,2}/[0-9]{4})\\s+)?" + // Grupo 3: Data (opcional: M/YYYY ou MM/YYYY)
                    "(?:([0-9]{3})\\s+)?" + // Grupo 4: Código intermediário (opcional: "001", "999")
                    "([A-Z]?[0-9]{1,3}(?:\\.[0-9]{3})*[,\\.][0-9]{2})\\s*$", // Grupo 5: Valor - OBRIGATÓRIO
            Pattern.MULTILINE);

    // Regex simples - apenas captura código, descrição (opcional), data (opcional)
    // e valor
    // PRIORIDADE: Código + Valor são obrigatórios
    private static final Pattern CAIXA_PATTERN_SIMPLES = Pattern.compile(
            "^([0-9]{3,4})\\s+" + // Grupo 1: Código (3-4 dígitos, sem espaços) - OBRIGATÓRIO
                    "(?:(.+?)\\s+(?=([0-9]{1,2}/[0-9]{4}|[A-Z]?[0-9]{1,3}(?:\\.[0-9]{3})*[,\\.][0-9]{2})))?" + // Grupo
                                                                                                               // 2:
                                                                                                               // Descrição
                                                                                                               // (opcional)
                    "(?:([0-9]{1,2}/[0-9]{4})\\s+)?" + // Grupo 3: Data (opcional: M/YYYY ou MM/YYYY)
                    "(?:[0-9]{3}\\s+)?" + // Código intermediário opcional (não captura)
                    "([A-Z]?[0-9]{1,3}(?:\\.[0-9]{3})*[,\\.][0-9]{2})\\s*$", // Grupo 4: Valor - OBRIGATÓRIO
            Pattern.MULTILINE);

    // Formato nativo com data colada: código descrição [prazo] valor+data
    private static final Pattern CAIXA_PATTERN_NATIVE = Pattern.compile(
            "^([0-9]\\s*[0-9]\\s*[0-9]\\s*[0-9]?)\\s+" + // Grupo 1: Código
                    "(?:(.+?)\\s+)?" + // Grupo 2: Descrição (opcional)
                    "(?:([0-9]{3})\\s+)?" + // Grupo 3: Prazo (opcional)
                    "([0-9]{1,3}(?:\\.[0-9]{3})*[,\\.][0-9]{2})([0-9]{2}/[0-9]{4})\\s*$", // Grupo 4: Valor, Grupo 5:
                                                                                          // Data colada
            Pattern.MULTILINE);

    // Padrão ULTRA FLEXÍVEL: código + valor (obrigatórios), descrição e competência
    // opcionais
    // Prioriza capturar código e valor, mesmo que descrição ou competência não
    // estejam presentes
    // Exemplos:
    // "4412 FUNCEF CONTR. EQUACIONAMENTO1 SALDADO 11/2016 001 115,37"
    // "2002 SALARIO PADRAO 5.825,00" (sem competência)
    // "4412 115,37" (apenas código e valor)
    private static final Pattern CAIXA_PATTERN_FLEXIVEL = Pattern.compile(
            "^([0-9]\\s*[0-9]\\s*[0-9]\\s*[0-9]?)\\s+" + // Grupo 1: Código (3-4 dígitos) - OBRIGATÓRIO
                    "(?:(.+?)\\s+(?=([0-9]{1,2}/[0-9]{4}|[0-9]{3}\\s+[A-Z]?[0-9]{1,3}(?:\\.[0-9]{3})*[,\\.][0-9]{2}|[A-Z]?[0-9]{1,3}(?:\\.[0-9]{3})*[,\\.][0-9]{2})))?"
                    + // Grupo 2: Descrição (opcional)
                    "(?:([0-9]{1,2}/[0-9]{4})\\s+)?" + // Grupo 3: Competência (opcional: M/YYYY ou MM/YYYY)
                    "(?:([0-9]{3})\\s+)?" + // Grupo 4: Prazo (opcional)
                    "([A-Z]?[0-9]{1,3}(?:\\.[0-9]{3})*[,\\.][0-9]{2})\\s*$", // Grupo 5: Valor - OBRIGATÓRIO
            Pattern.MULTILINE);

    /**
     * Regex para FUNCEF:
     * Formato real encontrado: "2 033 2018/01 SUPL. APOS. TEMPO CONTRIB. BENEF.
     * SALD. 4.741,41"
     * ou: "4 430 2018/01 CONTRIBUIÇÃO EXTRAORDINARIA 2014 131,81"
     * ou: "4 459 2016/13 CONT. EXTRAORDINARIA ABONO ANUAL - 2014 107,77"
     * 
     * Ordem: código → referência (YYYY/MM) → descrição → valor (sem data colada!)
     * 
     * Captura:
     * 1. Código (3-4 dígitos, pode ter espaços - será normalizado)
     * 2. Referência (YYYY/MM) - formato diferente de CAIXA (aceita qualquer 2 dígitos, incluindo 13 para 13º mês)
     * 3. Descrição
     * 4. Valor (com pontos e vírgulas, SEM data colada)
     * 
     * Nota: Código pode ter espaços (ex: "2 033" → normalizado para "2033")
     * Nota: Referência vem ANTES da descrição no formato FUNCEF
     * Nota: A descrição usa .+? (non-greedy) para capturar tudo até encontrar o padrão de valor no final
     */
    private static final Pattern FUNCEF_PATTERN = Pattern.compile(
            "^([0-9]\\s*[0-9]\\s*[0-9]\\s*[0-9]?)\\s+([0-9]{4}/[0-9]{1,2})\\s+(.+?)\\s+([0-9]{1,3}(?:\\.[0-9]{3})*,[0-9]{2})\\s*$",
            Pattern.MULTILINE);

    /**
     * Representa uma linha de rubrica extraída do PDF.
     */
    public static class ParsedLine {
        private final String codigo;
        private final String descricao;
        private final String referencia; // Pode ser null para FUNCEF
        private final String valorStr;

        public ParsedLine(String codigo, String descricao, String referencia, String valorStr) {
            this.codigo = codigo;
            this.descricao = descricao;
            this.referencia = referencia;
            this.valorStr = valorStr;
        }

        public String getCodigo() {
            return codigo;
        }

        public String getDescricao() {
            return descricao;
        }

        public String getReferencia() {
            return referencia;
        }

        public String getValorStr() {
            return valorStr;
        }
    }

    /**
     * Extrai linhas de rubricas de um texto de página baseado no tipo do documento.
     */
    public List<ParsedLine> parseLines(String pageText, DocumentType documentType) {
        List<ParsedLine> parsedLines = new ArrayList<>();

        if (pageText == null || pageText.trim().isEmpty()) {
            log.debug("Texto da página vazio ou nulo");
            return parsedLines;
        }

        // ========== LOG COMPLETO DO TEXTO EXTRAÍDO ==========
        log.info("════════════════════════════════════════════════════════════════════════════════");
        log.info("📄 TEXTO COMPLETO EXTRAÍDO DA PÁGINA ({} caracteres):", pageText.length());
        log.info("════════════════════════════════════════════════════════════════════════════════");
        log.info("{}", pageText);
        log.info("════════════════════════════════════════════════════════════════════════════════");

        // Log para debug - mostrar TODAS as linhas numeradas
        String[] lines = pageText.split("\n");
        log.info("Processando página (tipo: {}, total de linhas: {})", documentType, lines.length);

        // ========== LOG DE TODAS AS LINHAS ==========
        log.info("════════════════════════════════════════════════════════════════════════════════");
        log.info("📋 TODAS AS LINHAS DO TEXTO ({} linhas):", lines.length);
        log.info("════════════════════════════════════════════════════════════════════════════════");
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            log.info("LINHA[{}]: [{}]", String.format("%3d", i + 1), line);
        }
        log.info("════════════════════════════════════════════════════════════════════════════════");

        // ========== PROCURAR LINHAS QUE PARECEM RUBRICAS ==========
        log.info("════════════════════════════════════════════════════════════════════════════════");
        log.info("🔍 PROCURANDO LINHAS QUE PARECEM RUBRICAS:");
        log.info("════════════════════════════════════════════════════════════════════════════════");
        int rubricaCount = 0;
        List<String> linhasParecemRubrica = new ArrayList<>();
        for (int i = 0; i < lines.length; i++) {
            String trimmed = lines[i].trim();
            // Verificar se começa com dígitos (código)
            boolean comecaComDigitos = trimmed.matches("^[0-9]\\s*[0-9]\\s*[0-9]\\s*[0-9]?.*");
            // Verificar se tem valor monetário
            boolean temValor = trimmed.matches(".*[0-9]{1,3}(?:\\.[0-9]{3})*,[0-9]{2}.*");

            if (comecaComDigitos || temValor) {
                log.info("LINHA[{}] - INSPEÇÃO: [{}]", String.format("%3d", i + 1), trimmed);
                log.info("  └─ Começa com dígitos: {}", comecaComDigitos);
                log.info("  └─ Tem valor monetário: {}", temValor);

                if (comecaComDigitos && temValor) {
                    log.info("  ✅ LINHA[{}] PARECE SER RUBRICA: [{}]", String.format("%3d", i + 1), trimmed);
                    linhasParecemRubrica.add(trimmed);
                    rubricaCount++;
                }
            }
        }
        log.info("════════════════════════════════════════════════════════════════════════════════");
        log.info("📊 Total de linhas que parecem rubricas: {}", rubricaCount);
        for (int idx = 0; idx < linhasParecemRubrica.size(); idx++) {
            log.info("  Rubrica[{}]: [{}]", idx + 1, linhasParecemRubrica.get(idx));
        }
        log.info("════════════════════════════════════════════════════════════════════════════════");

        // Determina quais padrões usar baseado no tipo
        List<Pattern> patternsToTry = new ArrayList<>();
        if (documentType == DocumentType.CAIXA || documentType == DocumentType.CAIXA_FUNCEF) {
            // Tentar padrões CAIXA na ordem de especificidade (mais específico primeiro)
            // 1. Linha única com todas as colunas (novo formato)
            patternsToTry.add(CAIXA_PATTERN_LINHA_UNICA);
            // 2. Padrão simples (formato mais comum)
            patternsToTry.add(CAIXA_PATTERN_SIMPLES);
            // 3. Padrão completo (com código intermediário)
            patternsToTry.add(CAIXA_PATTERN_DATA_SEPARADA);
            // 4. Formato nativo (data colada no valor)
            patternsToTry.add(CAIXA_PATTERN_NATIVE);
            // 5. Padrão ultra flexível (código + valor obrigatórios, resto opcional) -
            // ÚLTIMO RECURSO
            patternsToTry.add(CAIXA_PATTERN_FLEXIVEL);
        } else if (documentType == DocumentType.FUNCEF) {
            patternsToTry.add(FUNCEF_PATTERN);
        }

        log.info("════════════════════════════════════════════════════════════════════════════════");
        log.info("🔧 CONFIGURAÇÃO DO PARSER:");
        log.info("════════════════════════════════════════════════════════════════════════════════");
        log.info("Tipo do documento: {}", documentType);
        log.info("Padrões regex a tentar: {}", patternsToTry.size());
        for (int p = 0; p < patternsToTry.size(); p++) {
            log.info("  Padrão[{}]: {}", p + 1, patternsToTry.get(p).pattern());
        }
        log.info("════════════════════════════════════════════════════════════════════════════════");

        // ========== PROCESSAR LINHA POR LINHA E EXTRAIR RUBRICAS ==========
        log.info("════════════════════════════════════════════════════════════════════════════════");
        log.info("🎯 PROCESSANDO LINHA POR LINHA E EXTRAINDO RUBRICAS:");
        log.info("════════════════════════════════════════════════════════════════════════════════");

        // Processar cada linha e extrair rubricas diretamente
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i].trim();
            if (line.isEmpty()) {
                continue;
            }

            // Tentar cada padrão até encontrar um match
            for (Pattern pattern : patternsToTry) {
                Matcher lineMatcher = pattern.matcher(line);
                if (lineMatcher.matches()) {
                    log.info("✅ LINHA[{}] MATCH com padrão! [{}]", String.format("%3d", i + 1), line);

                    // Extrair dados do match
                    String codigo = normalizeCodigo(lineMatcher.group(1));
                    String descricao = null;
                    String referencia = null;
                    String valorStr = "";

                    if (documentType == DocumentType.CAIXA || documentType == DocumentType.CAIXA_FUNCEF) {
                        // Determinar qual padrão foi usado
                        boolean isLinhaUnicaPattern = pattern == CAIXA_PATTERN_LINHA_UNICA;
                        boolean isSimplesPattern = pattern == CAIXA_PATTERN_SIMPLES;
                        boolean isDataSeparadaPattern = pattern == CAIXA_PATTERN_DATA_SEPARADA;
                        boolean isNativePattern = pattern == CAIXA_PATTERN_NATIVE;
                        boolean isFlexivelPattern = pattern == CAIXA_PATTERN_FLEXIVEL;

                        if (isLinhaUnicaPattern) {
                            // Padrão Linha Única: código(1), descrição(2 opcional), [lookahead grupo 3],
                            // competência(4 opcional), prazo(5 opcional), valor(6)
                            // NOTA: O lookahead (?=...) contém grupo de captura que é grupo 3!
                            descricao = lineMatcher.group(2) != null
                                    ? normalizer.normalizeDescription(lineMatcher.group(2))
                                    : null;
                            referencia = lineMatcher.group(4); // Competência MM/YYYY (opcional) - era grupo 3!
                            String prazo = lineMatcher.group(5) != null ? lineMatcher.group(5).trim() : "";
                            valorStr = lineMatcher.group(6); // Valor - era grupo 5!

                            // Limpar valor de caracteres estranhos como "T" no início
                            if (valorStr != null) {
                                valorStr = valorStr.replaceFirst("^[A-Z]", "").trim();
                            } else {
                                valorStr = "";
                            }

                            log.info("  └─ ✅ RUBRICA CAIXA EXTRAÍDA (LINHA ÚNICA):");
                            log.info("      ├─ Código: [{}]", codigo);
                            log.info("      ├─ Descrição: [{}]", descricao != null ? descricao : "(não encontrada)");
                            log.info("      ├─ Competência: [{}]",
                                    referencia != null ? referencia : "(não encontrada)");
                            log.info("      ├─ Prazo: [{}]", prazo.isEmpty() ? "(vazio)" : prazo);
                            log.info("      └─ Valor: [{}]", valorStr);
                        } else if (isSimplesPattern) {
                            // Padrão Simples: código(1), descrição(2 opcional), [lookahead grupo 3],
                            // data(4 opcional), valor(5)
                            // NOTA: O lookahead (?=...) contém grupo de captura que é grupo 3!
                            descricao = lineMatcher.group(2) != null
                                    ? normalizer.normalizeDescription(lineMatcher.group(2))
                                    : null;
                            referencia = lineMatcher.group(4); // Data MM/YYYY (opcional) - era grupo 3!
                            valorStr = lineMatcher.group(5); // Valor - era grupo 4!

                            // Limpar valor de caracteres estranhos como "T" no início
                            if (valorStr != null) {
                                valorStr = valorStr.replaceFirst("^[A-Z]", "").trim();
                            } else {
                                valorStr = "";
                            }

                            log.info("  └─ ✅ RUBRICA CAIXA EXTRAÍDA (SIMPLES):");
                            log.info("      ├─ Código: [{}]", codigo);
                            log.info("      ├─ Descrição: [{}]", descricao != null ? descricao : "(não encontrada)");
                            log.info("      ├─ Valor: [{}]", valorStr);
                            log.info("      └─ Referência: [{}]", referencia != null ? referencia : "(não encontrada)");
                        } else if (isDataSeparadaPattern) {
                            // Padrão com Data Separada: código(1), descrição(2 opcional), [lookahead grupo
                            // 3],
                            // data(4 opcional), código_intermediário(5 opcional), valor(6)
                            // NOTA: O lookahead (?=...) contém grupo de captura que é grupo 3!
                            descricao = lineMatcher.group(2) != null
                                    ? normalizer.normalizeDescription(lineMatcher.group(2))
                                    : null;
                            referencia = lineMatcher.group(4); // Data MM/YYYY (opcional) - era grupo 3!
                            String codigoIntermediario = lineMatcher.group(5) != null ? lineMatcher.group(5).trim()
                                    : "";
                            valorStr = lineMatcher.group(6); // Valor - era grupo 5!

                            // Limpar valor de caracteres estranhos como "T" no início
                            if (valorStr != null) {
                                valorStr = valorStr.replaceFirst("^[A-Z]", "").trim();
                            } else {
                                valorStr = "";
                            }

                            log.info("  └─ ✅ RUBRICA CAIXA EXTRAÍDA (DATA SEPARADA):");
                            log.info("      ├─ Código: [{}]", codigo);
                            log.info("      ├─ Descrição: [{}]", descricao != null ? descricao : "(não encontrada)");
                            log.info("      ├─ Código intermediário: [{}]",
                                    codigoIntermediario.isEmpty() ? "(vazio)" : codigoIntermediario);
                            log.info("      ├─ Valor: [{}]", valorStr);
                            log.info("      └─ Referência: [{}]", referencia != null ? referencia : "(não encontrada)");
                        } else if (isNativePattern) {
                            // Padrão nativo: código(1), descrição(2 opcional), prazo(3 opcional), valor(4),
                            // data(5 colada)
                            descricao = lineMatcher.group(2) != null
                                    ? normalizer.normalizeDescription(lineMatcher.group(2))
                                    : null;
                            String prazo = lineMatcher.group(3) != null ? lineMatcher.group(3).trim() : "";
                            valorStr = lineMatcher.group(4);
                            referencia = lineMatcher.group(5); // Data está no grupo 5

                            log.info("  └─ ✅ RUBRICA CAIXA EXTRAÍDA (NATIVO):");
                            log.info("      ├─ Código: [{}]", codigo);
                            log.info("      ├─ Descrição: [{}]", descricao != null ? descricao : "(não encontrada)");
                            log.info("      ├─ Prazo: [{}]", prazo.isEmpty() ? "(vazio)" : prazo);
                            log.info("      ├─ Valor: [{}]", valorStr);
                            log.info("      └─ Referência: [{}]", referencia != null ? referencia : "(não encontrada)");
                        } else if (isFlexivelPattern) {
                            // Padrão Flexível: código(1), descrição(2 opcional), [lookahead grupo 3],
                            // competência(4 opcional), prazo(5 opcional), valor(6)
                            // NOTA: O lookahead (?=...) contém grupo de captura que é grupo 3!
                            descricao = lineMatcher.group(2) != null
                                    ? normalizer.normalizeDescription(lineMatcher.group(2))
                                    : null;
                            referencia = lineMatcher.group(4); // Competência MM/YYYY (opcional) - era grupo 3!
                            String prazo = lineMatcher.group(5) != null ? lineMatcher.group(5).trim() : "";
                            valorStr = lineMatcher.group(6); // Valor - era grupo 5!

                            // Limpar valor de caracteres estranhos como "T" no início
                            if (valorStr != null) {
                                valorStr = valorStr.replaceFirst("^[A-Z]", "").trim();
                            } else {
                                valorStr = "";
                            }

                            log.info("  └─ ✅ RUBRICA CAIXA EXTRAÍDA (FLEXÍVEL - código + valor):");
                            log.info("      ├─ Código: [{}]", codigo);
                            log.info("      ├─ Descrição: [{}]", descricao != null ? descricao : "(não encontrada)");
                            log.info("      ├─ Competência: [{}]",
                                    referencia != null ? referencia : "(não encontrada)");
                            log.info("      ├─ Prazo: [{}]", prazo.isEmpty() ? "(vazio)" : prazo);
                            log.info("      └─ Valor: [{}]", valorStr);
                        } else {
                            log.warn("  └─ ⚠️ Padrão desconhecido! Não foi possível extrair rubrica.");
                            break;
                        }
                    } else {
                        // FUNCEF: código(1), referência(2), descrição(3), valor(4)
                        referencia = lineMatcher.group(2); // Referência YYYY/MM
                        String descricaoRaw = lineMatcher.group(3);
                        descricao = normalizer.normalizeDescription(descricaoRaw);
                        valorStr = lineMatcher.group(4);

                        log.info("  └─ ✅ RUBRICA FUNCEF EXTRAÍDA:");
                        log.info("      ├─ Código: [{}]", codigo);
                        log.info("      ├─ Referência: [{}]", referencia);
                        log.info("      ├─ Descrição: [{}]", descricao);
                        log.info("      └─ Valor: [{}]", valorStr);
                    }

                    // Só adiciona se o valor foi extraído corretamente
                    if (!valorStr.isEmpty()) {
                        parsedLines.add(new ParsedLine(codigo, descricao, referencia, valorStr));
                    }

                    // Match encontrado, passar para próxima linha
                    break;
                }
            }
        }

        log.info("════════════════════════════════════════════════════════════════════════════════");
        log.info("📊 Total de rubricas extraídas linha por linha: {}", parsedLines.size());
        log.info("════════════════════════════════════════════════════════════════════════════════");
        log.info("Total de rubricas extraídas: {} (tipo: {})", parsedLines.size(), documentType);

        if (!parsedLines.isEmpty()) {
            log.info("════════════════════════════════════════════════════════════════════════════════");
            log.info("📝 RUBRICAS EXTRAÍDAS:");
            log.info("════════════════════════════════════════════════════════════════════════════════");
            for (int idx = 0; idx < parsedLines.size(); idx++) {
                ParsedLine pl = parsedLines.get(idx);
                log.info("Rubrica[{}]: código=[{}], descrição=[{}], referência=[{}], valor=[{}]",
                        idx + 1, pl.getCodigo(), pl.getDescricao(), pl.getReferencia(), pl.getValorStr());
            }
        }
        log.info("════════════════════════════════════════════════════════════════════════════════");

        return parsedLines;
    }

    /**
     * Extrai linhas de rubricas de um texto de página FUNCEF.
     * 
     * Formato FUNCEF: "2 033 2018/01 SUPL. APOS. TEMPO CONTRIB. BENEF. SALD.
     * 4.741,41"
     * ou: "4 430 2018/01 CONTRIBUIÇÃO EXTRAORDINARIA 2014 131,81"
     * 
     * A referência já vem na linha no formato YYYY/MM.
     */
    public List<ParsedLine> parseLinesFuncef(String pageText, String referenciaFromHeader) {
        // Usa o mesmo método parseLines que já tem o padrão correto
        return parseLines(pageText, DocumentType.FUNCEF);
    }

    /**
     * Normaliza código de rubrica removendo espaços.
     * Ex: "4 416" → "4416", "1034" → "1034"
     */
    private String normalizeCodigo(String codigo) {
        if (codigo == null) {
            return null;
        }
        return codigo.replaceAll("\\s+", "").trim();
    }
}
