package br.com.verticelabs.pdfprocessor.interfaces.dashboard.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DashboardMetric {
    private String titulo;
    private Long valor;
    private String descricao;
    private String trend; // Ex: "+12%", "-5%", etc.
    private Boolean trendPositive; // true se positivo, false se negativo
}

