package br.com.verticelabs.pdfprocessor.interfaces.dashboard.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DashboardResponse {
    private DashboardMetric totalDocumentos;
    private DashboardMetric lancamentos;
    private DashboardMetric pessoas;
    private DashboardMetric rubricas;
    private List<TotalPorAno> totalPorAno; // Total em reais agrupado por ano
    private List<TotalPorMes> totalPorMes; // Total em reais agrupado por mês
}

