package br.com.verticelabs.pdfprocessor.domain.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Dados extraídos do Informe / Comprovante de Rendimentos Funcef.
 * Embutido em {@link PayrollDocument} quando o tipo for {@code INFORME_RENDIMENTOS}.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InformeRendimentosData {

    /** CNPJ da fonte pagadora (com máscara). */
    private String cnpjFontePagadora;

    /** Razão social da fonte pagadora. */
    private String razaoSocialFontePagadora;

    /** Ano-calendário (ex.: "2018"). */
    private String anoCalendario;

    /** CPF do beneficiário (normalizado ou mascarado conforme extraído). */
    private String cpfBeneficiario;

    /** Nome completo do beneficiário. */
    private String nomeBeneficiario;

    /** Natureza do rendimento, se presente. */
    private String naturezaRendimento;

    /** Texto bruto da seção 7 (auditoria). */
    private String informacoesComplementaresRaw;

    @Builder.Default
    private List<InformacaoComplementarJudiciaria> informacoesComplementares = new ArrayList<>();

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class InformacaoComplementarJudiciaria {
        private String numeroProcesso;
        /** Data no formato dd/MM/yyyy. */
        private String data;
        private String codigo;
        private String varaOuLocal;
        private BigDecimal contrExtr;
        private BigDecimal irrf;
        private BigDecimal contrExtr13;
        private BigDecimal irrf13;
    }
}
