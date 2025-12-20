package br.com.verticelabs.pdfprocessor.domain.service;

import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.io.InputStream;

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
    class IncomeTaxInfo {
        private String nome;
        private String cpf;
        private String anoCalendario;
        private String exercicio;
        private BigDecimal baseCalculoImposto;
        private BigDecimal impostoDevido;
        private BigDecimal deducaoIncentivo;
        private BigDecimal impostoDevidoI;
        private BigDecimal contribuicaoPrevEmpregadorDomestico;
        private BigDecimal impostoDevidoII;
        private BigDecimal impostoDevidoRRA;
        private BigDecimal totalImpostoDevido;
        private BigDecimal saldoImpostoPagar;
        private BigDecimal rendimentosTributaveis;
        private BigDecimal deducoes;
        private BigDecimal impostoRetidoFonteTitular;
        private BigDecimal impostoPagoTotal;
        private BigDecimal impostoRestituir;

        // Campos individuais de DEDUÇÕES
        private BigDecimal deducoesContribPrevOficial;
        private BigDecimal deducoesContribPrevRRA;
        private BigDecimal deducoesContribPrevCompl;
        private BigDecimal deducoesDependentes;
        private BigDecimal deducoesInstrucao;
        private BigDecimal deducoesMedicas;
        private BigDecimal deducoesPensaoJudicial;
        private BigDecimal deducoesPensaoEscritura;
        private BigDecimal deducoesPensaoRRA;
        private BigDecimal deducoesLivroCaixa;

        // Campos individuais de IMPOSTO PAGO
        private BigDecimal impostoRetidoFonteDependentes;
        private BigDecimal carneLeaoTitular;
        private BigDecimal carneLeaoDependentes;
        private BigDecimal impostoComplementar;
        private BigDecimal impostoPagoExterior;
        private BigDecimal impostoRetidoFonteLei11033;
        private BigDecimal impostoRetidoRRA;

        public IncomeTaxInfo(String nome, String cpf, String anoCalendario, String exercicio,
                BigDecimal baseCalculoImposto, BigDecimal impostoDevido,
                BigDecimal deducaoIncentivo,
                BigDecimal impostoDevidoI, BigDecimal contribuicaoPrevEmpregadorDomestico,
                BigDecimal impostoDevidoII, BigDecimal impostoDevidoRRA,
                BigDecimal totalImpostoDevido,
                BigDecimal saldoImpostoPagar,
                BigDecimal rendimentosTributaveis, BigDecimal deducoes,
                BigDecimal impostoRetidoFonteTitular, BigDecimal impostoPagoTotal,
                BigDecimal impostoRestituir,
                // Novos campos DEDUÇÕES
                BigDecimal deducoesContribPrevOficial, BigDecimal deducoesContribPrevRRA,
                BigDecimal deducoesContribPrevCompl, BigDecimal deducoesDependentes,
                BigDecimal deducoesInstrucao, BigDecimal deducoesMedicas,
                BigDecimal deducoesPensaoJudicial, BigDecimal deducoesPensaoEscritura,
                BigDecimal deducoesPensaoRRA, BigDecimal deducoesLivroCaixa,
                // Novos campos IMPOSTO PAGO
                BigDecimal impostoRetidoFonteDependentes, BigDecimal carneLeaoTitular,
                BigDecimal carneLeaoDependentes, BigDecimal impostoComplementar,
                BigDecimal impostoPagoExterior, BigDecimal impostoRetidoFonteLei11033,
                BigDecimal impostoRetidoRRA) {
            this.nome = nome;
            this.cpf = cpf;
            this.anoCalendario = anoCalendario;
            this.exercicio = exercicio;
            this.baseCalculoImposto = baseCalculoImposto;
            this.impostoDevido = impostoDevido;
            this.deducaoIncentivo = deducaoIncentivo;
            this.impostoDevidoI = impostoDevidoI;
            this.contribuicaoPrevEmpregadorDomestico = contribuicaoPrevEmpregadorDomestico;
            this.impostoDevidoII = impostoDevidoII;
            this.impostoDevidoRRA = impostoDevidoRRA;
            this.totalImpostoDevido = totalImpostoDevido;
            this.saldoImpostoPagar = saldoImpostoPagar;
            this.rendimentosTributaveis = rendimentosTributaveis;
            this.deducoes = deducoes;
            this.impostoRetidoFonteTitular = impostoRetidoFonteTitular;
            this.impostoPagoTotal = impostoPagoTotal;
            this.impostoRestituir = impostoRestituir;
            // Campos DEDUÇÕES
            this.deducoesContribPrevOficial = deducoesContribPrevOficial;
            this.deducoesContribPrevRRA = deducoesContribPrevRRA;
            this.deducoesContribPrevCompl = deducoesContribPrevCompl;
            this.deducoesDependentes = deducoesDependentes;
            this.deducoesInstrucao = deducoesInstrucao;
            this.deducoesMedicas = deducoesMedicas;
            this.deducoesPensaoJudicial = deducoesPensaoJudicial;
            this.deducoesPensaoEscritura = deducoesPensaoEscritura;
            this.deducoesPensaoRRA = deducoesPensaoRRA;
            this.deducoesLivroCaixa = deducoesLivroCaixa;
            // Campos IMPOSTO PAGO
            this.impostoRetidoFonteDependentes = impostoRetidoFonteDependentes;
            this.carneLeaoTitular = carneLeaoTitular;
            this.carneLeaoDependentes = carneLeaoDependentes;
            this.impostoComplementar = impostoComplementar;
            this.impostoPagoExterior = impostoPagoExterior;
            this.impostoRetidoFonteLei11033 = impostoRetidoFonteLei11033;
            this.impostoRetidoRRA = impostoRetidoRRA;
        }

        public String getNome() {
            return nome;
        }

        public String getCpf() {
            return cpf;
        }

        public String getAnoCalendario() {
            return anoCalendario;
        }

        public String getExercicio() {
            return exercicio;
        }

        public BigDecimal getBaseCalculoImposto() {
            return baseCalculoImposto;
        }

        public BigDecimal getImpostoDevido() {
            return impostoDevido;
        }

        public BigDecimal getDeducaoIncentivo() {
            return deducaoIncentivo;
        }

        public BigDecimal getImpostoDevidoI() {
            return impostoDevidoI;
        }

        public BigDecimal getContribuicaoPrevEmpregadorDomestico() {
            return contribuicaoPrevEmpregadorDomestico;
        }

        public BigDecimal getImpostoDevidoII() {
            return impostoDevidoII;
        }

        public BigDecimal getImpostoDevidoRRA() {
            return impostoDevidoRRA;
        }

        public BigDecimal getTotalImpostoDevido() {
            return totalImpostoDevido;
        }

        public BigDecimal getSaldoImpostoPagar() {
            return saldoImpostoPagar;
        }

        public BigDecimal getRendimentosTributaveis() {
            return rendimentosTributaveis;
        }

        public BigDecimal getDeducoes() {
            return deducoes;
        }

        public BigDecimal getImpostoRetidoFonteTitular() {
            return impostoRetidoFonteTitular;
        }

        public BigDecimal getImpostoPagoTotal() {
            return impostoPagoTotal;
        }

        public BigDecimal getImpostoRestituir() {
            return impostoRestituir;
        }

        // Getters para campos DEDUÇÕES
        public BigDecimal getDeducoesContribPrevOficial() {
            return deducoesContribPrevOficial;
        }

        public BigDecimal getDeducoesContribPrevRRA() {
            return deducoesContribPrevRRA;
        }

        public BigDecimal getDeducoesContribPrevCompl() {
            return deducoesContribPrevCompl;
        }

        public BigDecimal getDeducoesDependentes() {
            return deducoesDependentes;
        }

        public BigDecimal getDeducoesInstrucao() {
            return deducoesInstrucao;
        }

        public BigDecimal getDeducoesMedicas() {
            return deducoesMedicas;
        }

        public BigDecimal getDeducoesPensaoJudicial() {
            return deducoesPensaoJudicial;
        }

        public BigDecimal getDeducoesPensaoEscritura() {
            return deducoesPensaoEscritura;
        }

        public BigDecimal getDeducoesPensaoRRA() {
            return deducoesPensaoRRA;
        }

        public BigDecimal getDeducoesLivroCaixa() {
            return deducoesLivroCaixa;
        }

        // Getters para campos IMPOSTO PAGO
        public BigDecimal getImpostoRetidoFonteDependentes() {
            return impostoRetidoFonteDependentes;
        }

        public BigDecimal getCarneLeaoTitular() {
            return carneLeaoTitular;
        }

        public BigDecimal getCarneLeaoDependentes() {
            return carneLeaoDependentes;
        }

        public BigDecimal getImpostoComplementar() {
            return impostoComplementar;
        }

        public BigDecimal getImpostoPagoExterior() {
            return impostoPagoExterior;
        }

        public BigDecimal getImpostoRetidoFonteLei11033() {
            return impostoRetidoFonteLei11033;
        }

        public BigDecimal getImpostoRetidoRRA() {
            return impostoRetidoRRA;
        }
    }
}
