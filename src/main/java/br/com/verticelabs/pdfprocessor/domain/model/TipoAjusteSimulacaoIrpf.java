package br.com.verticelabs.pdfprocessor.domain.model;

/**
 * Linha manual da simulação IRPF com aproveitamento das contribuições extras.
 */
public enum TipoAjusteSimulacaoIrpf {
    RENDIMENTOS_PJ_TITULAR(
            AreaAjusteSimulacaoIrpf.RENDIMENTOS_TRIBUTAVEIS,
            "Recebidos de Pessoa Jurídica pelo titular"),
    DEDUCAO_PREV_OFICIAL_PUBLICA(
            AreaAjusteSimulacaoIrpf.DEDUCOES,
            "Contribuição à previdência oficial e à previdência complementar pública (até o limite do patrocinador)"),
    DEDUCAO_PREV_OFICIAL_RRA(
            AreaAjusteSimulacaoIrpf.DEDUCOES,
            "Contribuição à previdência oficial (Rendimentos recebidos acumuladamente)"),
    DEDUCAO_PREV_COMPLEMENTAR(
            AreaAjusteSimulacaoIrpf.DEDUCOES,
            "Contribuição à previdência complementar (acima do limite do patrocinador) ou privada, a Fapi"),
    IMPOSTO_DEPOSITO_JUDICIAL_CIVEL(
            AreaAjusteSimulacaoIrpf.IMPOSTO_PAGO,
            "Imposto Recolhido Através de Depósito Judicial Processo Cível");

    private final AreaAjusteSimulacaoIrpf area;
    private final String rotulo;

    TipoAjusteSimulacaoIrpf(AreaAjusteSimulacaoIrpf area, String rotulo) {
        this.area = area;
        this.rotulo = rotulo;
    }

    public AreaAjusteSimulacaoIrpf getArea() {
        return area;
    }

    public String getRotulo() {
        return rotulo;
    }

    public static TipoAjusteSimulacaoIrpf from(String valor) {
        if (valor == null || valor.isBlank()) {
            throw new IllegalArgumentException("Tipo do ajuste é obrigatório");
        }
        try {
            return TipoAjusteSimulacaoIrpf.valueOf(valor.trim());
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("Tipo de ajuste inválido: " + valor);
        }
    }
}
