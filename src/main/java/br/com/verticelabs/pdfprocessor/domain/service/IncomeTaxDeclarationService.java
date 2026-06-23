package br.com.verticelabs.pdfprocessor.domain.service;

import lombok.AllArgsConstructor;
import lombok.Getter;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.io.InputStream;
import java.util.List;

/**
 * Serviço para extrair informações de declaração de imposto de renda.
 */
public interface IncomeTaxDeclarationService {

    /**
     * Extrai informações da página RESUMO da declaração de IR.
     * 
     * @param inputStream Stream do PDF
     * @return Mono com objeto contendo Ano-Calendário e Imposto Devido
     */
    Mono<IncomeTaxInfo> extractIncomeTaxInfo(InputStream inputStream);

    /**
     * Classe para armazenar informações extraídas da declaração de IR.
     */
    @Getter
    @AllArgsConstructor
    class IncomeTaxInfo {
        // Dados Básicos
        private final String nome;
        private final String cpf;
        private final String anoCalendario;
        private final String exercicio;

        // IMPOSTO DEVIDO
        private final BigDecimal baseCalculoImposto;
        private final BigDecimal impostoDevido;
        private final BigDecimal deducaoIncentivo;
        private final BigDecimal impostoDevidoI;
        private final BigDecimal contribuicaoPrevEmpregadorDomestico;
        private final BigDecimal impostoDevidoII;
        private final BigDecimal impostoDevidoRRA;
        private final BigDecimal totalImpostoDevido;
        private final BigDecimal saldoImpostoPagar;

        // Rendimentos e Deduções Gerais
        private final BigDecimal rendimentosTributaveis;
        private final BigDecimal deducoes;
        private final BigDecimal impostoRetidoFonteTitular;
        private final BigDecimal impostoPagoTotal;
        private final BigDecimal impostoRestituir;

        // Campos individuais de DEDUÇÕES
        private final BigDecimal deducoesContribPrevOficial;
        private final BigDecimal deducoesContribPrevRRA;
        private final BigDecimal deducoesContribPrevCompl;
        private final BigDecimal deducoesDependentes;
        private final BigDecimal deducoesInstrucao;
        private final BigDecimal deducoesMedicas;
        private final BigDecimal deducoesPensaoJudicial;
        private final BigDecimal deducoesPensaoEscritura;
        private final BigDecimal deducoesPensaoRRA;
        private final BigDecimal deducoesLivroCaixa;

        // Campos individuais de IMPOSTO PAGO
        private final BigDecimal impostoRetidoFonteDependentes;
        private final BigDecimal carneLeaoTitular;
        private final BigDecimal carneLeaoDependentes;
        private final BigDecimal impostoComplementar;
        private final BigDecimal impostoPagoExterior;
        private final BigDecimal impostoRetidoFonteLei11033;
        private final BigDecimal impostoRetidoRRA;

        // Campos exclusivos de 2017+ (Desconto Simplificado)
        private final BigDecimal descontoSimplificado;
        private final BigDecimal aliquotaEfetiva;

        // Identificação adicional (página 1)
        private final String tipoTributacao;      // "SIMPLIFICADO" ou "COMPLETO"
        private final String dataNascimento;
        private final String tituloEleitoral;
        private final String tipoDeclaracao;      // "Original" ou "Retificadora"
        private final String dataEntrega;

        // Evolução patrimonial (página RESUMO)
        private final BigDecimal bensAnterior;    // Bens e direitos 31/12 ano anterior
        private final BigDecimal bensAtual;       // Bens e direitos 31/12 ano atual
        private final BigDecimal dividasAnterior; // Dívidas 31/12 ano anterior
        private final BigDecimal dividasAtual;    // Dívidas 31/12 ano atual

        // Outras informações do RESUMO
        private final BigDecimal rendimentosIsentos;
        private final BigDecimal rendimentosTributacaoExclusiva;

        // Pagamentos efetuados
        private final List<PagamentoEfetuado> pagamentosEfetuados;

        // Fontes pagadoras — extraídas das páginas "RENDIMENTOS TRIBUTÁVEIS
        // RECEBIDOS DE PESSOA JURÍDICA PELO TITULAR"
        private final List<FontePagadora> fontesPagadoras;

        /**
         * Representa um pagamento efetuado declarado no IR.
         */
        @Getter
        @AllArgsConstructor
        public static class PagamentoEfetuado {
            private final String codigo;
            private final String nomeBeneficiario;
            private final String cpfCnpj;
            private final BigDecimal valorPago;
            /** Parcela não dedutível (coluna "PARC. NÃO DEDUTÍVEL" do PDF). */
            private final BigDecimal parcNaoDedutivel;
            /** NIT do empregado doméstico (coluna "NIT EMPREGADO DOMÉSTICO" do PDF, pode ser null). */
            private final String nitEmpregadoDomestico;
        }

        /**
         * Representa uma doação efetuada declarada no IR.
         */
        @Getter
        @AllArgsConstructor
        public static class DoacaoEfetuada {
            private final String codigo;
            private final String nomeBeneficiario;
            private final String cpfCnpj;
            private final BigDecimal valorDoado;
        }

