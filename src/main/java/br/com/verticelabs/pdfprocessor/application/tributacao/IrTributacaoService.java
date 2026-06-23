package br.com.verticelabs.pdfprocessor.application.tributacao;

import br.com.verticelabs.pdfprocessor.domain.model.IrParametrosAnuais;
import br.com.verticelabs.pdfprocessor.domain.model.IrTabelaTributacao;
import br.com.verticelabs.pdfprocessor.domain.repository.IrTributacaoRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.util.List;

/**
 * Serviço para gestão e cálculo de tributação IRPF.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class IrTributacaoService {

    private final IrTributacaoRepository repository;
    private final IrCalculoProgressivoService calculoProgressivoService;

    // ========== Consultas ==========

    /**
     * Busca todos os anos disponíveis para um tipo de incidência.
     */
    public Flux<Integer> buscarAnosDisponiveis(String tipoIncidencia) {
        return repository.findAnosDisponiveis(tipoIncidencia);
    }

    /**
     * Busca as faixas de tributação de um ano e tipo.
     */
    public Flux<IrTabelaTributacao> buscarFaixas(Integer ano, String tipoIncidencia) {
        return repository.findFaixas(ano, tipoIncidencia);
    }

    /**
     * Busca os parâmetros de um ano e tipo.
     */
    public Mono<IrParametrosAnuais> buscarParametros(Integer ano, String tipoIncidencia) {
        return repository.findParametros(ano, tipoIncidencia);
    }

    // ========== CRUD ==========

    /**
     * Salva uma faixa de tributação.
     */
    public Mono<IrTabelaTributacao> salvarFaixa(IrTabelaTributacao faixa) {
        return repository.saveFaixa(faixa);
    }

    /**
     * Salva várias faixas de tributação.
     */
    public Flux<IrTabelaTributacao> salvarFaixas(List<IrTabelaTributacao> faixas) {
        return Flux.fromIterable(faixas)
                .flatMap(repository::saveFaixa);
    }

    /**
     * Salva parâmetros anuais.
     */
    public Mono<IrParametrosAnuais> salvarParametros(IrParametrosAnuais parametros) {
        return repository.saveParametros(parametros);
    }

    /**
     * Remove todas as faixas e parâmetros de um ano e tipo.
     */
    public Mono<Void> removerAno(Integer ano, String tipoIncidencia) {
        return repository.deleteFaixas(ano, tipoIncidencia)
                .then(repository.deleteParametros(ano, tipoIncidencia));
    }

    /**
     * Remove apenas as faixas de um ano e tipo (preserva parâmetros).
     */
    public Mono<Void> deleteFaixasOnly(Integer ano, String tipoIncidencia) {
        return repository.deleteFaixas(ano, tipoIncidencia);
    }

    // ========== Cálculo de Imposto ==========

    /**
     * Calcula o imposto devido com base na tabela progressiva.
     * 
     * @param baseCalculo    Base de cálculo do imposto
     * @param ano            Ano-calendário
     * @param tipoIncidencia Tipo de incidência (ANUAL, MENSAL, PLR)
     * @return Mono com o valor do imposto calculado
     */
    public Mono<BigDecimal> calcularImposto(BigDecimal baseCalculo, Integer ano, String tipoIncidencia) {
        log.debug("Calculando imposto: base={}, ano={}, tipo={}", baseCalculo, ano, tipoIncidencia);

        if (baseCalculo == null || baseCalculo.compareTo(BigDecimal.ZERO) <= 0) {
            return Mono.just(BigDecimal.ZERO);
        }

        return buscarFaixas(ano, tipoIncidencia)
                .collectList()
                .flatMap(faixas -> buscarParametros(ano, tipoIncidencia)
                        .defaultIfEmpty(new IrParametrosAnuais())
                        .map(params -> {
                            var resultado = calculoProgressivoService.calcular(
                                    faixas, baseCalculo, BigDecimal.ZERO, BigDecimal.ZERO, params);
                            return resultado.getImpostoDevido();
                        }))
                .doOnSuccess(imposto -> log.debug("Imposto calculado: {}", imposto));
    }
}
