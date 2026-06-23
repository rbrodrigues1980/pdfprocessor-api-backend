package br.com.verticelabs.pdfprocessor.infrastructure.excel;

import lombok.Builder;
import lombok.Value;

import java.math.BigDecimal;

/**
 * Linhas da seção DEDUÇÕES (rótulos do RESUMO IRPF) para o bloco de simulação Completa + planilha.
 */
@Value
@Builder
public class ExcelIrpfDeducoesResumoDTO {

    BigDecimal prevOficialPublica;
    BigDecimal prevOficialRra;
    BigDecimal prevComplementarPrivadaEfetiva;
    BigDecimal prevComplementarPlanilhaBruta;
    BigDecimal dependentes;
    BigDecimal despesasInstrucao;
    BigDecimal despesasMedicas;
    BigDecimal pensaoJudicial;
    BigDecimal pensaoEscritura;
    BigDecimal pensaoJudicialRra;
    BigDecimal livroCaixa;
    BigDecimal totalDeducoes;
    BigDecimal inssDomesticoCredito;
    BigDecimal limite12PctRendimentos;
}
