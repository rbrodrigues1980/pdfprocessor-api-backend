package br.com.verticelabs.pdfprocessor.application.tributacao.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SimuladorIrpfResponse {

    private Integer anoCalendario;
    /** Eco do request — rendimentos RRA (tributação exclusiva, não entra na progressiva). */
    private BigDecimal rendimentosRRA;
    private String modeloRecomendado;
    private ModeloTributacaoResultDTO modeloCompleto;
    private ModeloTributacaoResultDTO modeloSimplificado;
    private ResumoDeclaracaoDTO resumoDeclaracao;
}
