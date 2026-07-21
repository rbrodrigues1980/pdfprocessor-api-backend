package br.com.verticelabs.pdfprocessor.application.excel;

import java.math.BigDecimal;

/**
 * Linha do relatório Excel de clientes.
 */
public record ClienteExcelReportRow(
        String nome,
        String cpf,
        String entidade,
        String status,
        String observacoes,
        BigDecimal percentualHonorarios,
        BigDecimal totalPrincipalPgfn,
        BigDecimal totalPrincipalMaisCorrecao
) {
}
