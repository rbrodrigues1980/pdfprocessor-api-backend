package br.com.verticelabs.pdfprocessor.interfaces.dashboard.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TotalPorAno {
    private Integer ano;
    private Double valorTotal; // Total em reais
}

