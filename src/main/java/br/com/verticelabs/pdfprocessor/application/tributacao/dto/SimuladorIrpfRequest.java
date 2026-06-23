package br.com.verticelabs.pdfprocessor.application.tributacao.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SimuladorIrpfRequest {

    private Integer anoCalendario;
    private String tipoIncidencia;

    /** Rendimentos sujeitos à tabela progressiva anual. Exclui RRA (tributação exclusiva). */
    private BigDecimal rendimentosTributaveis;

    /** Rendimentos de RRA (ficha própria). Isolado da base progressiva e da redução anual 2026. */
    private BigDecimal rendimentosRRA;

    // Deduções legais (modelo completo)
    private BigDecimal despesasMedicas;
    /** Gasto com instrução do titular (limite individual por CPF). */
    private BigDecimal despesasInstrucaoTitular;
    /** Gasto com instrução por dependente, na ordem (limite individual por CPF). */
    private List<BigDecimal> despesasInstrucaoDependentes;
    /** Gasto com instrução por alimentando, na ordem (limite individual por CPF). */
    private List<BigDecimal> despesasInstrucaoAlimentandos;
    private Integer qtdDependentes;
    private Integer qtdAlimentandos;
    /**
     * Totais efetivos da declaração importada (Excel). Quando preenchidos, substituem
     * o recálculo por qtd × valor dependente ou cap individual de educação.
     */
    private BigDecimal deducaoDependentesDeclarada;
    private BigDecimal despesasInstrucaoDeclarada;
    private BigDecimal previdenciaOficial;
    private BigDecimal previdenciaPrivada;
    private BigDecimal pensaoAlimenticia;
    /** INSS patronal recolhido por empregador doméstico (dedução histórica AC ≤ 2018). */
    private BigDecimal previdenciaEmpregadoDomestico;

    /**
     * Quando true, INSS doméstico não entra nas deduções da base; aplica-se como crédito
     * (Imposto devido II = Imposto devido I − INSS doméstico), conforme RESUMO IRPF.
     */
    private Boolean inssDomesticoComoCreditoImposto;

    // Deduções especiais (item 5)
    private BigDecimal deducaoIncentivo;
    private BigDecimal dedPronas;
    private BigDecimal dedPronon;

    /** Imposto devido sobre RRA. Soma apenas no total do item 7 (não entra na progressiva). */
    private BigDecimal impostoDevidoRRA;

    // Imposto pago (item 8)
    private BigDecimal impostoRetidoFonteTitular;
    private BigDecimal impostoRetidoFonteDependentes;
    private BigDecimal carneLeaoTitular;
    private BigDecimal carneLeaoDependentes;
    private BigDecimal impostoComplementar;
    private BigDecimal impostoPagoExterior;
    private BigDecimal impostoRetidoFonteLei11033;
    private BigDecimal impostoRetidoRRA;
}
