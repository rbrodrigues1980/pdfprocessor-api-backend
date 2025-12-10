package br.com.verticelabs.pdfprocessor.domain.service;

import reactor.core.publisher.Mono;

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
        private Double baseCalculoImposto;
        private Double impostoDevido;
        private Double deducaoIncentivo;
        private Double impostoDevidoI;
        private Double contribuicaoPrevEmpregadorDomestico;
        private Double impostoDevidoII;
        private Double impostoDevidoRRA;
        private Double totalImpostoDevido;
        private Double saldoImpostoPagar;

        public IncomeTaxInfo(String nome, String cpf, String anoCalendario, String exercicio,
                Double baseCalculoImposto, Double impostoDevido, Double deducaoIncentivo,
                Double impostoDevidoI, Double contribuicaoPrevEmpregadorDomestico,
                Double impostoDevidoII, Double impostoDevidoRRA, Double totalImpostoDevido,
                Double saldoImpostoPagar) {
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

        public Double getBaseCalculoImposto() {
            return baseCalculoImposto;
        }

        public Double getImpostoDevido() {
            return impostoDevido;
        }

        public Double getDeducaoIncentivo() {
            return deducaoIncentivo;
        }

        public Double getImpostoDevidoI() {
            return impostoDevidoI;
        }

        public Double getContribuicaoPrevEmpregadorDomestico() {
            return contribuicaoPrevEmpregadorDomestico;
        }

        public Double getImpostoDevidoII() {
            return impostoDevidoII;
        }

        public Double getImpostoDevidoRRA() {
            return impostoDevidoRRA;
        }

        public Double getTotalImpostoDevido() {
            return totalImpostoDevido;
        }

        public Double getSaldoImpostoPagar() {
            return saldoImpostoPagar;
        }
    }
}
