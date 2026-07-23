package br.com.verticelabs.pdfprocessor.infrastructure.ai;

import br.com.verticelabs.pdfprocessor.domain.model.PayrollEntry;
import br.com.verticelabs.pdfprocessor.domain.service.IncomeTaxDeclarationService.IncomeTaxInfo;
import br.com.verticelabs.pdfprocessor.domain.service.IncomeTaxDeclarationService.IncomeTaxInfo.DependenteInfo;
import br.com.verticelabs.pdfprocessor.domain.service.IncomeTaxDeclarationService.IncomeTaxInfo.DoacaoEfetuada;
import br.com.verticelabs.pdfprocessor.domain.service.IncomeTaxDeclarationService.IncomeTaxInfo.FontePagadora;
import br.com.verticelabs.pdfprocessor.domain.service.IncomeTaxDeclarationService.IncomeTaxInfo.PagamentoEfetuado;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Parser para respostas JSON estruturadas do Gemini AI.
 * Converte o JSON do prompt CONTRACHEQUE_EXTRACTION em PayrollEntry objects.
 *
 * <h3>Formato JSON esperado do Gemini:</h3>
 * <pre>
 * {
 *   "nome": "FULANO DA SILVA",
 *   "cpf": "123.456.789-00",
 *   "competencia": "01/2024",
 *   "salarioBruto": 21475.01,
 *   "totalDescontos": 8782.78,
 *   "salarioLiquido": 12692.23,
 *   "rubricas": [
 *     {"codigo": "2002", "descricao": "SALARIO PADRAO", "provento": 10985.00, "desconto": null},
 *     {"codigo": "4313", "descricao": "INSS CONTRIBUICAO", "provento": null, "desconto": 908.85}
 *   ]
 * }
 * </pre>
 */
@Slf4j
public class GeminiResponseParser {

    private static final ObjectMapper mapper = new ObjectMapper();

    /**
     * Resultado do parse do JSON do Gemini.
     * Contém as entries e metadados do contracheque.
     */
    public static class ParsedPayrollData {
        private final String nome;
        private final String cpf;
        private final String matricula;
        private final String competencia;
        private final BigDecimal salarioBruto;
        private final BigDecimal totalDescontos;
        private final BigDecimal salarioLiquido;
        private final List<PayrollEntry> entries;

        public ParsedPayrollData(String nome, String cpf, String matricula, String competencia,
                                 BigDecimal salarioBruto, BigDecimal totalDescontos,
                                 BigDecimal salarioLiquido, List<PayrollEntry> entries) {
            this.nome = nome;
            this.cpf = cpf;
            this.matricula = matricula;
            this.competencia = competencia;
            this.salarioBruto = salarioBruto;
            this.totalDescontos = totalDescontos;
            this.salarioLiquido = salarioLiquido;
            this.entries = entries;
        }

        public String getNome() { return nome; }
        public String getCpf() { return cpf; }
        public String getMatricula() { return matricula; }
        public String getCompetencia() { return competencia; }
        public BigDecimal getSalarioBruto() { return salarioBruto; }
        public BigDecimal getTotalDescontos() { return totalDescontos; }
        public BigDecimal getSalarioLiquido() { return salarioLiquido; }
        public List<PayrollEntry> getEntries() { return entries; }
    }

