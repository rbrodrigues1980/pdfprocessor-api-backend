package br.com.verticelabs.pdfprocessor.domain.repository;

import br.com.verticelabs.pdfprocessor.domain.model.TaxaSelic;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;

/**
 * Repository para taxas SELIC (dados do Banco Central).
 */
public interface TaxaSelicRepository {

    /**
     * Salva ou atualiza uma taxa SELIC.
     */
    Mono<TaxaSelic> save(TaxaSelic taxaSelic);

    /**
     * Busca por número da reunião do COPOM.
     */
    Mono<TaxaSelic> findByNumeroReuniaoCopom(Integer numeroReuniao);

    /**
     * Busca a taxa vigente em uma data específica.
     */
    Mono<TaxaSelic> findVigenteEmData(Instant data);

    /**
     * Busca a taxa atualmente vigente (dataFimVigencia = null).
     */
    Mono<TaxaSelic> findVigenteAtual();

    /**
     * Lista todas as taxas ordenadas por data (mais recente primeiro).
     */
    Flux<TaxaSelic> findAllOrderByDataDesc();

    /**
     * Lista taxas de um período específico.
     */
    Flux<TaxaSelic> findByPeriodo(Instant inicio, Instant fim);

    /**
     * Conta total de registros.
     */
    Mono<Long> count();

    /**
     * Busca o maior número de reunião (para saber última sincronização).
     */
    Mono<Integer> findMaxNumeroReuniao();

    /**
     * Verifica se existe registro para uma reunião.
     */
    Mono<Boolean> existsByNumeroReuniao(Integer numeroReuniao);
}
