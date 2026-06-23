package br.com.verticelabs.pdfprocessor.interfaces.repasse.dto;

import br.com.verticelabs.pdfprocessor.interfaces.dashboard.dto.DashboardChartItem;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DeveloperRepasseSummaryResponse {
    private long totalClientes;
    private long totalValidados;
    private long totalNaoValidados;
    private long totalPendentes;
    private long totalPagos;
    private BigDecimal valorPendente;
    private BigDecimal valorPago;
    private BigDecimal valorUnitario;
    private int anoBase;
    private int anoAtual;
    private Instant vigenciaDe;
    private List<DashboardChartItem> graficoBase;
    private List<DashboardChartItem> graficoRepasse;
    private List<RepasseMonthlyChartItem> graficoPorMes;
}
