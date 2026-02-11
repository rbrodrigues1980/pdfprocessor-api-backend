package br.com.verticelabs.pdfprocessor.application.selic;

import br.com.verticelabs.pdfprocessor.domain.model.SelicMensalEntity;
import br.com.verticelabs.pdfprocessor.domain.model.TaxaSelic;
import br.com.verticelabs.pdfprocessor.domain.repository.TaxaSelicRepository;
import br.com.verticelabs.pdfprocessor.infrastructure.bcb.BcbSelicClient;
import br.com.verticelabs.pdfprocessor.infrastructure.bcb.BcbSelicMensalClient;
import br.com.verticelabs.pdfprocessor.infrastructure.mongodb.SpringDataSelicMensalRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.UUID;

/**
 * Serviço para gestão de taxas SELIC.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TaxaSelicService {

    private final TaxaSelicRepository repository;
    private final BcbSelicClient bcbClient;
    private final BcbSelicMensalClient bcbSelicMensalClient;
    private final SpringDataSelicMensalRepository selicMensalRepository;

    // ========== Consultas ==========

    /**
     * Busca a taxa SELIC vigente atualmente.
     */
    public Mono<TaxaSelic> buscarTaxaVigente() {
        return repository.findVigenteAtual();
    }

    /**
     * Busca a taxa SELIC vigente em uma data específica.
     */
    public Mono<TaxaSelic> buscarTaxaEmData(LocalDate data) {
        Instant instant = data.atStartOfDay(ZoneId.of("America/Sao_Paulo")).toInstant();
        return repository.findVigenteEmData(instant);
    }

    /**
     * Busca a taxa SELIC vigente em um ano específico (usa 1º de julho como
     * referência).
     */
    public Mono<TaxaSelic> buscarTaxaPorAno(int ano) {
        LocalDate dataRef = LocalDate.of(ano, 7, 1);
        return buscarTaxaEmData(dataRef);
    }

    /**
     * Lista todo o histórico de taxas.
     */
    public Flux<TaxaSelic> listarHistorico() {
        return repository.findAllOrderByDataDesc();
    }

    /**
     * Lista taxas de um período.
     */
    public Flux<TaxaSelic> listarPorPeriodo(LocalDate inicio, LocalDate fim) {
        Instant inicioInstant = inicio.atStartOfDay(ZoneId.of("America/Sao_Paulo")).toInstant();
        Instant fimInstant = fim.atTime(23, 59, 59).atZone(ZoneId.of("America/Sao_Paulo")).toInstant();
        return repository.findByPeriodo(inicioInstant, fimInstant);
    }

    /**
     * Busca por número de reunião do COPOM.
     */
    public Mono<TaxaSelic> buscarPorReuniao(Integer numeroReuniao) {
        return repository.findByNumeroReuniaoCopom(numeroReuniao);
    }

    /**
     * Conta total de registros.
     */
    public Mono<Long> contarRegistros() {
        return repository.count();
    }

    // ========== Cálculo de SELIC Acumulada ==========

    /**
     * Calcula a SELIC acumulada em um período.
     * Usa a fórmula de juros compostos: (1 + taxa1/100) * (1 + taxa2/100) * ... - 1
     * 
     * @param inicio Data de início do período
     * @param fim    Data de fim do período
     * @return Mono com o resultado do cálculo
     */
    public Mono<SelicAcumuladaResult> calcularSelicAcumulada(LocalDate inicio, LocalDate fim) {
        log.info("Calculando SELIC acumulada de {} a {}", inicio, fim);

        return repository.findAllOrderByDataDesc()
                .filter(taxa -> {
                    // Incluir taxas que se sobrepõem ao período
                    if (taxa.getDataInicioVigencia() == null)
                        return false;

                    Instant dataInicio = inicio.atStartOfDay(ZoneId.of("America/Sao_Paulo")).toInstant();
                    Instant dataFim = fim.atTime(23, 59, 59).atZone(ZoneId.of("America/Sao_Paulo")).toInstant();

                    Instant vigenciaInicio = taxa.getDataInicioVigencia();
                    Instant vigenciaFim = taxa.getDataFimVigencia() != null
                            ? taxa.getDataFimVigencia()
                            : Instant.now();

                    // Verifica sobreposição de períodos
                    return !vigenciaFim.isBefore(dataInicio) && !vigenciaInicio.isAfter(dataFim);
                })
                .collectList()
                .map(taxas -> {
                    if (taxas.isEmpty()) {
                        log.warn("Nenhuma taxa encontrada para o período {} a {}", inicio, fim);
                        return new SelicAcumuladaResult(inicio, fim, java.math.BigDecimal.ZERO,
                                java.math.BigDecimal.ZERO, 0, java.util.Collections.emptyList());
                    }

                    return calcularAcumulado(taxas, inicio, fim);
                });
    }

    /**
     * Calcula o acumulado das taxas SELIC.
     */
    private SelicAcumuladaResult calcularAcumulado(java.util.List<TaxaSelic> taxas, LocalDate inicio, LocalDate fim) {
        java.math.BigDecimal fatorAcumulado = java.math.BigDecimal.ONE;
        java.util.List<PeriodoSelic> periodosDetalhados = new java.util.ArrayList<>();

        for (TaxaSelic taxa : taxas) {
            if (taxa.getTaxaSelicEfetivaVigencia() == null)
                continue;

            // Calcular dias de sobreposição
            LocalDate vigenciaInicioLocal = taxa.getDataInicioVigencia()
                    .atZone(ZoneId.of("America/Sao_Paulo")).toLocalDate();
            LocalDate vigenciaFimLocal = taxa.getDataFimVigencia() != null
                    ? taxa.getDataFimVigencia().atZone(ZoneId.of("America/Sao_Paulo")).toLocalDate()
                    : LocalDate.now();

            // Período efetivo dentro do range solicitado
            LocalDate periodoInicio = vigenciaInicioLocal.isBefore(inicio) ? inicio : vigenciaInicioLocal;
            LocalDate periodoFim = vigenciaFimLocal.isAfter(fim) ? fim : vigenciaFimLocal;

            if (periodoInicio.isAfter(periodoFim))
                continue;

            long diasPeriodo = java.time.temporal.ChronoUnit.DAYS.between(periodoInicio, periodoFim) + 1;
            long diasVigenciaTotal = java.time.temporal.ChronoUnit.DAYS.between(vigenciaInicioLocal, vigenciaFimLocal)
                    + 1;

            if (diasVigenciaTotal <= 0)
                diasVigenciaTotal = 1;

            // Taxa proporcional ao período
            java.math.BigDecimal taxaPeriodo = taxa.getTaxaSelicEfetivaVigencia();
            java.math.BigDecimal proporcao = new java.math.BigDecimal(diasPeriodo)
                    .divide(new java.math.BigDecimal(diasVigenciaTotal), 10, java.math.RoundingMode.HALF_UP);
            java.math.BigDecimal taxaProporcional = taxaPeriodo.multiply(proporcao);

            // Fator: 1 + taxa/100
            java.math.BigDecimal fator = java.math.BigDecimal.ONE.add(
                    taxaProporcional.divide(new java.math.BigDecimal("100"), 10, java.math.RoundingMode.HALF_UP));
            fatorAcumulado = fatorAcumulado.multiply(fator);

            periodosDetalhados.add(new PeriodoSelic(
                    periodoInicio, periodoFim, diasPeriodo,
                    taxa.getMetaSelic(), taxaProporcional, taxa.getNumeroReuniaoCopom()));
        }

        // Taxa acumulada = (fator - 1) * 100
        java.math.BigDecimal taxaAcumulada = fatorAcumulado.subtract(java.math.BigDecimal.ONE)
                .multiply(new java.math.BigDecimal("100"))
                .setScale(4, java.math.RoundingMode.HALF_UP);

        long diasTotais = java.time.temporal.ChronoUnit.DAYS.between(inicio, fim) + 1;

        log.info("SELIC acumulada de {} a {}: {}% ({} dias, {} períodos)",
                inicio, fim, taxaAcumulada, diasTotais, periodosDetalhados.size());

        return new SelicAcumuladaResult(inicio, fim, taxaAcumulada,
                fatorAcumulado.setScale(8, java.math.RoundingMode.HALF_UP),
                diasTotais, periodosDetalhados);
    }

    // ========== DTOs de Resultado ==========

    @lombok.Data
    @lombok.AllArgsConstructor
    @lombok.NoArgsConstructor
    public static class SelicAcumuladaResult {
        private LocalDate dataInicio;
        private LocalDate dataFim;
        private java.math.BigDecimal taxaAcumuladaPercentual;
        private java.math.BigDecimal fatorAcumulado;
        private long diasTotais;
        private java.util.List<PeriodoSelic> periodos;
    }

    @lombok.Data
    @lombok.AllArgsConstructor
    @lombok.NoArgsConstructor
    public static class PeriodoSelic {
        private LocalDate inicio;
        private LocalDate fim;
        private long dias;
        private java.math.BigDecimal metaSelic;
        private java.math.BigDecimal taxaAplicada;
        private Integer reuniaoCopom;
    }

    // ========== Cálculo SELIC para Receita Federal ==========

    /**
     * Calcula a SELIC acumulada para fins de Receita Federal (restituição/débitos).
     * 
     * Regras RFB:
     * - Período começa no MÊS SEGUINTE ao pagamento/declaração
     * - Utiliza taxa MENSAL (calculada a partir das taxas efetivas por período)
     * - Período vai até o mês de atualização
     * 
     * Exemplo: Pagamento em 30/04/2017, atualização em 31/08/2025
     * - Período: Maio/2017 até Agosto/2025 = 100 meses
     * 
     * @param dataPagamento   Data do pagamento original
     * @param dataAtualizacao Data de atualização (quando o valor será corrigido)
     * @return Mono com o resultado do cálculo no formato RFB
     */
    public Mono<SelicReceitaFederalResult> calcularSelicReceitaFederal(LocalDate dataPagamento,
            LocalDate dataAtualizacao) {
        log.info("Calculando SELIC Receita Federal: pagamento={}, atualização={}", dataPagamento, dataAtualizacao);

        // Período inicia no mês seguinte ao pagamento
        java.time.YearMonth mesInicio = java.time.YearMonth.from(dataPagamento).plusMonths(1);
        java.time.YearMonth mesFim = java.time.YearMonth.from(dataAtualizacao);

        // Se mês fim < mês inicio, retorna zero
        if (mesFim.isBefore(mesInicio)) {
            return Mono.just(new SelicReceitaFederalResult(
                    dataPagamento, dataAtualizacao,
                    java.math.BigDecimal.ZERO, java.math.BigDecimal.ONE,
                    0, java.util.Collections.emptyList()));
        }

        // Buscar taxas mensais da tabela SELIC mensal (série 4390 do BCB)
        return selicMensalRepository.findAllByOrderByAnoDescMesDesc()
                .collectList()
                .map(taxasMensais -> calcularSelicMensalReal(taxasMensais, mesInicio, mesFim, dataPagamento,
                        dataAtualizacao));
    }

    /**
     * Calcula SELIC usando taxas mensais REAIS do BCB (série 4390).
     */
    private SelicReceitaFederalResult calcularSelicMensalReal(
            java.util.List<SelicMensalEntity> taxasMensais,
            java.time.YearMonth mesInicio,
            java.time.YearMonth mesFim,
            LocalDate dataPagamento,
            LocalDate dataAtualizacao) {

        // Criar mapa para lookup rápido: "ANO-MES" -> taxa
        java.util.Map<String, java.math.BigDecimal> mapaTaxas = new java.util.HashMap<>();
        for (SelicMensalEntity taxa : taxasMensais) {
            String chave = taxa.getAno() + "-" + taxa.getMes();
            mapaTaxas.put(chave, taxa.getTaxa());
        }

        java.math.BigDecimal fatorAcumulado = java.math.BigDecimal.ONE;
        java.util.List<SelicMensal> mesesDetalhados = new java.util.ArrayList<>();
        int totalMeses = 0;

        java.time.YearMonth mesAtual = mesInicio;
        while (!mesAtual.isAfter(mesFim)) {
            String chave = mesAtual.getYear() + "-" + mesAtual.getMonthValue();
            java.math.BigDecimal taxaMes = mapaTaxas.getOrDefault(chave, java.math.BigDecimal.ZERO);

            // Acumular fator: (1 + taxa/100)
            java.math.BigDecimal fatorMes = java.math.BigDecimal.ONE.add(
                    taxaMes.divide(new java.math.BigDecimal("100"), 10, java.math.RoundingMode.HALF_UP));
            fatorAcumulado = fatorAcumulado.multiply(fatorMes);

            mesesDetalhados.add(new SelicMensal(
                    mesAtual.getYear(),
                    mesAtual.getMonthValue(),
                    taxaMes));

            totalMeses++;
            mesAtual = mesAtual.plusMonths(1);
        }

        // Taxa acumulada = (fator - 1) * 100
        java.math.BigDecimal taxaAcumulada = fatorAcumulado.subtract(java.math.BigDecimal.ONE)
                .multiply(new java.math.BigDecimal("100"))
                .setScale(2, java.math.RoundingMode.HALF_UP);

        log.info("SELIC Receita Federal (mensal real): {} a {} = {}% ({} meses)",
                mesInicio, mesFim, taxaAcumulada, totalMeses);

        return new SelicReceitaFederalResult(
                dataPagamento, dataAtualizacao,
                taxaAcumulada,
                fatorAcumulado.setScale(8, java.math.RoundingMode.HALF_UP),
                totalMeses, mesesDetalhados);
    }

    /**
     * Calcula a SELIC mensal para cada mês do período.
     */
    private SelicReceitaFederalResult calcularSelicMensal(
            java.util.List<TaxaSelic> taxas,
            java.time.YearMonth mesInicio,
            java.time.YearMonth mesFim,
            LocalDate dataPagamento,
            LocalDate dataAtualizacao) {

        java.math.BigDecimal fatorAcumulado = java.math.BigDecimal.ONE;
        java.util.List<SelicMensal> mesesDetalhados = new java.util.ArrayList<>();
        int totalMeses = 0;

        java.time.YearMonth mesAtual = mesInicio;
        while (!mesAtual.isAfter(mesFim)) {
            // Encontrar a taxa SELIC para este mês
            java.math.BigDecimal taxaMes = calcularTaxaMes(taxas, mesAtual);

            // Acumular fator: (1 + taxa/100)
            java.math.BigDecimal fatorMes = java.math.BigDecimal.ONE.add(
                    taxaMes.divide(new java.math.BigDecimal("100"), 10, java.math.RoundingMode.HALF_UP));
            fatorAcumulado = fatorAcumulado.multiply(fatorMes);

            mesesDetalhados.add(new SelicMensal(
                    mesAtual.getYear(),
                    mesAtual.getMonthValue(),
                    taxaMes));

            totalMeses++;
            mesAtual = mesAtual.plusMonths(1);
        }

        // Taxa acumulada = (fator - 1) * 100
        java.math.BigDecimal taxaAcumulada = fatorAcumulado.subtract(java.math.BigDecimal.ONE)
                .multiply(new java.math.BigDecimal("100"))
                .setScale(2, java.math.RoundingMode.HALF_UP);

        log.info("SELIC Receita Federal: {} a {} = {}% ({} meses)",
                mesInicio, mesFim, taxaAcumulada, totalMeses);

        return new SelicReceitaFederalResult(
                dataPagamento, dataAtualizacao,
                taxaAcumulada,
                fatorAcumulado.setScale(8, java.math.RoundingMode.HALF_UP),
                totalMeses, mesesDetalhados);
    }

    /**
     * Calcula a taxa SELIC de um mês específico baseado nos períodos COPOM.
     */
    private java.math.BigDecimal calcularTaxaMes(java.util.List<TaxaSelic> taxas, java.time.YearMonth mes) {
        LocalDate primeiroDiaMes = mes.atDay(1);
        LocalDate ultimoDiaMes = mes.atEndOfMonth();

        java.math.BigDecimal taxaTotal = java.math.BigDecimal.ZERO;
        int diasContabilizados = 0;

        for (TaxaSelic taxa : taxas) {
            if (taxa.getDataInicioVigencia() == null || taxa.getTaxaSelicEfetivaVigencia() == null)
                continue;

            LocalDate vigenciaInicio = taxa.getDataInicioVigencia().atZone(ZoneId.of("America/Sao_Paulo"))
                    .toLocalDate();
            LocalDate vigenciaFim = taxa.getDataFimVigencia() != null
                    ? taxa.getDataFimVigencia().atZone(ZoneId.of("America/Sao_Paulo")).toLocalDate()
                    : LocalDate.now();

            // Verificar sobreposição com o mês
            if (vigenciaFim.isBefore(primeiroDiaMes) || vigenciaInicio.isAfter(ultimoDiaMes)) {
                continue;
            }

            // Calcular dias de sobreposição
            LocalDate inicioSobreposicao = vigenciaInicio.isBefore(primeiroDiaMes) ? primeiroDiaMes : vigenciaInicio;
            LocalDate fimSobreposicao = vigenciaFim.isAfter(ultimoDiaMes) ? ultimoDiaMes : vigenciaFim;

            long diasSobreposicao = java.time.temporal.ChronoUnit.DAYS.between(inicioSobreposicao, fimSobreposicao) + 1;
            long diasVigenciaTotal = java.time.temporal.ChronoUnit.DAYS.between(vigenciaInicio, vigenciaFim) + 1;

            if (diasVigenciaTotal <= 0)
                diasVigenciaTotal = 1;

            // Taxa proporcional aos dias
            java.math.BigDecimal proporcao = new java.math.BigDecimal(diasSobreposicao)
                    .divide(new java.math.BigDecimal(diasVigenciaTotal), 10, java.math.RoundingMode.HALF_UP);
            java.math.BigDecimal taxaProporcional = taxa.getTaxaSelicEfetivaVigencia().multiply(proporcao);

            taxaTotal = taxaTotal.add(taxaProporcional);
            diasContabilizados += diasSobreposicao;
        }

        return taxaTotal.setScale(4, java.math.RoundingMode.HALF_UP);
    }

    // ========== DTOs para Receita Federal ==========

    @lombok.Data
    @lombok.AllArgsConstructor
    @lombok.NoArgsConstructor
    public static class SelicReceitaFederalResult {
        private LocalDate dataPagamento;
        private LocalDate dataAtualizacao;
        private java.math.BigDecimal taxaAcumuladaPercentual;
        private java.math.BigDecimal fatorAcumulado;
        private int totalMeses;
        private java.util.List<SelicMensal> meses;
    }

    @lombok.Data
    @lombok.AllArgsConstructor
    @lombok.NoArgsConstructor
    public static class SelicMensal {
        private int ano;
        private int mes;
        private java.math.BigDecimal taxa;
    }

    /**
     * Sincroniza dados com a API do BCB.
     * Atualiza registros existentes e insere novos.
     */
    public Mono<SyncResult> sincronizarComBcb() {
        log.info("Iniciando sincronização com BCB...");

        return bcbClient.fetchHistoricoSelic()
                .doOnNext(taxa -> log.debug("Recebido do BCB: reuniao={}, meta={}",
                        taxa.getNumeroReuniaoCopom(), taxa.getMetaSelic()))
                .concatMap(this::salvarOuAtualizar)
                .collectList()
                .map(saved -> {
                    SyncResult result = new SyncResult(saved.size(), 0);
                    log.info("Sincronização concluída: {} registros salvos", saved.size());
                    return result;
                })
                .onErrorResume(e -> {
                    log.error("Erro na sincronização: {}", e.getMessage(), e);
                    return Mono.just(new SyncResult(0, 1));
                });
    }

    /**
     * Salva ou atualiza uma taxa SELIC.
     */
    private Mono<TaxaSelic> salvarOuAtualizar(TaxaSelic taxa) {
        if (taxa.getNumeroReuniaoCopom() == null) {
            log.warn("Taxa com numeroReuniaoCopom null, ignorando");
            return Mono.empty();
        }

        log.debug("Processando taxa: reuniao={}, meta={}",
                taxa.getNumeroReuniaoCopom(), taxa.getMetaSelic());

        return repository.findByNumeroReuniaoCopom(taxa.getNumeroReuniaoCopom())
                .flatMap(existing -> {
                    // Atualizar registro existente
                    log.trace("Atualizando reunião existente: {}", existing.getNumeroReuniaoCopom());
                    taxa.setId(existing.getId());
                    return repository.save(taxa);
                })
                .switchIfEmpty(Mono.defer(() -> {
                    // Inserir novo registro
                    log.trace("Inserindo nova reunião: {}", taxa.getNumeroReuniaoCopom());
                    return repository.save(taxa);
                }))
                .doOnSuccess(saved -> log.trace("Salvo: reuniao={}", saved.getNumeroReuniaoCopom()))
                .doOnError(e -> log.error("Erro ao salvar reuniao {}: {}",
                        taxa.getNumeroReuniaoCopom(), e.getMessage()));
    }

    /**
     * Força sincronização completa (recarrega todos os dados).
     */
    public Mono<SyncResult> sincronizarCompleto() {
        log.info("Iniciando sincronização COMPLETA com BCB...");
        return sincronizarComBcb();
    }

    // ========== Resultado de Sincronização ==========

    @lombok.Data
    @lombok.AllArgsConstructor
    public static class SyncResult {
        private int registrosProcessados;
        private int erros;
    }
}
