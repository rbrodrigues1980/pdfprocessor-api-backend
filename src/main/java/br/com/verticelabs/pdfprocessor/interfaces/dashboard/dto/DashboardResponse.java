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
    private DashboardMetric rubricas;
    private DashboardMetric pessoas;
    private DashboardMetric totalDocumentos;
    private DashboardMetric lancamentos;
    private List<DashboardChartItem> graficoRubricas;
    private List<DashboardChartItem> graficoPessoas;
    private List<DashboardChartItem> graficoDocumentos;
    private List<DashboardChartItem> graficoLancamentos;
}
