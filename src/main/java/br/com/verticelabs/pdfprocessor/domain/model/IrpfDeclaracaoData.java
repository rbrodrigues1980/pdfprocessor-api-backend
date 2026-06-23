package br.com.verticelabs.pdfprocessor.domain.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

/**
 * Dados extraídos de uma declaração de Imposto de Renda (IRPF).
 * Embutido em {@link PayrollDocument} quando o tipo for {@code INCOME_TAX}.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IrpfDeclaracaoData {

    // ==========================================
    // IDENTIFICAÇÃO
    // ==========================================

    /** Nome completo do declarante. */
    private String nomeTitular;

    /** CPF do declarante (normalizado, sem pontuação). */
    private String cpfTitular;

    /** Ano de exercício (ex: "2017"). */
    private String exercicio;

    /** Ano-calendário (ex: "2016"). */
    private String anoCalendario;

    /** Número de controle gerado pela Receita Federal. */
    private String controle;

    /** Data e hora da entrega (ex: "25/04/2017 às 22:41:36"). */
    private String dataHoraEntrega;

    /** Tipo de declaração: "Original" ou "Retificadora". */
    private String tipoDeclaracao;

    /** Tipo de tributação: "SIMPLIFICADO" ou "COMPLETO". */
    private String tipoTributacao;

    // ==========================================
    // DEPENDENTES
    // ==========================================

    /** Lista de dependentes declarados. */
    private List<PessoaRelacionada> dependentes;

    /** Total de dedução com dependentes. */
    private BigDecimal totalDeducaoDependentes;

    // ==========================================
    // ALIMENTANDOS
    // ==========================================

    /** Lista de alimentandos declarados. */
    private List<PessoaRelacionada> alimentandos;

    // ==========================================
    // RENDIMENTOS POR FONTE PAGADORA (TITULAR — PJ)
    // ==========================================

    /** Fontes pagadoras do titular (rendimentos tributáveis de PJ). */
    private List<FontePagadoraIrpf> rendimentosFontesTitular;

    // ==========================================
    // DESCONTO SIMPLIFICADO
    // ==========================================

    private BigDecimal descontoSimplificado;

    // ==========================================
    // DEDUÇÕES INDIVIDUAIS
    // ==========================================

    /** Linha RESUMO — prev. oficial até limite do patrocinador (sem RRA). */
    private BigDecimal contribuicaoPrevidenciaOficialResumo;
    /** Linha RESUMO — prev. oficial sobre rendimentos acumulados (RRA). */
    private BigDecimal contribuicaoPrevidenciaOficialRra;
    /** Prev. oficial para dedução (fontes PJ ou resumo, sem RRA). */
    private BigDecimal contribuicaoPrevidenciaSocial;
    private BigDecimal contribuicaoPrevidenciaPrivada;
    /** Valor total da dedução com dependentes (diferente da lista de dependentes acima). */
    private BigDecimal deducaoDependentes;
    private BigDecimal despesasInstrucao;
    private BigDecimal despesasMedicas;
    private BigDecimal pensaoAlimenticiaJudicial;
    private BigDecimal pensaoAlimenticiaJudicialRra;
    private BigDecimal pensaoAlimenticiaEscrituraPublica;
    private BigDecimal livroCaixa;
    private BigDecimal contribuicaoPatronalPrevidenciaSocial;

    // ==========================================
    // RESUMO FINANCEIRO
    // ==========================================

    private BigDecimal rendimentosTributaveisTotal;
    private BigDecimal deducoesTotal;
    private BigDecimal baseCalculoImposto;
    private BigDecimal impostoDevido;
    private BigDecimal deducaoIncentivo;
    private BigDecimal impostoDevidoI;
    private BigDecimal impostoDevidoII;
    private BigDecimal impostoSobreRRA;
    private BigDecimal totalImpostoDevido;

    // ==========================================
    // IMPOSTO PAGO INDIVIDUAL
    // ==========================================

    private BigDecimal impostoRetidoFonteTitular;
    private BigDecimal impostoRetidoDependentes;
    private BigDecimal carneLeaoTitular;
    private BigDecimal carneLeaoDependentes;
    private BigDecimal impostoComplementar;
    private BigDecimal impostoPagoExterior;
    private BigDecimal impostoRetidoFonteLei11033;
    private BigDecimal impostoRetidoRRA;

    private BigDecimal impostoPagoTotal;
    private BigDecimal impostoRestituir;
    private BigDecimal saldoImpostoPagar;
    private BigDecimal aliquotaEfetiva;
    private BigDecimal rendimentosIsentos;
    private BigDecimal rendimentosTributacaoExclusiva;

    // ==========================================
    // PAGAMENTOS EFETUADOS
    // ==========================================

    /** Lista de pagamentos efetuados (seção PAGAMENTOS EFETUADOS do IR). */
    private List<PagamentoEfetuadoIrpf> pagamentosEfetuados;

    // ==========================================
    // DOAÇÕES EFETUADAS
    // ==========================================

    /** Lista de doações efetuadas (seção DOAÇÕES EFETUADAS do IR). */
    private List<DoacaoEfetuadaIrpf> doacoesEfetuadas;

    // ==========================================
    // LINHAS INDIVIDUAIS DE RENDIMENTOS TRIBUTÁVEIS (RESUMO)
    // ==========================================

    private BigDecimal rendimentosTributaveisTitularPJ;
    private BigDecimal rendimentosTributaveisDependentesPJ;
    private BigDecimal rendimentosTributaveisTitularPF;
    private BigDecimal rendimentosTributaveisDependentesPF;
    private BigDecimal resultadoAtividadeRural;
    private BigDecimal rendimentosAcumuladosTitular;
    private BigDecimal rendimentosAcumuladosDependentes;

    // ==========================================
    // OUTRAS INFORMAÇÕES (RESUMO)
    // ==========================================

    private BigDecimal impostoPagoGanhosCapital;
    private BigDecimal impostoDevidoGanhosCapital;
    private BigDecimal impostoDevidoGanhosCapitalMoedaEstrangeira;
    private BigDecimal impostoPagoGanhosCapitalMoedaEstrangeira;
    private BigDecimal impostoPagoRendaVariavel;
    private BigDecimal impostoDevidoGanhosLiquidosRendaVariavel;
    private BigDecimal impostoAPagarGanhosCapitalMoedaEstrangeira;
    private BigDecimal rendimentosTributaveisExigSuspensa;
    private BigDecimal depositosJudiciais;
    private BigDecimal impostoDiferidoGanhosCapital;
    private BigDecimal doacoesPartidosPoliticos;

    // ==========================================
    // INNER CLASSES
    // ==========================================

    /**
     * Representa um pagamento efetuado declarado no IRPF.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PagamentoEfetuadoIrpf {
        private String codigo;
        private String nomeBeneficiario;
        private String cpfCnpj;
        private BigDecimal valorPago;
        private BigDecimal parcNaoDedutivel;
        private String nitEmpregadoDomestico;
    }

    /**
     * Representa uma doação efetuada declarada no IRPF.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DoacaoEfetuadaIrpf {
        private String codigo;
        private String nomeBeneficiario;
        private String cpfCnpj;
        private BigDecimal valorDoado;
    }

    /**
     * Representa um dependente ou alimentando declarado no IRPF.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PessoaRelacionada {
        /** Código do tipo de vínculo (ex: 21 = filho(a), 31 = pai/mãe). */
        private String codigo;
        /** Nome completo. */
        private String nome;
        /** Data de nascimento (DD/MM/YYYY). */
        private String dataNascimento;
        /** CPF. */
        private String cpf;
    }

    /**
     * Representa uma fonte pagadora de rendimentos tributáveis do titular.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FontePagadoraIrpf {
        /** Nome da empresa/entidade. */
        private String nome;
        /** CNPJ ou CPF da fonte pagadora. */
        private String cnpjCpf;
        /** Rendimentos tributáveis recebidos de PJ. */
        private BigDecimal rendRecebidosPJ;
        /** Contribuição à previdência oficial (INSS/FUNCEF). */
        private BigDecimal contrPrevOficial;
        /** Imposto de renda retido na fonte. */
        private BigDecimal impostoRetidoFonte;
        /** 13º Salário. */
        private BigDecimal decimoTerceiro;
        /** IRRF sobre 13º Salário. */
        private BigDecimal irrfDecimoTerceiro;
    }
}
