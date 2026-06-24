package br.com.verticelabs.pdfprocessor.interfaces.excel.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ResumoGeralTotaisResponse {
    private BigDecimal totalPrincipal;
    private BigDecimal totalCorrecao;
    private BigDecimal totalPrincipalMaisCorrecao;
    private BigDecimal honorarios;
    private BigDecimal valorReceber;
}
