package br.com.verticelabs.pdfprocessor.application.excel;

import br.com.verticelabs.pdfprocessor.application.empresas.EmpresaHonorariosResolver;
import br.com.verticelabs.pdfprocessor.application.selic.TaxaSelicService;
import br.com.verticelabs.pdfprocessor.application.tributacao.IrTributacaoService;
import br.com.verticelabs.pdfprocessor.domain.model.IrParametrosAnuais;
import br.com.verticelabs.pdfprocessor.domain.model.IrTabelaTributacao;
import br.com.verticelabs.pdfprocessor.domain.model.IrpfDeclaracaoData;
import br.com.verticelabs.pdfprocessor.domain.model.Person;
import br.com.verticelabs.pdfprocessor.infrastructure.excel.ConsolidationAnoTotalsHelper;
import br.com.verticelabs.pdfprocessor.infrastructure.excel.ExcelResumoGeralHelper;
import br.com.verticelabs.pdfprocessor.infrastructure.excel.ExcelResumoGeralLinhaDTO;
import br.com.verticelabs.pdfprocessor.interfaces.consolidation.dto.ConsolidatedResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.AbstractMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

@Slf4j
@Service
@RequiredArgsConstructor
public class ResumoGeralAssemblyService {

    private static final ZoneId FUSO = ZoneId.of("America/Sao_Paulo");

    private final IrTributacaoService tributacaoService;
    private final ExcelResumoGeralHelper resumoGeralHelper;
    private final TaxaSelicService taxaSelicService;
    private final EmpresaHonorariosResolver empresaHonorariosResolver;

    public Mono<ResumoGeralMontagemResult> montar(
            Person person,
            ConsolidatedResponse consolidatedResponse,
            Map<String, IrpfDeclaracaoData> irpfDeclaracoes) {

        Set<String> anosContracheque = new HashSet<>(consolidatedResponse.getAnos());
        Map<String, IrpfDeclaracaoData> irpfDeclaracoesAlinhadas =
                resumoGeralHelper.filtrarDeclaracoesPorAnosContracheque(irpfDeclaracoes, anosContracheque);

        log.info("Anos com contracheque: {}; declarações IR alinhadas: {}",
                anosContracheque, irpfDeclaracoesAlinhadas.keySet());

        return Mono.zip(
                        buscarTabelasTributacao(anosContracheque),
                        buscarParametrosTributacao(anosContracheque))
                .flatMap(zip -> {
                    Map<String, List<IrTabelaTributacao>> tabelasTributacao = zip.getT1();
                    Map<String, IrParametrosAnuais> parametrosTributacao = zip.getT2();

                    TreeSet<String> anosOrdenados = new TreeSet<>(consolidatedResponse.getAnos());
                    Map<String, BigDecimal> prevComplPorAno = new HashMap<>();
                    for (String ano : anosOrdenados) {
                        prevComplPorAno.put(ano,
                                ConsolidationAnoTotalsHelper.calcularTotalContracheques(consolidatedResponse, ano));
                    }

                    if (irpfDeclaracoesAlinhadas.isEmpty()) {
                        return buildResult(person, List.of(), irpfDeclaracoesAlinhadas, prevComplPorAno,
                                tabelasTributacao, parametrosTributacao);
                    }

                    List<ExcelResumoGeralLinhaDTO> linhasResumoBase = resumoGeralHelper.montarLinhas(
                            irpfDeclaracoesAlinhadas, prevComplPorAno, tabelasTributacao, parametrosTributacao);

                    if (linhasResumoBase.isEmpty()) {
                        return buildResult(person, List.of(), irpfDeclaracoesAlinhadas, prevComplPorAno,
                                tabelasTributacao, parametrosTributacao);
                    }

                    LocalDate dataPagamentoSelic = LocalDate.now(FUSO);

                    return Flux.fromIterable(linhasResumoBase)
                            .concatMap(linha -> {
                                if (linha.getPrincipal().compareTo(BigDecimal.ZERO) > 0
                                        && linha.getDataVencimento() != null) {
                                    return taxaSelicService.calcularSelicReceitaFederal(
                                                    linha.getDataVencimento(),
                                                    dataPagamentoSelic,
                                                    linha.getPrincipal())
                                            .map(linha::enriquecerComSelic)
                                            .onErrorResume(e -> {
                                                log.warn("SELIC Resumo Geral ano {}: {}",
                                                        linha.getAnoCalendario(), e.getMessage());
                                                return Mono.just(linha);
                                            });
                                }
                                return Mono.just(linha);
                            })
                            .collectList()
                            .map(resumoGeralHelper::ordenarPorAnoCalendario)
                            .flatMap(linhas -> buildResult(person, linhas, irpfDeclaracoesAlinhadas, prevComplPorAno,
                                    tabelasTributacao, parametrosTributacao));
                });
    }