    /**
     * Tenta reparar JSON truncado fechando arrays e objetos abertos.
     * Útil quando o Gemini retorna resposta cortada por MAX_TOKENS.
     *
     * @param json JSON possivelmente incompleto
     * @return JSON reparado (pode ainda ser inválido se muito corrompido)
     */
    public static String tryRepairTruncatedJson(String json) {
        if (json == null || json.trim().isEmpty()) return json;

        String trimmed = json.trim();

        // Se já termina com }, provavelmente está completo
        if (trimmed.endsWith("}")) return trimmed;

        log.info("🔧 Tentando reparar JSON truncado ({} chars)...", trimmed.length());

        // Contar brackets/braces abertos
        int openBraces = 0;
        int openBrackets = 0;
        boolean inString = false;
        boolean escaped = false;

        for (int i = 0; i < trimmed.length(); i++) {
            char c = trimmed.charAt(i);

            if (escaped) {
                escaped = false;
                continue;
            }

            if (c == '\\' && inString) {
                escaped = true;
                continue;
            }

            if (c == '"') {
                inString = !inString;
                continue;
            }

            if (!inString) {
                switch (c) {
                    case '{': openBraces++; break;
                    case '}': openBraces--; break;
                    case '[': openBrackets++; break;
                    case ']': openBrackets--; break;
                }
            }
        }

        // Se estamos dentro de uma string, fechar a string primeiro
        StringBuilder repaired = new StringBuilder(trimmed);
        if (inString) {
            repaired.append("\"");
        }

        // Remover possível rubrica incompleta (última entrada do array pode estar cortada)
        String current = repaired.toString();
        int lastCompleteEntry = current.lastIndexOf("},");
        int lastOpenBrace = current.lastIndexOf("{");
        if (lastOpenBrace > lastCompleteEntry && openBraces > 1) {
            // Há uma rubrica incompleta no final — remover até a última completa
            repaired = new StringBuilder(current.substring(0, lastCompleteEntry + 1));
            // Recalcular brackets
            openBraces = 0;
            openBrackets = 0;
            inString = false;
            escaped = false;
            for (int i = 0; i < repaired.length(); i++) {
                char c = repaired.charAt(i);
                if (escaped) { escaped = false; continue; }
                if (c == '\\' && inString) { escaped = true; continue; }
                if (c == '"') { inString = !inString; continue; }
                if (!inString) {
                    switch (c) {
                        case '{': openBraces++; break;
                        case '}': openBraces--; break;
                        case '[': openBrackets++; break;
                        case ']': openBrackets--; break;
                    }
                }
            }
        }

        // Fechar brackets e braces na ordem correta
        for (int i = 0; i < openBrackets; i++) {
            repaired.append("]");
        }
        for (int i = 0; i < openBraces; i++) {
            repaired.append("}");
        }

        String result = repaired.toString();
        log.info("🔧 JSON reparado: {} chars -> {} chars (adicionados {} brackets, {} braces)",
                trimmed.length(), result.length(), openBrackets, openBraces);

        return result;
    }

    /**
     * Parseia a resposta JSON do Gemini e cria PayrollEntry objects.
     * Se o JSON estiver truncado, tenta reparar antes de parsear.
     *
     * @param jsonResponse   resposta JSON do Gemini
     * @param documentId     ID do documento
     * @param tenantId       ID do tenant
     * @param origem         origem (CAIXA, FUNCEF)
     * @param pageNumber     número da página
     * @return ParsedPayrollData com entries e metadados, ou null se o parse falhar
     */
    public static ParsedPayrollData parsePayrollResponse(
            String jsonResponse,
            String documentId,
            String tenantId,
            String origem,
            int pageNumber) {

        if (jsonResponse == null || jsonResponse.trim().isEmpty()) {
            log.warn("Resposta JSON do Gemini vazia para página {}", pageNumber);
            return null;
        }

        // Tentar reparar JSON truncado antes de parsear
        String jsonToParse = jsonResponse.trim();
        if (!jsonToParse.endsWith("}")) {
            jsonToParse = tryRepairTruncatedJson(jsonToParse);
        }

        try {
            JsonNode root = mapper.readTree(jsonToParse);

            // Extrair metadados
            String nome = getTextOrNull(root, "nome");
            String cpf = getTextOrNull(root, "cpf");
            String matricula = getTextOrNull(root, "matricula");
            String competencia = getTextOrNull(root, "competencia");
            BigDecimal salarioBruto = getDecimalOrNull(root, "salarioBruto");
            BigDecimal totalDescontos = getDecimalOrNull(root, "totalDescontos");
            BigDecimal salarioLiquido = getDecimalOrNull(root, "salarioLiquido");

            log.info("Gemini JSON - Página {}: nome={}, cpf={}, competencia={}, bruto={}, descontos={}, líquido={}",
                    pageNumber, nome, cpf, competencia, salarioBruto, totalDescontos, salarioLiquido);

            // Normalizar competência de MM/YYYY para YYYY-MM
            String referenciaBase = normalizeCompetencia(competencia);

            // Extrair rubricas
            List<PayrollEntry> entries = new ArrayList<>();
            JsonNode rubricasNode = root.get("rubricas");

            if (rubricasNode != null && rubricasNode.isArray()) {
                for (JsonNode rubricaNode : rubricasNode) {
                    PayrollEntry entry = parseRubrica(rubricaNode, documentId, tenantId,
                            referenciaBase, origem, pageNumber);
                    if (entry != null) {
                        entries.add(entry);
                    }
                }
                log.info("Gemini JSON - Página {}: {} rubricas extraídas do JSON", pageNumber, entries.size());
            } else {
                log.warn("Gemini JSON - Página {}: campo 'rubricas' não encontrado ou não é array", pageNumber);
            }

            return new ParsedPayrollData(nome, cpf, matricula, competencia,
                    salarioBruto, totalDescontos, salarioLiquido, entries);

        } catch (Exception e) {
            log.error("Erro ao parsear JSON do Gemini para página {}: {}", pageNumber, e.getMessage());
            log.debug("JSON recebido: {}", jsonResponse.length() > 500
                    ? jsonResponse.substring(0, 500) + "..." : jsonResponse);
            return null;
        }
    }

