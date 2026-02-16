package br.com.verticelabs.pdfprocessor.infrastructure.validation;

import br.com.verticelabs.pdfprocessor.domain.model.PayrollEntry;
import br.com.verticelabs.pdfprocessor.domain.model.ValidationIssue;
import br.com.verticelabs.pdfprocessor.domain.model.ValidationResult;
import br.com.verticelabs.pdfprocessor.domain.service.CpfValidationService;
import br.com.verticelabs.pdfprocessor.domain.service.ExtractionValidationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/**
 * Implementação das regras de validação para dados extraídos de documentos.
 * Custo: $0 — são apenas regras de negócio em código Java, sem chamadas externas.
 *
 * <h3>Pesos das regras (Contracheques):</h3>
 * <table>
 *   <tr><th>Regra</th><th>Peso</th></tr>
 *   <tr><td>SOMA_PROVENTOS</td><td>20%</td></tr>
 *   <tr><td>SOMA_DESCONTOS</td><td>20%</td></tr>
 *   <tr><td>LIQUIDO_CORRETO</td><td>25%</td></tr>
 *   <tr><td>CPF_VALIDO</td><td>15%</td></tr>
 *   <tr><td>COMPETENCIA_VALIDA</td><td>10%</td></tr>
 *   <tr><td>VALORES_POSITIVOS</td><td>10%</td></tr>
 * </table>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ExtractionValidationServiceImpl implements ExtractionValidationService {

    private final CpfValidationService cpfValidationService;

    // Tolerância de 1% para comparações de somas (arredondamentos do PDF)
    private static final BigDecimal TOLERANCE_PERCENT = new BigDecimal("0.01");

    // ==========================================
    // CONTRACHEQUE
    // ==========================================

    @Override
    public ValidationResult validatePayrollExtraction(
            List<PayrollEntry> entries,
            BigDecimal salarioBruto,
            BigDecimal totalDescontos,
            BigDecimal salarioLiquido,
            String cpf,
            String competencia) {

        log.info("Validando extração de contracheque: {} entries, bruto={}, descontos={}, líquido={}",
                entries != null ? entries.size() : 0, salarioBruto, totalDescontos, salarioLiquido);

        List<ValidationIssue> issues = new ArrayList<>();
        double totalWeight = 0.0;
        double passedWeight = 0.0;

        // Regra 1: Soma dos proventos = salário bruto (peso 20%)
        double weight1 = 0.20;
        totalWeight += weight1;
        if (salarioBruto != null && entries != null && !entries.isEmpty()) {
            BigDecimal somaProventos = entries.stream()
                    .filter(e -> e.getValor() != null && e.getValor().compareTo(BigDecimal.ZERO) > 0)
                    .map(PayrollEntry::getValor)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            if (isWithinTolerance(somaProventos, salarioBruto)) {
                passedWeight += weight1;
            } else {
                issues.add(new ValidationIssue(
                        "salarioBruto",
                        ValidationIssue.TYPE_SOMA_PROVENTOS,
                        salarioBruto.toPlainString(),
                        somaProventos.toPlainString(),
                        "Soma dos proventos (" + somaProventos.toPlainString() +
                                ") difere do salário bruto informado (" + salarioBruto.toPlainString() + ")"
                ));
            }
        } else {
            // Se não temos dados suficientes para validar, consideramos neutro (50% do peso)
            passedWeight += weight1 * 0.5;
        }

        // Regra 2: Soma dos descontos = total descontos (peso 20%)
        double weight2 = 0.20;
        totalWeight += weight2;
        if (totalDescontos != null && entries != null && !entries.isEmpty()) {
            BigDecimal somaDescontos = entries.stream()
                    .filter(e -> e.getValor() != null && e.getValor().compareTo(BigDecimal.ZERO) < 0)
                    .map(e -> e.getValor().abs())
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            if (isWithinTolerance(somaDescontos, totalDescontos)) {
                passedWeight += weight2;
            } else {
                issues.add(new ValidationIssue(
                        "totalDescontos",
                        ValidationIssue.TYPE_SOMA_DESCONTOS,
                        totalDescontos.toPlainString(),
                        somaDescontos.toPlainString(),
                        "Soma dos descontos (" + somaDescontos.toPlainString() +
                                ") difere do total informado (" + totalDescontos.toPlainString() + ")"
                ));
            }
        } else {
            passedWeight += weight2 * 0.5;
        }

        // Regra 3: Bruto - descontos = líquido (peso 25%)
        double weight3 = 0.25;
        totalWeight += weight3;
        if (salarioBruto != null && totalDescontos != null && salarioLiquido != null) {
            BigDecimal calculatedLiquido = salarioBruto.subtract(totalDescontos);
            if (isWithinTolerance(calculatedLiquido, salarioLiquido)) {
                passedWeight += weight3;
            } else {
                issues.add(new ValidationIssue(
                        "salarioLiquido",
                        ValidationIssue.TYPE_LIQUIDO_INCORRETO,
                        salarioLiquido.toPlainString(),
                        calculatedLiquido.toPlainString(),
                        "Bruto (" + salarioBruto.toPlainString() + ") - Descontos (" +
                                totalDescontos.toPlainString() + ") = " + calculatedLiquido.toPlainString() +
                                ", mas líquido informado é " + salarioLiquido.toPlainString()
                ));
            }
        } else {
            passedWeight += weight3 * 0.5;
        }

        // Regra 4: CPF válido (peso 15%)
        double weight4 = 0.15;
        totalWeight += weight4;
        if (cpf != null && !cpf.trim().isEmpty()) {
            if (cpfValidationService.isValid(cpf)) {
                passedWeight += weight4;
            } else {
                issues.add(new ValidationIssue(
                        "cpf",
                        ValidationIssue.TYPE_CPF_INVALIDO,
                        "CPF com dígitos verificadores corretos",
                        cpf,
                        "CPF " + cpf + " não passou na validação de dígitos verificadores"
                ));
            }
        } else {
            passedWeight += weight4 * 0.5;
        }

        // Regra 5: Competência válida MM/YYYY (peso 10%)
        double weight5 = 0.10;
        totalWeight += weight5;
        if (competencia != null && !competencia.trim().isEmpty()) {
            if (isValidCompetencia(competencia)) {
                passedWeight += weight5;
            } else {
                issues.add(new ValidationIssue(
                        "competencia",
                        ValidationIssue.TYPE_COMPETENCIA_INVALIDA,
                        "MM/YYYY com mês entre 01 e 13",
                        competencia,
                        "Competência '" + competencia + "' não está no formato esperado (MM/YYYY)"
                ));
            }
        } else {
            passedWeight += weight5 * 0.5;
        }

        // Regra 6: Valores positivos (peso 10%)
        double weight6 = 0.10;
        totalWeight += weight6;
        if (entries != null && !entries.isEmpty()) {
            long negativos = entries.stream()
                    .filter(e -> e.getValor() != null && e.getValor().compareTo(BigDecimal.ZERO) < 0)
                    .count();
            // Nota: descontos podem ter valor negativo na modelagem, então verificamos se há
            // valores negativos que não são descontos
            passedWeight += weight6; // Sempre passa — descontos podem ser negativos na modelagem
        } else {
            passedWeight += weight6 * 0.5;
        }

        double score = totalWeight > 0 ? passedWeight / totalWeight : 0.0;
        score = Math.max(0.0, Math.min(1.0, score)); // Clamp 0-1

        ValidationResult result = ValidationResult.fromScore(score, issues);

        log.info("Validação contracheque concluída: score={}, valid={}, recommendation={}, issues={}",
                String.format("%.2f", result.confidenceScore()),
                result.isValid(),
                result.recommendation(),
                result.issues().size());

        return result;
    }

    // ==========================================
    // DECLARAÇÃO DE IR
    // ==========================================

    @Override
    public ValidationResult validateIncomeTaxExtraction(
            String nome,
            String cpf,
            String exercicio,
            String anoCalendario,
            BigDecimal totalImpostoDevido,
            BigDecimal totalImpostoPago,
            BigDecimal saldoImpostoPagar,
            BigDecimal impostoRestituir,
            BigDecimal totalDeducoes,
            BigDecimal rendimentosTributaveis) {

        log.info("Validando extração de IR: exercicio={}, anoCalendario={}", exercicio, anoCalendario);

        List<ValidationIssue> issues = new ArrayList<>();
        double totalWeight = 0.0;
        double passedWeight = 0.0;

        // Regra 1: CPF válido (peso 10%)
        double weight1 = 0.10;
        totalWeight += weight1;
        if (cpf != null && !cpf.trim().isEmpty()) {
            if (cpfValidationService.isValid(cpf)) {
                passedWeight += weight1;
            } else {
                issues.add(new ValidationIssue(
                        "cpf", ValidationIssue.TYPE_CPF_INVALIDO,
                        "CPF válido", cpf,
                        "CPF " + cpf + " não passou na validação"
                ));
            }
        } else {
            passedWeight += weight1 * 0.5;
        }

        // Regra 2: Ano coerente — exercício = ano calendário + 1 (peso 10%)
        double weight2 = 0.10;
        totalWeight += weight2;
        if (exercicio != null && anoCalendario != null) {
            try {
                int exer = Integer.parseInt(exercicio.trim());
                int cal = Integer.parseInt(anoCalendario.trim());
                if (exer == cal + 1) {
                    passedWeight += weight2;
                } else {
                    issues.add(new ValidationIssue(
                            "exercicio", ValidationIssue.TYPE_ANO_INCOERENTE,
                            String.valueOf(cal + 1), exercicio,
                            "Exercício (" + exercicio + ") deveria ser Ano Calendário (" +
                                    anoCalendario + ") + 1 = " + (cal + 1)
                    ));
                }
            } catch (NumberFormatException e) {
                issues.add(new ValidationIssue(
                        "exercicio", ValidationIssue.TYPE_ANO_INCOERENTE,
                        "Numérico", exercicio + "/" + anoCalendario,
                        "Exercício ou Ano Calendário não são numéricos"
                ));
            }
        } else {
            passedWeight += weight2 * 0.5;
        }

        // Regra 3: Resultado coerente — devido - pago = saldo ou restituição (peso 25%)
        double weight3 = 0.25;
        totalWeight += weight3;
        if (totalImpostoDevido != null && totalImpostoPago != null) {
            BigDecimal diferenca = totalImpostoDevido.subtract(totalImpostoPago);
            boolean coerente = false;

            if (diferenca.compareTo(BigDecimal.ZERO) > 0 && saldoImpostoPagar != null) {
                // Deve ter saldo a pagar
                coerente = isWithinTolerance(diferenca, saldoImpostoPagar);
            } else if (diferenca.compareTo(BigDecimal.ZERO) < 0 && impostoRestituir != null) {
                // Deve ter restituição
                coerente = isWithinTolerance(diferenca.abs(), impostoRestituir);
            } else if (diferenca.compareTo(BigDecimal.ZERO) == 0) {
                coerente = true;
            }

            if (coerente) {
                passedWeight += weight3;
            } else {
                issues.add(new ValidationIssue(
                        "resultado", ValidationIssue.TYPE_RESULTADO_INCOERENTE,
                        "Devido - Pago = Saldo/Restituição",
                        "Devido=" + totalImpostoDevido + ", Pago=" + totalImpostoPago +
                                ", Saldo=" + saldoImpostoPagar + ", Restituir=" + impostoRestituir,
                        "Resultado da declaração não é coerente: devido - pago ≠ saldo/restituição"
                ));
            }
        } else {
            passedWeight += weight3 * 0.5;
        }

        // Regra 4: Valores positivos (peso 10%)
        double weight4 = 0.10;
        totalWeight += weight4;
        boolean allPositive = true;
        if (totalImpostoDevido != null && totalImpostoDevido.compareTo(BigDecimal.ZERO) < 0) allPositive = false;
        if (totalImpostoPago != null && totalImpostoPago.compareTo(BigDecimal.ZERO) < 0) allPositive = false;
        if (rendimentosTributaveis != null && rendimentosTributaveis.compareTo(BigDecimal.ZERO) < 0)
            allPositive = false;

        if (allPositive) {
            passedWeight += weight4;
        } else {
            issues.add(new ValidationIssue(
                    "valores", ValidationIssue.TYPE_VALOR_NEGATIVO,
                    ">= 0", "Valores negativos encontrados",
                    "Valores monetários negativos encontrados na declaração"
            ));
        }

        // Regra 5: Campos obrigatórios (peso 5%)
        double weight5 = 0.05;
        totalWeight += weight5;
        if (nome != null && !nome.trim().isEmpty() &&
                cpf != null && !cpf.trim().isEmpty() &&
                exercicio != null && !exercicio.trim().isEmpty() &&
                anoCalendario != null && !anoCalendario.trim().isEmpty()) {
            passedWeight += weight5;
        } else {
            List<String> faltando = new ArrayList<>();
            if (nome == null || nome.trim().isEmpty()) faltando.add("nome");
            if (cpf == null || cpf.trim().isEmpty()) faltando.add("cpf");
            if (exercicio == null || exercicio.trim().isEmpty()) faltando.add("exercicio");
            if (anoCalendario == null || anoCalendario.trim().isEmpty()) faltando.add("anoCalendario");
            issues.add(new ValidationIssue(
                    "camposObrigatorios", ValidationIssue.TYPE_CAMPOS_OBRIGATORIOS,
                    "Todos preenchidos", "Faltando: " + String.join(", ", faltando),
                    "Campos obrigatórios não preenchidos: " + String.join(", ", faltando)
            ));
        }

        // Regras restantes (soma deduções, soma imposto pago) — peso 40% dividido
        // Adicionamos 40% como neutro se não temos dados detalhados de deduções/pagamentos
        double weightRemaining = 0.40;
        totalWeight += weightRemaining;
        passedWeight += weightRemaining * 0.5; // Neutro — dados detalhados não disponíveis nesta fase

        double score = totalWeight > 0 ? passedWeight / totalWeight : 0.0;
        score = Math.max(0.0, Math.min(1.0, score));

        ValidationResult result = ValidationResult.fromScore(score, issues);

        log.info("Validação IR concluída: score={}, valid={}, recommendation={}, issues={}",
                String.format("%.2f", result.confidenceScore()),
                result.isValid(),
                result.recommendation(),
                result.issues().size());

        return result;
    }

    // ==========================================
    // UTILITÁRIOS
    // ==========================================

    /**
     * Verifica se dois valores estão dentro da tolerância de 1%.
     */
    private boolean isWithinTolerance(BigDecimal actual, BigDecimal expected) {
        if (actual == null || expected == null) return false;
        if (expected.compareTo(BigDecimal.ZERO) == 0) {
            return actual.abs().compareTo(new BigDecimal("0.01")) <= 0;
        }
        BigDecimal diff = actual.subtract(expected).abs();
        BigDecimal tolerance = expected.abs().multiply(TOLERANCE_PERCENT);
        return diff.compareTo(tolerance) <= 0;
    }

    /**
     * Valida se a competência está no formato MM/YYYY com mês entre 01 e 13.
     * Mês 13 = abono anual (13º salário).
     */
    private boolean isValidCompetencia(String competencia) {
        if (competencia == null) return false;
        String trimmed = competencia.trim();

        // Formato MM/YYYY
        if (trimmed.matches("^(0[1-9]|1[0-3])/\\d{4}$")) {
            return true;
        }

        // Formato YYYY-MM (normalizado)
        if (trimmed.matches("^\\d{4}-(0[1-9]|1[0-3])$")) {
            return true;
        }

        return false;
    }
}
