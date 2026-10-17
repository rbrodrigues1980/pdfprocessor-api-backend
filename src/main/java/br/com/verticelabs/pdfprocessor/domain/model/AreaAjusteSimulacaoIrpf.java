package br.com.verticelabs.pdfprocessor.domain.model;

/**
 * Quadro da simulação IRPF em que o lançamento manual entra.
 */
public enum AreaAjusteSimulacaoIrpf {
    RENDIMENTOS_TRIBUTAVEIS("Rendimentos tributáveis"),
    DEDUCOES("Deduções"),
    IMPOSTO_PAGO("Imposto pago");

    private final String rotulo;

    AreaAjusteSimulacaoIrpf(String rotulo) {
        this.rotulo = rotulo;
    }

    public String getRotulo() {
        return rotulo;
    }
}
