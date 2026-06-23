package br.com.verticelabs.pdfprocessor.interfaces.repasse.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RepasseMonthlyChartItem {
    private String mes;
    private Long validacoes;
    private Long pagos;
}