    /**
     * Parseia uma rubrica individual do JSON.
     */
    private static PayrollEntry parseRubrica(JsonNode rubricaNode, String documentId,
                                              String tenantId, String referenciaBase,
                                              String origem, int pageNumber) {
        try {
            String codigo = getTextOrNull(rubricaNode, "codigo");
            if (codigo == null || codigo.trim().isEmpty()) {
                return null;
            }

            // Limpar código (remover espaços)
            codigo = codigo.replaceAll("\\s+", "");

            String descricao = getTextOrNull(rubricaNode, "descricao");
            BigDecimal provento = getDecimalOrNull(rubricaNode, "provento");
            BigDecimal desconto = getDecimalOrNull(rubricaNode, "desconto");
            BigDecimal referencia = getDecimalOrNull(rubricaNode, "referencia");

            // Determinar valor (provento é positivo, desconto é o valor absoluto)
            BigDecimal valor;
            if (provento != null && provento.compareTo(BigDecimal.ZERO) > 0) {
                valor = provento;
            } else if (desconto != null && desconto.compareTo(BigDecimal.ZERO) > 0) {
                valor = desconto; // Armazenar como positivo (o tipo provento/desconto é determinado pelo código)
            } else {
                // Pode ter valor zero
                valor = provento != null ? provento : (desconto != null ? desconto : BigDecimal.ZERO);
            }

            // Usar competência da rubrica se disponível, senão a do cabeçalho
            String entryReferencia = referenciaBase;
            String competenciaRubrica = getTextOrNull(rubricaNode, "competencia");
            if (competenciaRubrica != null) {
                String normalized = normalizeCompetencia(competenciaRubrica);
                if (normalized != null) {
                    entryReferencia = normalized;
                }
            }

            return PayrollEntry.builder()
                    .tenantId(tenantId)
                    .documentoId(documentId)
                    .rubricaCodigo(codigo)
                    .rubricaDescricao(descricao)
                    .referencia(entryReferencia)
                    .mesPagamento(referenciaBase)
                    .valor(valor)
                    .origem(origem)
                    .pagina(pageNumber)
                    .build();

        } catch (Exception e) {
            log.warn("Erro ao parsear rubrica do JSON: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Normaliza competência de MM/YYYY para YYYY-MM.
     */
    static String normalizeCompetencia(String competencia) {
        if (competencia == null) return null;
        String trimmed = competencia.trim();

        // Formato MM/YYYY → YYYY-MM
        if (trimmed.matches("^(0[1-9]|1[0-3])/\\d{4}$")) {
            String[] parts = trimmed.split("/");
            return parts[1] + "-" + parts[0];
        }

        // Formato YYYY-MM (já normalizado)
        if (trimmed.matches("^\\d{4}-(0[1-9]|1[0-3])$")) {
            return trimmed;
        }

        // Tentar outros formatos comuns
        if (trimmed.matches("^\\d{2}/\\d{4}$")) {
            String[] parts = trimmed.split("/");
            return parts[1] + "-" + parts[0];
        }

        log.warn("Formato de competência não reconhecido: {}", competencia);
        return null;
    }

    // ==========================================
    // DECLARAÇÃO DE IMPOSTO DE RENDA (IRPF)
    // ==========================================

    /**
     * Parseia a resposta JSON do Gemini (prompt IR_RESUMO_EXTRACTION) em um objeto IncomeTaxInfo.
     *
     * <p>O JSON esperado segue o formato definido em {@code GeminiPrompts.IR_RESUMO_EXTRACTION},
     * com campos como exercicio, anoCalendario, nome, cpf, rendimentosTributaveis,
     * deducoes (objeto aninhado), baseCalculoImposto, totalImpostoDevido, etc.</p>
     *
     * @param jsonResponse resposta JSON do Gemini
     * @return IncomeTaxInfo preenchido, ou null se o parse falhar
     */
    public static IncomeTaxInfo parseIncomeTaxResponse(String jsonResponse) {
        if (jsonResponse == null || jsonResponse.trim().isEmpty()) {
            log.warn("Resposta JSON do Gemini (IR) vazia.");
            return null;
        }

        String jsonToParse = jsonResponse.trim();
        if (!jsonToParse.endsWith("}")) {
            jsonToParse = tryRepairTruncatedJson(jsonToParse);
        }

        try {
            JsonNode root = mapper.readTree(jsonToParse);

            // Dados básicos
            String exercicio        = getTextOrNull(root, "exercicio");
            String anoCalendario    = getTextOrNull(root, "anoCalendario");
            String nome             = getTextOrNull(root, "nome");
            String cpf              = getTextOrNull(root, "cpf");
            String tipoDeclaracao   = getTextOrNull(root, "tipoDeclaracao");

            // Tipo de tributação derivado do modeloDeclaracao retornado pelo Gemini
            // Gemini retorna "COMPLETA" para Deduções Legais e "SIMPLIFICADA" para Desconto Simplificado
            String modeloDeclaracao = getTextOrNull(root, "modeloDeclaracao");
            String tipoTributacao = null;
            if (modeloDeclaracao != null) {
                if (modeloDeclaracao.toUpperCase().contains("COMPLET")) {
                    tipoTributacao = "COMPLETO";
                } else if (modeloDeclaracao.toUpperCase().contains("SIMPLIF")) {
                    tipoTributacao = "SIMPLIFICADO";
                }
            }

            // Rendimentos e base
            BigDecimal rendimentosTributaveis = getDecimalOrNull(root, "rendimentosTributaveis");
            JsonNode rendimentosNode = root.get("rendimentos");
            BigDecimal rendTitularPJ = null;
            BigDecimal rendDepPJ = null;
            BigDecimal rendTitularPF = null;
            BigDecimal rendDepPF = null;
            BigDecimal rendAcumTitular = null;
            BigDecimal rendAcumDep = null;
            BigDecimal resultadoRural = null;
            if (rendimentosNode != null && rendimentosNode.isObject()) {
                rendTitularPJ = getDecimalOrNull(rendimentosNode, "titularPJ");
                rendDepPJ = getDecimalOrNull(rendimentosNode, "dependentesPJ");
                rendTitularPF = getDecimalOrNull(rendimentosNode, "titularPF");
                rendDepPF = getDecimalOrNull(rendimentosNode, "dependentesPF");
                rendAcumTitular = getDecimalOrNull(rendimentosNode, "acumuladosTitular");
                rendAcumDep = getDecimalOrNull(rendimentosNode, "acumuladosDependentes");
                resultadoRural = getDecimalOrNull(rendimentosNode, "atividadeRural");
                BigDecimal rendTotal = getDecimalOrNull(rendimentosNode, "total");
                if (rendimentosTributaveis == null) {
                    rendimentosTributaveis = rendTotal;
                }
            }
            BigDecimal baseCalculoImposto     = getDecimalOrNull(root, "baseCalculoImposto");

            // IMPOSTO DEVIDO
            BigDecimal impostoDevido                      = getDecimalOrNull(root, "impostoDevido");
            BigDecimal deducaoIncentivo                   = getDecimalOrNull(root, "deducaoIncentivo");
            BigDecimal impostoDevidoI                     = getDecimalOrNull(root, "impostoDevidoI");
            BigDecimal contribuicaoPrevEmpregadorDomestico = getDecimalOrNull(root, "contribuicaoPrevEmpregadorDomestico");
            BigDecimal impostoDevidoII                    = getDecimalOrNull(root, "impostoDevidoII");
            BigDecimal impostoDevidoRRA                   = getDecimalOrNull(root, "impostoDevidoRRA");
            BigDecimal totalImpostoDevido                 = getDecimalOrNull(root, "totalImpostoDevido");

            // RESULTADO
            BigDecimal saldoImpostoPagar = getDecimalOrNull(root, "saldoImpostoPagar");
            BigDecimal impostoRestituir  = getDecimalOrNull(root, "impostoRestituir");

            // Outros resumo
            BigDecimal descontoSimplificado = getDecimalOrNull(root, "descontoSimplificado");
            BigDecimal aliquotaEfetiva      = getDecimalOrNull(root, "aliquotaEfetiva");

            // DEDUÇÕES (objeto aninhado)
            JsonNode deducoesNode = root.get("deducoes");
            BigDecimal deducoesTotal               = null;
            BigDecimal deducoesContribPrevOficial  = null;
            BigDecimal deducoesContribPrevRRA      = null;
            BigDecimal deducoesContribPrevCompl    = null;
            BigDecimal deducoesDependentes         = null;
            BigDecimal deducoesInstrucao           = null;
            BigDecimal deducoesMedicas             = null;
            BigDecimal deducoesPensaoJudicial      = null;
            BigDecimal deducoesPensaoEscritura     = null;
            BigDecimal deducoesPensaoRRA           = null;
            BigDecimal deducoesLivroCaixa          = null;

            if (deducoesNode != null && deducoesNode.isObject()) {
                deducoesTotal              = getDecimalOrNull(deducoesNode, "total");
                deducoesContribPrevOficial = getDecimalOrNull(deducoesNode, "previdenciaOficial");
                deducoesContribPrevRRA     = getDecimalOrNull(deducoesNode, "previdenciaOficialRRA");
                deducoesContribPrevCompl   = getDecimalOrNull(deducoesNode, "previdenciaComplementar");
                deducoesDependentes        = getDecimalOrNull(deducoesNode, "dependentes");
                deducoesInstrucao          = getDecimalOrNull(deducoesNode, "instrucao");
                deducoesMedicas            = getDecimalOrNull(deducoesNode, "despesasMedicas");
                deducoesPensaoJudicial     = getDecimalOrNull(deducoesNode, "pensaoAlimenticiaJudicial");
                deducoesPensaoEscritura    = getDecimalOrNull(deducoesNode, "pensaoAlimenticiaEscritura");
                deducoesPensaoRRA          = getDecimalOrNull(deducoesNode, "pensaoAlimenticiaRRA");
                deducoesLivroCaixa         = getDecimalOrNull(deducoesNode, "livrosCaixa");
            }

            // IMPOSTO PAGO (objeto aninhado)
            JsonNode impostoPagoNode = root.get("impostoPago");
            BigDecimal impostoPagoTotal              = null;
            BigDecimal impostoRetidoFonteTitular     = null;
            BigDecimal impostoRetidoFonteDependentes = null;
            BigDecimal carneLeaoTitular              = null;
            BigDecimal carneLeaoDependentes          = null;
            BigDecimal impostoComplementar           = null;
            BigDecimal impostoPagoExterior           = null;
            BigDecimal impostoRetidoFonteLei11033    = null;
            BigDecimal impostoRetidoRRA              = null;

            if (impostoPagoNode != null && impostoPagoNode.isObject()) {
                impostoPagoTotal              = getDecimalOrNull(impostoPagoNode, "total");
                impostoRetidoFonteTitular     = getDecimalOrNull(impostoPagoNode, "retidoFonteTitular");
                impostoRetidoFonteDependentes = getDecimalOrNull(impostoPagoNode, "retidoFonteDependentes");
                carneLeaoTitular              = getDecimalOrNull(impostoPagoNode, "carneLeaoTitular");
                carneLeaoDependentes          = getDecimalOrNull(impostoPagoNode, "carneLeaoDependentes");
                impostoComplementar           = getDecimalOrNull(impostoPagoNode, "complementar");
                impostoPagoExterior           = getDecimalOrNull(impostoPagoNode, "exterior");
                impostoRetidoFonteLei11033    = getDecimalOrNull(impostoPagoNode, "retidoFonteLei11033");
                impostoRetidoRRA              = getDecimalOrNull(impostoPagoNode, "retidoRRA");
            }

            log.info("Gemini IR parse — nome={}, cpf={}, exercicio={}, anoCalendario={}, " +
                    "rendimentos={}, totalDevido={}, saldoPagar={}, restituir={}",
                    nome, cpf, exercicio, anoCalendario,
                    rendimentosTributaveis, totalImpostoDevido, saldoImpostoPagar, impostoRestituir);

            IncomeTaxInfo parsed = new IncomeTaxInfo(
                    nome, cpf, anoCalendario, exercicio,
                    baseCalculoImposto, impostoDevido, deducaoIncentivo, impostoDevidoI,
                    contribuicaoPrevEmpregadorDomestico, impostoDevidoII, impostoDevidoRRA,
                    totalImpostoDevido, saldoImpostoPagar,
                    rendimentosTributaveis, deducoesTotal,
                    impostoRetidoFonteTitular, impostoPagoTotal, impostoRestituir,
                    deducoesContribPrevOficial, deducoesContribPrevRRA, deducoesContribPrevCompl,
                    deducoesDependentes, deducoesInstrucao, deducoesMedicas,
                    deducoesPensaoJudicial, deducoesPensaoEscritura, deducoesPensaoRRA, deducoesLivroCaixa,
                    impostoRetidoFonteDependentes, carneLeaoTitular, carneLeaoDependentes,
                    impostoComplementar, impostoPagoExterior, impostoRetidoFonteLei11033, impostoRetidoRRA,
                    descontoSimplificado, aliquotaEfetiva,
                    tipoTributacao, null, null, tipoDeclaracao, null,
                    null, null, null, null,
                    null, null,
                    Collections.<PagamentoEfetuado>emptyList(),
                    Collections.<FontePagadora>emptyList(),
                    null,
                    Collections.<DependenteInfo>emptyList(),
                    null,
                    Collections.<DependenteInfo>emptyList(),
                    rendTitularPJ, rendDepPJ, rendTitularPF, rendDepPF,
                    resultadoRural, rendAcumTitular, rendAcumDep,
                    null, null, null, null, null, null, null, null, null, null, null,
                    Collections.<DoacaoEfetuada>emptyList()
            );
            return br.com.verticelabs.pdfprocessor.infrastructure.incometax.IncomeTaxGeminiHelper.enrich(parsed);

        } catch (Exception e) {
            log.error("Erro ao parsear JSON do Gemini (IR): {}", e.getMessage());
            log.debug("JSON recebido: {}", jsonResponse.length() > 500
                    ? jsonResponse.substring(0, 500) + "..." : jsonResponse);
            return null;
        }
    }

    /**
     * Parseia resposta do prompt {@code IR_PAGAMENTOS_EXTRACTION}.
     *
     * @return lista de pagamentos (nunca null; vazia se ausente/inválido)
     */
    public static List<PagamentoEfetuado> parsePagamentosResponse(String jsonResponse) {
        List<PagamentoEfetuado> result = new ArrayList<>();
        if (jsonResponse == null || jsonResponse.trim().isEmpty()) {
            return result;
        }
        try {
            JsonNode root = mapper.readTree(jsonResponse.trim());
            JsonNode arr = root.get("pagamentos");
            if (arr == null || !arr.isArray()) {
                return result;
            }
            for (JsonNode item : arr) {
                String codigo = getTextOrNull(item, "codigo");
                if (codigo == null || codigo.isBlank()) {
                    continue;
                }
                String nome = getTextOrNull(item, "nomeBeneficiario");
                String cpfCnpj = getTextOrNull(item, "cpfCnpj");
                BigDecimal valorPago = getDecimalOrNull(item, "valorPago");
                BigDecimal parc = getDecimalOrNull(item, "parcNaoDedutivel");
                if (valorPago == null) {
                    continue;
                }
                result.add(new PagamentoEfetuado(codigo.trim(), nome, cpfCnpj, valorPago, parc, null));
            }
            log.info("Gemini pagamentos parse — {} item(ns)", result.size());
        } catch (Exception e) {
            log.error("Erro ao parsear JSON de pagamentos Gemini: {}", e.getMessage());
        }
        return result;
    }

    /**
     * Resultado da extração Gemini de dependentes (lista + total).
     */
    public record DependentesExtractionResult(List<DependenteInfo> dependentes, BigDecimal totalDeducao) {
    }

    /**
     * Parseia resposta do prompt {@code IR_DEPENDENTES_EXTRACTION}.
     */
    public static DependentesExtractionResult parseDependentesResponse(String jsonResponse) {
        List<DependenteInfo> dependentes = new ArrayList<>();
        BigDecimal total = null;
        if (jsonResponse == null || jsonResponse.trim().isEmpty()) {
            return new DependentesExtractionResult(dependentes, null);
        }
        try {
            JsonNode root = mapper.readTree(jsonResponse.trim());
            total = getDecimalOrNull(root, "totalDeducaoDependentes");
            JsonNode arr = root.get("dependentes");
            if (arr != null && arr.isArray()) {
                for (JsonNode item : arr) {
                    String codigo = getTextOrNull(item, "codigo");
                    String nome = getTextOrNull(item, "nome");
                    if (nome == null || nome.isBlank()) {
                        continue;
                    }
                    dependentes.add(new DependenteInfo(
                            codigo,
                            nome,
                            getTextOrNull(item, "dataNascimento"),
                            getTextOrNull(item, "cpf")));
                }
            }
            log.info("Gemini dependentes parse — {} pessoa(s), total={}", dependentes.size(), total);
        } catch (Exception e) {
            log.error("Erro ao parsear JSON de dependentes Gemini: {}", e.getMessage());
        }
        return new DependentesExtractionResult(dependentes, total);
    }

    private static String getTextOrNull(JsonNode node, String field) {
        JsonNode child = node.get(field);
        if (child == null || child.isNull() || child.asText().equalsIgnoreCase("null")) {
            return null;
        }
        String text = child.asText().trim();
        return text.isEmpty() ? null : text;
    }

    private static BigDecimal getDecimalOrNull(JsonNode node, String field) {
        JsonNode child = node.get(field);
        if (child == null || child.isNull()) {
            return null;
        }
        try {
            if (child.isNumber()) {
                return child.decimalValue();
            }
            String text = child.asText().trim();
            if (text.isEmpty() || text.equalsIgnoreCase("null")) {
                return null;
            }
            // Limpar formatação BR (1.234,56 → 1234.56)
            text = text.replaceAll("\\.", "").replace(",", ".");
            return new BigDecimal(text);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