    private Mono<ResumoGeralMontagemResult> buildResult(
            Person person,
            List<ExcelResumoGeralLinhaDTO> linhas,
            Map<String, IrpfDeclaracaoData> irpfDeclaracoesAlinhadas,
            Map<String, BigDecimal> prevComplPorAno,
            Map<String, List<IrTabelaTributacao>> tabelasTributacao,
            Map<String, IrParametrosAnuais> parametrosTributacao) {

        LocalDate dataPagamentoSelic = LocalDate.now(FUSO);
        LocalDateTime dataGeracao = LocalDateTime.now(FUSO);

        return empresaHonorariosResolver.resolve(person)
                .map(honorariosConfig -> {
                    ExcelResumoGeralHelper.TotaisResumoGeral totais = linhas.isEmpty()
                            ? new ExcelResumoGeralHelper.TotaisResumoGeral(
                                    BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                                    BigDecimal.ZERO, BigDecimal.ZERO)
                            : resumoGeralHelper.calcularTotais(linhas, honorariosConfig.percentualFracao());
                    return new ResumoGeralMontagemResult(
                            linhas,
                            honorariosConfig,
                            totais,
                            irpfDeclaracoesAlinhadas,
                            prevComplPorAno,
                            tabelasTributacao,
                            parametrosTributacao,
                            dataPagamentoSelic,
                            dataGeracao);
                });
    }

    private Mono<Map<String, List<IrTabelaTributacao>>> buscarTabelasTributacao(Set<String> anos) {
        return Flux.fromIterable(anos)
                .flatMap(ano -> {
                    try {
                        int anoInt = Integer.parseInt(ano);
                        return tributacaoService.buscarFaixas(anoInt, "ANUAL")
                                .collectList()
                                .map(faixas -> new AbstractMap.SimpleEntry<>(ano, faixas));
                    } catch (NumberFormatException e) {
                        log.warn("Ano inválido: {}", ano);
                        return Mono.empty();
                    }
                })
                .collectMap(AbstractMap.SimpleEntry::getKey, AbstractMap.SimpleEntry::getValue);
    }

    private Mono<Map<String, IrParametrosAnuais>> buscarParametrosTributacao(Set<String> anos) {
        return Flux.fromIterable(anos)
                .flatMap(ano -> {
                    try {
                        int anoInt = Integer.parseInt(ano);
                        return tributacaoService.buscarParametros(anoInt, "ANUAL")
                                .defaultIfEmpty(IrParametrosAnuais.builder()
                                        .anoCalendario(anoInt)
                                        .tipoIncidencia("ANUAL")
                                        .build())
                                .map(params -> new AbstractMap.SimpleEntry<>(ano, params));
                    } catch (NumberFormatException e) {
                        log.warn("Ano inválido para parâmetros: {}", ano);
                        return Mono.empty();
                    }
                })
                .collectMap(AbstractMap.SimpleEntry::getKey, AbstractMap.SimpleEntry::getValue);
    }
}
