package br.com.verticelabs.pdfprocessor.interfaces.dashboard.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TotalPorMes {
    private String mesAno; // Formato "2017-08"
    private Double valorTotal; // Total em reais
}

