package br.com.verticelabs.pdfprocessor.domain.service;

import br.com.verticelabs.pdfprocessor.domain.model.PayrollEntry;
import br.com.verticelabs.pdfprocessor.domain.model.ValidationResult;

import java.math.BigDecimal;
import java.util.List;

/**
 * Serviço de validação de dados extraídos de documentos.
 * Verifica consistência dos dados atribuindo um score de confiança (0.0 a 1.0).
 *
 * <h3>Regras de validação para Contracheques:</h3>
 * <ul>
 *   <li>Soma dos proventos = salário bruto (20%)</li>
 *   <li>Soma dos descontos = total descontos (20%)</li>
 *   <li>Bruto - descontos = líquido (25%)</li>
 *   <li>CPF válido (15%)</li>
 *   <li>Competência válida MM/YYYY (10%)</li>
 *   <li>Valores positivos (10%)</li>
 * </ul>
 *
 * <p>Custo: $0 — são apenas regras de negócio em código Java.</p>
 *
 * @see ValidationResult
 */
public interface ExtractionValidationService {

    /**
     * Valida dados extraídos de um contracheque.
     *
     * @param entries        lista de entries extraídas da página
     * @param salarioBruto   salário bruto informado no documento (pode ser null)
     * @param totalDescontos total de descontos informado no documento (pode ser null)
     * @param salarioLiquido salário líquido informado no documento (pode ser null)
     * @param cpf            CPF informado no documento (pode ser null)
     * @param competencia    competência no formato MM/YYYY (pode ser null)
     * @return ValidationResult com score de confiança e lista de inconsistências
     */
    ValidationResult validatePayrollExtraction(
            List<PayrollEntry> entries,
            BigDecimal salarioBruto,
            BigDecimal totalDescontos,
            BigDecimal salarioLiquido,
            String cpf,
            String competencia
    );

    /**
     * Valida dados extraídos de uma declaração de IR.
     *
     * @param nome                 nome do declarante
     * @param cpf                  CPF do declarante
     * @param exercicio            ano do exercício
     * @param anoCalendario        ano calendário
     * @param totalImpostoDevido   total do imposto devido
     * @param totalImpostoPago     total do imposto pago
     * @param saldoImpostoPagar    saldo de imposto a pagar
     * @param impostoRestituir     imposto a restituir
     * @param totalDeducoes        total de deduções
     * @param rendimentosTributaveis rendimentos tributáveis
     * @return ValidationResult com score de confiança e lista de inconsistências
     */
    ValidationResult validateIncomeTaxExtraction(
            String nome,
            String cpf,
            String exercicio,
            String anoCalendario,
            BigDecimal totalImpostoDevido,
            BigDecimal totalImpostoPago,
            BigDecimal saldoImpostoPagar,
            BigDecimal impostoRestituir,
            BigDecimal totalDeducoes,
            BigDecimal rendimentosTributaveis
    );
}