        /**
         * Representa uma fonte pagadora de rendimentos tributáveis recebidos de
         * pessoa jurídica pelo titular, conforme página de detalhe do IRPF.
         */
        @Getter
        @AllArgsConstructor
        public static class FontePagadora {
            /** Nome completo da empresa/entidade pagadora. */
            private final String nome;
            /** CNPJ ou CPF da fonte pagadora (formato original do PDF). */
            private final String cnpjCpf;
            /** Rendimento tributável total declarado pela fonte. */
            private final BigDecimal rendimentoTributavel;
            /** Contribuição à previdência oficial descontada na fonte (ex: INSS/FUNCEF). */
            private final BigDecimal contribuicaoPrevOficial;
            /** Imposto de renda retido na fonte pela pagadora. */
            private final BigDecimal impostoRetidoFonte;
            /** 13º Salário. */
            private final BigDecimal decimoTerceiro;
            /** IRRF sobre 13º Salário. */
            private final BigDecimal irrfDecimoTerceiro;
        }

        /**
         * Representa um dependente declarado no IRPF.
         */
        @Getter
        @AllArgsConstructor
        public static class DependenteInfo {
            /** Código do tipo de dependente (ex: 21 = filho(a), 31 = pai/mãe). */
            private final String codigo;
            /** Nome completo do dependente. */
            private final String nome;
            /** Data de nascimento no formato DD/MM/YYYY. */
            private final String dataNascimento;
            /** CPF do dependente. */
            private final String cpf;
        }

        // Identificação adicional — página 1
        // (campos já declarados acima: tipoTributacao, dataNascimento, tituloEleitoral,
        //  tipoDeclaracao, dataEntrega)

        // Número de controle da entrega (código gerado pela Receita Federal)
        private final String controle;

        // Dependentes
        private final List<DependenteInfo> dependentes;
        private final BigDecimal totalDeducaoDependentes;

        // Alimentandos (estrutura igual à de dependentes)
        private final List<DependenteInfo> alimentandos;

        // ==========================================
        // LINHAS INDIVIDUAIS DE RENDIMENTOS TRIBUTÁVEIS (RESUMO)
        // ==========================================

        /** Recebidos de Pessoa Jurídica pelo titular (linha individual do RESUMO). */
        private final BigDecimal rendimentosTributaveisTitularPJ;
        /** Recebidos de Pessoa Jurídica pelos dependentes. */
        private final BigDecimal rendimentosTributaveisDependentesPJ;
        /** Recebidos de Pessoa Física/Exterior pelo titular. */
        private final BigDecimal rendimentosTributaveisTitularPF;
        /** Recebidos de Pessoa Física/Exterior pelos dependentes. */
        private final BigDecimal rendimentosTributaveisDependentesPF;
        /** Resultado tributável da Atividade Rural. */
        private final BigDecimal resultadoAtividadeRural;
        /** Recebidos acumuladamente pelo titular (RRA). */
        private final BigDecimal rendimentosAcumuladosTitular;
        /** Recebidos acumuladamente pelos dependentes (RRA). */
        private final BigDecimal rendimentosAcumuladosDependentes;

        // ==========================================
        // OUTRAS INFORMAÇÕES (RESUMO)
        // ==========================================

        /** Imposto pago sobre Ganhos de Capital. */
        private final BigDecimal impostoPagoGanhosCapital;
        /** Imposto devido sobre Ganhos de Capital. */
        private final BigDecimal impostoDevidoGanhosCapital;
        /** Imposto devido sobre Ganhos de Capital – Moeda Estrangeira. */
        private final BigDecimal impostoDevidoGanhosCapitalMoedaEstrangeira;
        /** Imposto pago Ganhos de Capital – Moeda Estrangeira. */
        private final BigDecimal impostoPagoGanhosCapitalMoedaEstrangeira;
        /** Imposto pago sobre Renda Variável. */
        private final BigDecimal impostoPagoRendaVariavel;
        /** Imposto devido sobre ganhos líquidos em Renda Variável. */
        private final BigDecimal impostoDevidoGanhosLiquidosRendaVariavel;
        /** Imposto a pagar sobre o Ganho de Capital – Moeda Estrangeira em Espécie. */
        private final BigDecimal impostoAPagarGanhosCapitalMoedaEstrangeira;
        /** Rendimentos tributáveis – imposto com exigibilidade suspensa. */
        private final BigDecimal rendimentosTributaveisExigSuspensa;
        /** Depósitos judiciais do imposto. */
        private final BigDecimal depositosJudiciais;
        /** Imposto diferido dos Ganhos de Capital. */
        private final BigDecimal impostoDiferidoGanhosCapital;
        /** Doações a Partidos Políticos e Candidatos a Cargos Eletivos. */
        private final BigDecimal doacoesPartidosPoliticos;

        // ==========================================
        // DOAÇÕES EFETUADAS
        // ==========================================

        /** Lista de doações efetuadas (seção DOAÇÕES EFETUADAS do IR). */
        private final List<DoacaoEfetuada> doacoesEfetuadas;
    }
}
