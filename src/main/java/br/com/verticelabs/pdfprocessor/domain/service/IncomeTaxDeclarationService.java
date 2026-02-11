package br.com.verticelabs.pdfprocessor.domain.service;

import lombok.AllArgsConstructor;
import lombok.Getter;
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
    }
}
