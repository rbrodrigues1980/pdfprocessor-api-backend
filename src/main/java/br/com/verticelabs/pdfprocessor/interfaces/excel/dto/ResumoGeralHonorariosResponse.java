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
public class ResumoGeralHonorariosResponse {
    private String label;
    private BigDecimal percentualExibicao;
    private String empresaSigla;
}
