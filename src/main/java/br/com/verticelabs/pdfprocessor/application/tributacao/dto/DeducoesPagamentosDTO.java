package br.com.verticelabs.pdfprocessor.application.tributacao.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Totais agregados a partir de {@code pagamentosEfetuados[]} por código oficial SERPRO.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DeducoesPagamentosDTO {

    private BigDecimal despesasMedicas;
    private BigDecimal despesasInstrucaoTitular;
    @Builder.Default
    private List<BigDecimal> despesasInstrucaoDependentes = new ArrayList<>();
    @Builder.Default
    private List<BigDecimal> despesasInstrucaoAlimentandos = new ArrayList<>();
    private BigDecimal pensaoAlimenticia;
    private BigDecimal previdenciaPrivada;
    private BigDecimal previdenciaEmpregadoDomestico;

    /** Indica se a agregação usou lista granular (true) ou fallback RESUMO (false). */
    private boolean fonteGranular;
}
