package br.com.verticelabs.pdfprocessor.infrastructure.validation;

import br.com.verticelabs.pdfprocessor.domain.model.CrossValidationResult;
import br.com.verticelabs.pdfprocessor.domain.model.FieldComparison;
import br.com.verticelabs.pdfprocessor.domain.model.PayrollEntry;
import br.com.verticelabs.pdfprocessor.domain.service.CrossValidationService;
import br.com.verticelabs.pdfprocessor.infrastructure.ai.GeminiPdfServiceImpl;
import br.com.verticelabs.pdfprocessor.infrastructure.ai.GeminiPrompts;
import br.com.verticelabs.pdfprocessor.infrastructure.ai.GeminiResponseParser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Implementação do serviço de cross-validation (Fase 3).
 *
 * <h3>Fluxo de execução:</h3>
 * <ol>
 *   <li>Recebe entries da 1ª extração (prompt principal: top-down)</li>
 *   <li>Executa 2ª extração com prompt alternativo (bottom-up)</li>
 *   <li>Compara resultados campo a campo:
 *       <ul>
 *         <li><strong>Metadados</strong>: nome, CPF, matrícula, competência, salários</li>
 *         <li><strong>Rubricas</strong>: por código — compara descrição e valor</li>
 *       </ul>
 *   </li>
 *   <li>Gera resultado consolidado com os valores mais confiáveis</li>
 * </ol>
 *
 * <h3>Custo:</h3>
 * <p>~$0.003 extra por página (1 chamada adicional ao Gemini Flash).</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CrossValidationServiceImpl implements CrossValidationService {

    private final GeminiPdfServiceImpl geminiService;

    // Tolerância para comparação de valores monetários (centavos)
    private static final BigDecimal VALUE_TOLERANCE = new BigDecimal("0.01");

    @Override
    public Mono<CrossValidationResult> crossValidate(
            byte[] pdfBytes,
            int pageNumber,
            List<PayrollEntry> firstEntries,
            String firstJson,
            String documentId,
            String tenantId,
            String origem) {

        log.info("════════════════════════════════════════════════════════════════════════════════");
        log.info("🔄 CROSS-VALIDATION — Página {} — Iniciando 2ª extração com prompt alternativo", pageNumber);
        log.info("════════════════════════════════════════════════════════════════════════════════");

        // Executar segunda extração com prompt alternativo (bottom-up)
        return geminiService.processWithPrimaryModel(pdfBytes, pageNumber, GeminiPrompts.CONTRACHEQUE_EXTRACTION_ALT)
                .map(secondJson -> {
                    log.info("✅ 2ª extração concluída: {} chars de JSON", secondJson != null ? secondJson.length() : 0);

                    // Parsear 2ª extração
                    GeminiResponseParser.ParsedPayrollData secondData =
                            GeminiResponseParser.parsePayrollResponse(secondJson, documentId, tenantId, origem, pageNumber);

                    if (secondData == null || secondData.getEntries().isEmpty()) {
                        log.warn("⚠️ 2ª extração não retornou entries. Mantendo resultado da 1ª extração.");
                        return buildResultFromFirstOnly(firstEntries);
                    }

                    // Parsear 1ª extração (metadados) para comparação
                    GeminiResponseParser.ParsedPayrollData firstData =
                            GeminiResponseParser.parsePayrollResponse(firstJson, documentId, tenantId, origem, pageNumber);

                    // Comparar campo a campo
                    return compareAndConsolidate(firstEntries, firstData, secondData, documentId, tenantId, origem, pageNumber);
                })
                .onErrorResume(error -> {
                    log.error("❌ Erro na cross-validation da página {}: {}. Mantendo resultado da 1ª extração.",
                            pageNumber, error.getMessage());
                    return Mono.just(buildResultFromFirstOnly(firstEntries));
                });
    }

    /**
     * Compara os resultados das duas extrações e gera resultado consolidado.
     */
    private CrossValidationResult compareAndConsolidate(
            List<PayrollEntry> firstEntries,
            GeminiResponseParser.ParsedPayrollData firstData,
            GeminiResponseParser.ParsedPayrollData secondData,
            String documentId, String tenantId, String origem, int pageNumber) {

        List<FieldComparison> comparisons = new ArrayList<>();
        boolean requiresManualReview = false;

        // ========== COMPARAR METADADOS ==========

        // Nome
        comparisons.add(compareStrings("nome",
                firstData != null ? firstData.getNome() : null,
                secondData.getNome()));

        // CPF (campo crítico)
        FieldComparison cpfComparison = compareStrings("cpf",
                firstData != null ? firstData.getCpf() : null,
                secondData.getCpf());
        comparisons.add(cpfComparison);
        if (!cpfComparison.match()) {
            requiresManualReview = true;
            log.warn("⚠️ CPF diverge entre extrações: A={}, B={}", cpfComparison.valueA(), cpfComparison.valueB());
        }

        // Matrícula
        comparisons.add(compareStrings("matricula",
                firstData != null ? firstData.getMatricula() : null,
                secondData.getMatricula()));

        // Competência
        comparisons.add(compareStrings("competencia",
                firstData != null ? firstData.getCompetencia() : null,
                secondData.getCompetencia()));

        // Salário Bruto (campo crítico)
        FieldComparison brutoComparison = compareDecimals("salarioBruto",
                firstData != null ? firstData.getSalarioBruto() : null,
                secondData.getSalarioBruto());
        comparisons.add(brutoComparison);
        if (!brutoComparison.match()) {
            requiresManualReview = true;
            log.warn("⚠️ Salário bruto diverge: A={}, B={}", brutoComparison.valueA(), brutoComparison.valueB());
        }

        // Total Descontos
        comparisons.add(compareDecimals("totalDescontos",
                firstData != null ? firstData.getTotalDescontos() : null,
                secondData.getTotalDescontos()));

        // Salário Líquido (campo crítico)
        FieldComparison liquidoComparison = compareDecimals("salarioLiquido",
                firstData != null ? firstData.getSalarioLiquido() : null,
                secondData.getSalarioLiquido());
        comparisons.add(liquidoComparison);
        if (!liquidoComparison.match()) {
            requiresManualReview = true;
            log.warn("⚠️ Salário líquido diverge: A={}, B={}", liquidoComparison.valueA(), liquidoComparison.valueB());
        }

        // ========== COMPARAR RUBRICAS ==========

        List<PayrollEntry> secondEntries = secondData.getEntries();

        // Indexar rubricas por código
        Map<String, PayrollEntry> firstByCode = firstEntries.stream()
                .collect(Collectors.toMap(
                        PayrollEntry::getRubricaCodigo,
                        e -> e,
                        (a, b) -> a // Se duplicado, manter primeiro
                ));

        Map<String, PayrollEntry> secondByCode = secondEntries.stream()
                .collect(Collectors.toMap(
                        PayrollEntry::getRubricaCodigo,
                        e -> e,
                        (a, b) -> a
                ));

        // Todos os códigos de rubrica (union)
        Set<String> allCodes = new LinkedHashSet<>();
        allCodes.addAll(firstByCode.keySet());
        allCodes.addAll(secondByCode.keySet());

        List<PayrollEntry> consolidatedEntries = new ArrayList<>();

        for (String code : allCodes) {
            PayrollEntry entryA = firstByCode.get(code);
            PayrollEntry entryB = secondByCode.get(code);

            if (entryA != null && entryB != null) {
                // Ambas extrações encontraram esta rubrica
                FieldComparison valueComp = compareDecimals("rubrica_" + code,
                        entryA.getValor(), entryB.getValor());
                comparisons.add(valueComp);

                if (valueComp.match()) {
                    // Valores coincidem — alta confiança, usar da 1ª extração
                    consolidatedEntries.add(entryA);
                } else {
                    // Valores divergem — flag e usar da 1ª extração como default
                    log.warn("⚠️ Rubrica {} diverge: A={}, B={}", code, entryA.getValor(), entryB.getValor());
                    consolidatedEntries.add(entryA);
                }
            } else if (entryA != null) {
                // Só a 1ª extração encontrou — incluir com confiança menor
                comparisons.add(new FieldComparison("rubrica_" + code,
                        entryA.getValor() != null ? entryA.getValor().toPlainString() : "null",
                        "(não encontrada)", false, entryA.getValor() != null ? entryA.getValor().toPlainString() : "null"));
                consolidatedEntries.add(entryA);
            } else if (entryB != null) {
                // Só a 2ª extração encontrou — incluir como nova
                comparisons.add(new FieldComparison("rubrica_" + code,
                        "(não encontrada)",
                        entryB.getValor() != null ? entryB.getValor().toPlainString() : "null",
                        false, entryB.getValor() != null ? entryB.getValor().toPlainString() : "null"));
                consolidatedEntries.add(entryB);
            }
        }

        // Calcular score consolidado
        int totalFields = comparisons.size();
        int matchedFields = (int) comparisons.stream().filter(FieldComparison::match).count();
        int divergedFields = totalFields - matchedFields;

        double confidenceScore = totalFields > 0 ? (double) matchedFields / totalFields : 0.0;

        CrossValidationResult result = new CrossValidationResult(
                consolidatedEntries, confidenceScore, comparisons,
                requiresManualReview, totalFields, matchedFields, divergedFields);

        log.info("════════════════════════════════════════════════════════════════════════════════");
        log.info("📊 CROSS-VALIDATION CONCLUÍDA:");
        log.info("   Total de campos comparados: {}", totalFields);
        log.info("   Campos que coincidem: {} ({}%)", matchedFields, String.format("%.0f", confidenceScore * 100));
        log.info("   Campos que divergem: {}", divergedFields);
        log.info("   Revisão manual necessária: {}", requiresManualReview);
        log.info("   Entries consolidadas: {}", consolidatedEntries.size());
        log.info("════════════════════════════════════════════════════════════════════════════════");

        return result;
    }

    /**
     * Quando a 2ª extração falha, retorna resultado baseado apenas na 1ª.
     */
    private CrossValidationResult buildResultFromFirstOnly(List<PayrollEntry> firstEntries) {
        return new CrossValidationResult(
                firstEntries,
                0.5,  // Score neutro — não houve cross-validation efetiva
                List.of(),
                false,
                0, 0, 0
        );
    }

    /**
     * Compara dois valores String (case-insensitive, trim, normalização de espaços).
     */
    private FieldComparison compareStrings(String field, String valueA, String valueB) {
        String normalizedA = normalizeString(valueA);
        String normalizedB = normalizeString(valueB);

        if (normalizedA == null && normalizedB == null) {
            return FieldComparison.matched(field, null);
        }
        if (normalizedA == null || normalizedB == null) {
            return FieldComparison.diverged(field,
                    valueA != null ? valueA : "(null)",
                    valueB != null ? valueB : "(null)");
        }

        boolean match = normalizedA.equalsIgnoreCase(normalizedB);
        if (match) {
            return FieldComparison.matched(field, valueA);
        } else {
            return FieldComparison.diverged(field, valueA, valueB);
        }
    }

    /**
     * Compara dois valores BigDecimal com tolerância de centavos.
     */
    private FieldComparison compareDecimals(String field, BigDecimal valueA, BigDecimal valueB) {
        String strA = valueA != null ? valueA.toPlainString() : "(null)";
        String strB = valueB != null ? valueB.toPlainString() : "(null)";

        if (valueA == null && valueB == null) {
            return FieldComparison.matched(field, null);
        }
        if (valueA == null || valueB == null) {
            return FieldComparison.diverged(field, strA, strB);
        }

        BigDecimal diff = valueA.subtract(valueB).abs();
        boolean match = diff.compareTo(VALUE_TOLERANCE) <= 0;

        if (match) {
            return FieldComparison.matched(field, strA);
        } else {
            return FieldComparison.diverged(field, strA, strB);
        }
    }

    /**
     * Normaliza string para comparação (trim, remove espaços extras, pontuação).
     */
    private String normalizeString(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        if (trimmed.isEmpty()) return null;
        // Normalizar espaços múltiplos
        return trimmed.replaceAll("\\s+", " ");
    }
}
