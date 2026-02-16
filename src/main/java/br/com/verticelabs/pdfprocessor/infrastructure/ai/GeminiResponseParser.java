package br.com.verticelabs.pdfprocessor.infrastructure.ai;

import br.com.verticelabs.pdfprocessor.domain.model.PayrollEntry;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.util.ArrayList;
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
     * Parseia a resposta JSON do Gemini e cria PayrollEntry objects.
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

        try {
            JsonNode root = mapper.readTree(jsonResponse);

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
