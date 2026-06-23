package br.com.verticelabs.pdfprocessor.infrastructure.excel;

/**
 * Modo de simulação IRPF renderizado na planilha Excel.
 */
public enum ModoSimulacaoExcel {
    /** Espelho da declaração entregue (fluxo legado COMPLETO — fase 2). */
    ESPELHO_ENTREGUE,
    /** Simulação modelo Completo com prev. complementar da planilha de contracheques. */
    SIMULACAO_COMPLETA_PLANILHA,
    /** @deprecated use {@link #ESPELHO_ENTREGUE} */
    @Deprecated
    DECLARACAO,
    /** @deprecated use {@link #SIMULACAO_COMPLETA_PLANILHA} */
    @Deprecated
    CONTRACHEQUES_EXTRA
}
