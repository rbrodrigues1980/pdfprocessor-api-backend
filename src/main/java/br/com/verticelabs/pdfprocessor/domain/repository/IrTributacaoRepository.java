package br.com.verticelabs.pdfprocessor.domain.repository;

import br.com.verticelabs.pdfprocessor.domain.model.IrTabelaTributacao;
import br.com.verticelabs.pdfprocessor.domain.model.IrParametrosAnuais;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Repository para tabelas de tributação IRPF.
 * Tabelas são globais (dados da Receita Federal).
 */
public interface IrTributacaoRepository {

    // ========== Faixas de Tributação ==========

    /**
     * Salva uma faixa de tributação.
     */
    Mono<IrTabelaTributacao> saveFaixa(IrTabelaTributacao faixa);

    /**
     * Busca todas as faixas de um ano e tipo.
     */
    Flux<IrTabelaTributacao> findFaixas(Integer anoCalendario, String tipoIncidencia);

    /**
     * Busca anos disponíveis para um tipo de incidência.
     */
    Flux<Integer> findAnosDisponiveis(String tipoIncidencia);

    /**
     * Remove todas as faixas de um ano e tipo.
     */
    Mono<Void> deleteFaixas(Integer anoCalendario, String tipoIncidencia);

    // ========== Parâmetros Anuais ==========

    /**
     * Salva parâmetros anuais.
     */
    Mono<IrParametrosAnuais> saveParametros(IrParametrosAnuais parametros);

    /**
     * Busca parâmetros de um ano e tipo.
     */
    Mono<IrParametrosAnuais> findParametros(Integer anoCalendario, String tipoIncidencia);

    /**
     * Remove parâmetros de um ano e tipo.
     */
    Mono<Void> deleteParametros(Integer anoCalendario, String tipoIncidencia);
}
