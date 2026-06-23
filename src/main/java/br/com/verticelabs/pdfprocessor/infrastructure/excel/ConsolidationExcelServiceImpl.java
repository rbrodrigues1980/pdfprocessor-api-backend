package br.com.verticelabs.pdfprocessor.infrastructure.excel;

import br.com.verticelabs.pdfprocessor.application.excel.ExcelExportService;
import br.com.verticelabs.pdfprocessor.application.incometax.IrpfPrevidenciaOficialResolver;
import br.com.verticelabs.pdfprocessor.application.selic.TaxaSelicService;
import br.com.verticelabs.pdfprocessor.application.tributacao.IrCalculoProgressivoService;
import br.com.verticelabs.pdfprocessor.application.tributacao.IrDoacoesDeducaoCalculator;
import br.com.verticelabs.pdfprocessor.application.tributacao.IrSimuladorMotorService;
import br.com.verticelabs.pdfprocessor.application.tributacao.dto.ModeloTributacaoResultDTO;
import br.com.verticelabs.pdfprocessor.application.tributacao.dto.ResultadoCalculoIrpfDTO;
import br.com.verticelabs.pdfprocessor.application.tributacao.dto.ResultadoFaixaCalculoDTO;
import br.com.verticelabs.pdfprocessor.application.tributacao.dto.SimuladorIrpfRequest;
import br.com.verticelabs.pdfprocessor.application.tributacao.dto.SimuladorIrpfResponse;
import br.com.verticelabs.pdfprocessor.domain.model.IrParametrosAnuais;
import br.com.verticelabs.pdfprocessor.domain.exceptions.ExcelGenerationException;
import br.com.verticelabs.pdfprocessor.domain.model.DocumentType;
import br.com.verticelabs.pdfprocessor.domain.model.IrpfDeclaracaoData;
import br.com.verticelabs.pdfprocessor.domain.model.PayrollEntry;
import br.com.verticelabs.pdfprocessor.domain.model.Person;
import br.com.verticelabs.pdfprocessor.domain.repository.PayrollDocumentRepository;
import br.com.verticelabs.pdfprocessor.domain.repository.PayrollEntryRepository;
import br.com.verticelabs.pdfprocessor.interfaces.consolidation.dto.ConsolidatedResponse;
import br.com.verticelabs.pdfprocessor.interfaces.consolidation.dto.ConsolidationRow;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.ss.util.CellReference;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class ConsolidationExcelServiceImpl implements ExcelExportService {

    private final PayrollDocumentRepository documentRepository;
    private final PayrollEntryRepository entryRepository;
    private final br.com.verticelabs.pdfprocessor.application.tributacao.IrTributacaoService tributacaoService;
    private final IrCalculoProgressivoService calculoProgressivoService;
    private final IrSimuladorMotorService simuladorMotorService;
    private final ExcelIrpfSimulacaoMapper excelIrpfSimulacaoMapper;
    private final ExcelIrpfDeducoesResumoHelper deducoesResumoHelper;
    private final ExcelResumoGeralHelper resumoGeralHelper;
    private final TaxaSelicService taxaSelicService;
        private final ExcelResumoGeralLogoHelper resumoGeralLogoHelper;
        private final br.com.verticelabs.pdfprocessor.application.empresas.EmpresaHonorariosResolver empresaHonorariosResolver;

    @Override
    public Mono<byte[]> generateConsolidationExcel(Person person, ConsolidatedResponse consolidatedResponse,
            String filename) {
        // Buscar entries de IR e declarações IRPF completas em paralelo
        return Mono.zip(
                buscarEntriesIncomeTax(person),
                buscarIrpfDeclaracoes(person)
        ).flatMap(tuple -> {
                    Map<String, Map<String, BigDecimal>> incomeTaxEntries = tuple.getT1();
                    Map<String, IrpfDeclaracaoData> irpfDeclaracoes = tuple.getT2();
                    java.util.Set<String> anosContracheque = new java.util.HashSet<>(consolidatedResponse.getAnos());
                    Map<String, IrpfDeclaracaoData> irpfDeclaracoesAlinhadas =
                            resumoGeralHelper.filtrarDeclaracoesPorAnosContracheque(
                                    irpfDeclaracoes, anosContracheque);
                    java.util.Set<String> anosTributacao = new java.util.HashSet<>(anosContracheque);
                    log.info("Anos com contracheque: {}; declarações IR alinhadas: {}",
                            anosContracheque, irpfDeclaracoesAlinhadas.keySet());
                    return Mono.zip(buscarTabelasTributacao(anosTributacao), buscarParametrosTributacao(anosTributacao))
                            .flatMap(zip -> {
                                Map<String, java.util.List<br.com.verticelabs.pdfprocessor.domain.model.IrTabelaTributacao>> tabelasTributacao = zip.getT1();
                                Map<String, IrParametrosAnuais> parametrosTributacao = zip.getT2();

                                TreeSet<String> anosOrdenados = new TreeSet<>(consolidatedResponse.getAnos());
                                Map<String, BigDecimal> prevComplPorAno = new HashMap<>();
                                for (String ano : anosOrdenados) {
                                    prevComplPorAno.put(ano, calcularTotalContracheques(consolidatedResponse, ano));
                                }

                                List<ExcelResumoGeralLinhaDTO> linhasResumoBase = resumoGeralHelper.montarLinhas(
                                        irpfDeclaracoesAlinhadas, prevComplPorAno, tabelasTributacao, parametrosTributacao);
                                LocalDate dataPagamentoSelic = LocalDate.now(ZoneId.of("America/Sao_Paulo"));

                                Mono<List<ExcelResumoGeralLinhaDTO>> linhasResumoComSelic = Flux.fromIterable(linhasResumoBase)
                                        .concatMap(linha -> {
                                            if (linha.getPrincipal().compareTo(BigDecimal.ZERO) > 0
                                                    && linha.getDataVencimento() != null) {
                                                return taxaSelicService.calcularSelicReceitaFederal(
                                                                linha.getDataVencimento(),
                                                                dataPagamentoSelic,
                                                                linha.getPrincipal())
                                                        .map(linha::enriquecerComSelic)
                                                        .onErrorResume(e -> {
                                                            log.warn("SELIC Resumo Geral ano {}: {}", linha.getAnoCalendario(), e.getMessage());
                                                            return Mono.just(linha);
                                                        });
                                            }
                                            return Mono.just(linha);
                                        })
                                        .collectList()
                                        .map(resumoGeralHelper::ordenarPorAnoCalendario);

                                return linhasResumoComSelic.flatMap(linhasResumo ->
                                        empresaHonorariosResolver.resolve(person).flatMap(honorariosConfig ->
                                                Mono.fromCallable(() -> {
                                    try (Workbook workbook = new XSSFWorkbook();
                                            ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                                        log.debug("Criando workbook Excel...");

                                        // Criar estilos
                                        CellStyle headerStyle = createHeaderStyle(workbook);
                                        CellStyle numberStyle = createNumberStyle(workbook);
                                        CellStyle defaultStyle = createDefaultStyle(workbook);
                                        CellStyle totalStyle = createTotalStyle(workbook);
                                        CellStyle infoStyle = createInfoStyle(workbook);

                                        log.info("Anos para criar abas: {} (ordem crescente)", anosOrdenados);

                                        // Criar uma aba para cada ano
                                        for (String ano : anosOrdenados) {
                                            log.debug("Criando aba para ano: {}", ano);
                                            Sheet sheet = workbook.createSheet(ano);

                                            int currentRow = 0;

                                            // Linha 1: Informações da pessoa (CPF e Nome na mesma linha)
                                            currentRow = addPersonInfo(sheet, person, currentRow, infoStyle);

                                            // Linha 2: Cabeçalho da matriz
                                            currentRow = addMatrixHeader(sheet, ano, currentRow, headerStyle);

                                            // Dados das rubricas (apenas do ano atual)
                                            currentRow = addMatrixData(sheet, consolidatedResponse, ano, currentRow,
                                                    numberStyle,
                                                    defaultStyle);

                                            // Linha de totais mensais
                                            currentRow = addMonthlyTotals(sheet, consolidatedResponse, ano, currentRow,
                                                    totalStyle,
                                                    numberStyle);

                                            // Duas simulações IRPF (declaração + contracheques), se houver declaração
                                            BigDecimal prevComplPlanilha = prevComplPorAno.getOrDefault(ano, BigDecimal.ZERO);
                                            CellStyle simTitleStyle = createSimTitleStyle(workbook);
                                            CellStyle simBannerSubtitleStyle = createSimBannerSubtitleStyle(workbook);
                                            CellStyle simLabelStyle = createSimLabelStyle(workbook);
                                            CellStyle simValueStyle = createSimValueStyle(workbook);
                                            CellStyle simSubLabelStyle = createSimSubLabelStyle(workbook);
                                            CellStyle simTotalStyle = createSimTotalStyle(workbook);
                                            CellStyle simTotalLabelStyle = createSimTotalLabelStyle(workbook);
                                            CellStyle simAliquotaStyle = createSimAliquotaStyle(workbook);
                                            CellStyle simHighlightGreenStyle = createSimHighlightGreenStyle(workbook);

                                            IrpfDeclaracaoData irpfAno = irpfDeclaracoesAlinhadas.get(ano);
                                            if (irpfAno != null) {
                                                if ("SIMPLIFICADO".equalsIgnoreCase(irpfAno.getTipoTributacao())) {
                                                    currentRow = addBlocoConformeDeclaracaoSimplificada(
                                                            sheet, person, irpfAno, currentRow,
                                                            simTitleStyle, simBannerSubtitleStyle, simLabelStyle,
                                                            simValueStyle, simSubLabelStyle, simTotalStyle,
                                                            simTotalLabelStyle, simAliquotaStyle);
                                                } else {
                                                    currentRow = addBlocoConformeDeclaracaoCompleta(
                                                            sheet, person, irpfAno, currentRow,
                                                            simTitleStyle, simBannerSubtitleStyle, simLabelStyle,
                                                            simValueStyle, simSubLabelStyle, simTotalStyle,
                                                            simTotalLabelStyle, simAliquotaStyle);
                                                }
                                                currentRow = addBlocoSimulacaoCompletaPlanilha(
                                                        sheet, person, ano, irpfAno, prevComplPlanilha,
                                                        tabelasTributacao, parametrosTributacao, currentRow,
                                                        simTitleStyle, simBannerSubtitleStyle, simLabelStyle,
                                                        simValueStyle, simSubLabelStyle, simTotalStyle,
                                                        simTotalLabelStyle, simAliquotaStyle, simHighlightGreenStyle);
                                            }

                                            // Ajustar largura das colunas
                                            autoSizeColumns(sheet,
                                                    consolidatedResponse.getRubricas().size() > 0
                                                            ? consolidatedResponse.getRubricas().get(0).getValores()
                                                                    .size()
                                                            : 0);

                                            // Congelar linha de cabeçalho (linha 2, índice 1)
                                            sheet.createFreezePane(0, 2); // Congela após linha 2 (cabeçalho)
                                        }

                                        // Aba Resumo Geral (antes da Consolidação)
                                        if (!linhasResumo.isEmpty()) {
                                            log.info("Criando aba Resumo Geral com {} anos", linhasResumo.size());
                                            addResumoGeralSheet(workbook, person, linhasResumo, honorariosConfig,
                                                    headerStyle, numberStyle, defaultStyle, totalStyle);
                                        }

                                        // Criar aba "Consolidação" com todos os anos
                                        log.info("Criando aba consolidada com todos os anos");
                                        Sheet consolidatedSheet = workbook.createSheet("Consolidação");
                                        int currentRow = 0;

                                        // Linha 1: Informações da pessoa
                                        currentRow = addPersonInfo(consolidatedSheet, person, currentRow, infoStyle);

                                        // Linha 2: Cabeçalho consolidado (todos os anos)
                                        currentRow = addConsolidatedHeader(consolidatedSheet, anosOrdenados, currentRow,
                                                headerStyle);

                                        // Dados consolidados (todas as rubricas de todos os anos)
                                        currentRow = addConsolidatedData(consolidatedSheet, consolidatedResponse,
                                                anosOrdenados,
                                                currentRow, numberStyle, defaultStyle);

                                        // Linha de totais consolidados
                                        currentRow = addConsolidatedTotals(consolidatedSheet, consolidatedResponse,
                                                anosOrdenados,
                                                currentRow, totalStyle, numberStyle);

                                        // Ajustar largura das colunas
                                        autoSizeConsolidatedColumns(consolidatedSheet, anosOrdenados.size());

                                        // Congelar linha de cabeçalho
                                        consolidatedSheet.createFreezePane(0, 2);

                                        workbook.write(out);
                                        log.info("Workbook Excel gerado com sucesso. Tamanho: {} bytes", out.size());
                                        return out.toByteArray();
                                    } catch (Exception e) {
                                        log.error("Erro ao gerar Excel", e);
                                        throw new ExcelGenerationException(
                                                "Erro ao gerar arquivo Excel: " + e.getMessage(), e);
                                    }
                                }).subscribeOn(Schedulers.boundedElastic())
                                        )
                                );
                            });
                });
    }

    /**
     * Busca todas as entries de declaração de IR para a pessoa.
     * Retorna um mapa: ano -> código_rubrica -> valor
     */
    private Mono<Map<String, Map<String, BigDecimal>>> buscarEntriesIncomeTax(Person person) {
        log.info("Buscando entries de declaração de IR para pessoa: {} ({})", person.getNome(), person.getCpf());

        // Buscar documentos de IR da pessoa
        return documentRepository.findByTenantIdAndCpf(person.getTenantId(), person.getCpf())
                .filter(doc -> doc.getTipo() == DocumentType.INCOME_TAX)
                .flatMap(doc -> {
                    log.debug("Documento de IR encontrado: {} (ano: {})", doc.getId(), doc.getAnoDetectado());
                    return entryRepository.findByTenantIdAndDocumentoId(person.getTenantId(), doc.getId());
                })
                .filter(entry -> "INCOME_TAX".equals(entry.getOrigem()))
                .collectList()
                .map(entries -> {
                    log.info("Encontradas {} entries de IR", entries.size());

                    // Criar mapa: ano -> código -> valor
                    Map<String, Map<String, BigDecimal>> map = new HashMap<>();

                    for (PayrollEntry entry : entries) {
                        // Extrair ano da referência (formato "2016-00")
                        if (entry.getReferencia() != null && entry.getReferencia().contains("-")) {
                            String ano = entry.getReferencia().split("-")[0];
                            String codigo = entry.getRubricaCodigo();
                            BigDecimal valor = entry.getValor() != null ? entry.getValor() : BigDecimal.ZERO;

                            map.putIfAbsent(ano, new HashMap<>());
                            map.get(ano).put(codigo, valor);

                            log.debug("Entry IR: ano={}, codigo={}, valor={}", ano, codigo, valor);
                        }
                    }

                    log.info("Mapeamento de IR por ano: {}", map.keySet());
                    return map;
                })
                .onErrorResume(e -> {
                    log.warn("Erro ao buscar entries de IR (continuando sem dados de IR): {}", e.getMessage());
                    return Mono.just(Collections.emptyMap());
                });
    }

    /**
     * Busca as tabelas de tributação para os anos especificados.
     * Retorna um mapa: ano -> lista de faixas de tributação
     */
    private Mono<Map<String, java.util.List<br.com.verticelabs.pdfprocessor.domain.model.IrTabelaTributacao>>> buscarTabelasTributacao(
            java.util.Set<String> anos) {
        log.info("Buscando tabelas de tributação para anos: {}", anos);

        return reactor.core.publisher.Flux.fromIterable(anos)
                .flatMap(ano -> {
                    try {
                        int anoInt = Integer.parseInt(ano);
                        return tributacaoService.buscarFaixas(anoInt, "ANUAL")
                                .collectList()
                                .map(faixas -> new java.util.AbstractMap.SimpleEntry<>(ano, faixas));
                    } catch (NumberFormatException e) {
                        log.warn("Ano inválido: {}", ano);
                        return Mono.empty();
                    }
                })
                .collectMap(
                        java.util.AbstractMap.SimpleEntry::getKey,
                        java.util.AbstractMap.SimpleEntry::getValue)
                .doOnSuccess(map -> log.info("Tabelas de tributação carregadas para {} anos", map.size()));
    }

    private Mono<Map<String, IrParametrosAnuais>> buscarParametrosTributacao(java.util.Set<String> anos) {
        log.info("Buscando parâmetros de tributação para anos: {}", anos);

        return reactor.core.publisher.Flux.fromIterable(anos)
                .flatMap(ano -> {
                    try {
                        int anoInt = Integer.parseInt(ano);
                        return tributacaoService.buscarParametros(anoInt, "ANUAL")
                                .defaultIfEmpty(IrParametrosAnuais.builder().anoCalendario(anoInt).tipoIncidencia("ANUAL").build())
                                .map(params -> new java.util.AbstractMap.SimpleEntry<>(ano, params));
                    } catch (NumberFormatException e) {
                        log.warn("Ano inválido para parâmetros: {}", ano);
                        return Mono.empty();
                    }
                })
                .collectMap(
                        java.util.AbstractMap.SimpleEntry::getKey,
                        java.util.AbstractMap.SimpleEntry::getValue)
                .doOnSuccess(map -> log.info("Parâmetros de tributação carregados para {} anos", map.size()));
    }

    /**
     * Calcula imposto devido usando o serviço centralizado de cálculo progressivo.
     */
    private BigDecimal calcularImpostoComTabelaBanco(BigDecimal baseCalculo, String ano,
            Map<String, java.util.List<br.com.verticelabs.pdfprocessor.domain.model.IrTabelaTributacao>> tabelasTributacao,
            Map<String, IrParametrosAnuais> parametrosTributacao) {

        if (baseCalculo == null || baseCalculo.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO;
        }

        java.util.List<br.com.verticelabs.pdfprocessor.domain.model.IrTabelaTributacao> faixas =
                tabelasTributacao.getOrDefault(ano, Collections.emptyList());
        if (faixas.isEmpty()) {
            log.warn("Nenhuma tabela de tributação encontrada para ano: {}", ano);
            return BigDecimal.ZERO;
        }

        IrParametrosAnuais params = parametrosTributacao != null ? parametrosTributacao.get(ano) : null;
        ResultadoCalculoIrpfDTO resultado = calculoProgressivoService.calcular(
                faixas, baseCalculo, BigDecimal.ZERO, BigDecimal.ZERO, params);
        return resultado.getImpostoDevido();
    }

    /**
     * Adiciona quadro resumo de Imposto de Renda após a linha TOTAL.
     * 
     * Estrutura:
     * - Linha vazia (espaçamento)
     * - RENDIMENTOS TRIBUTÁVEIS | valor
     * - DEDUÇÕES (Contrib. prev. compl. - Declaração) | valor
     * - DEDUÇÕES (Contrib. prev. compl. - Novo cálculo) | total contracheques
     * - DEDUÇÕES (Total) | valor
     * - DEDUÇÕES (Total - novo cálculo) | calculado
     * - Limite de 12% sobre os rendimentos tributáveis | calculado
     */
    private int addIncomeTaxSummaryTable(Sheet sheet, String ano,
            Map<String, Map<String, BigDecimal>> incomeTaxEntries,
            ConsolidatedResponse consolidatedResponse,
            Map<String, java.util.List<br.com.verticelabs.pdfprocessor.domain.model.IrTabelaTributacao>> tabelasTributacao,
            Map<String, IrParametrosAnuais> parametrosTributacao,
            int startRow, CellStyle totalStyle, CellStyle numberStyle) {

        Map<String, BigDecimal> irAno = incomeTaxEntries.get(ano);

        if (irAno == null || irAno.isEmpty()) {
            log.debug("Nenhum dado de IR encontrado para ano: {}", ano);
            return startRow;
        }

        log.info("Adicionando quadro resumo de IR para ano {}", ano);

        // Buscar valores do IR
        BigDecimal rendimentosTributaveis = irAno.getOrDefault("IR_RENDIMENTOS_TRIBUTAVEIS", BigDecimal.ZERO);
        BigDecimal deducoesContribPrevCompl = irAno.getOrDefault("IR_DEDUCOES_CONTRIB_PREV_COMPL", BigDecimal.ZERO);
        BigDecimal deducoesTotal = irAno.getOrDefault("IR_DEDUCOES", BigDecimal.ZERO);

        // Calcular total dos contracheques (novo cálculo) = soma de todas as rubricas
        // do ano
        BigDecimal totalContracheques = calcularTotalContracheques(consolidatedResponse, ano);

        // Calcular DEDUÇÕES Total - novo cálculo = deducoesTotal -
        // deducoesContribPrevCompl + totalContracheques
        BigDecimal deducoesTotalNovoCalculo = deducoesTotal
                .subtract(deducoesContribPrevCompl)
                .add(totalContracheques);

        // Calcular limite de 12% sobre rendimentos tributáveis
        BigDecimal limite12Porcento = rendimentosTributaveis
                .multiply(new BigDecimal("0.12"));

        // Buscar valores do segundo quadro
        BigDecimal baseCalculoImposto = irAno.getOrDefault("IR_BASE_CALCULO_IMPOSTO", BigDecimal.ZERO);
        BigDecimal totalImpostoDevido = irAno.getOrDefault("IR_TOTAL_IMPOSTO_DEVIDO", BigDecimal.ZERO);
        BigDecimal impostoPagoTotal = irAno.getOrDefault("IR_IMPOSTO_PAGO_TOTAL", BigDecimal.ZERO);
        BigDecimal saldoImpostoPagar = irAno.getOrDefault("IR_SALDO_IMPOSTO_A_PAGAR", BigDecimal.ZERO);

        // Linha vazia (espaçamento)
        sheet.createRow(startRow);
        int rowNum = startRow + 1;

        // =============================================
        // QUADRO 1: RENDIMENTOS E DEDUÇÕES
        // =============================================

        // Linha 1: RENDIMENTOS TRIBUTÁVEIS
        rowNum = addSummaryRow(sheet, rowNum, "RENDIMENTOS TRIBUTÁVEIS",
                rendimentosTributaveis, totalStyle, numberStyle,
                "Total de rendimentos tributáveis declarados na DIRPF (salários, aluguéis, etc.)");

        // Linha 2: DEDUÇÕES (Contrib. prev. compl. - Declaração)
        rowNum = addSummaryRow(sheet, rowNum, "DEDUÇÕES (Contribuição à previdência complementar - Declaração)",
                deducoesContribPrevCompl, totalStyle, numberStyle,
                "Valor declarado na DIRPF para contribuições à previdência complementar/FAPI");

        // Linha 3: DEDUÇÕES (Contrib. prev. compl. - Novo cálculo)
        rowNum = addSummaryRow(sheet, rowNum, "DEDUÇÕES (Contribuição à previdência complementar - Novo calculo)",
                totalContracheques, totalStyle, numberStyle,
                "Soma das contribuições extraordinárias extraídas dos contracheques no ano");

        // Linha 4: DEDUÇÕES (Total)
        rowNum = addSummaryRow(sheet, rowNum, "DEDUÇÕES (Total)",
                deducoesTotal, totalStyle, numberStyle,
                "Total de deduções declaradas na DIRPF (previdência, dependentes, instrução, médicas)");

        // Linha 5: DEDUÇÕES (Total - novo cálculo)
        rowNum = addSummaryRow(sheet, rowNum, "DEDUÇÕES (Total - novo calculo)",
                deducoesTotalNovoCalculo, totalStyle, numberStyle,
                "Deduções Total - Contrib. Declaração + Contrib. Contracheques (recalculado)");

        // Linha 6: Limite de 12% sobre os rendimentos tributáveis
        rowNum = addSummaryRow(sheet, rowNum, "Limite de 12% sobre os rendimentos tributáveis",
                limite12Porcento, totalStyle, numberStyle,
                "Limite máximo de dedução para previdência complementar (12% dos rendimentos)");

        // =============================================
        // QUADRO 2: IMPOSTO DEVIDO E PAGO
        // =============================================

        // Linha vazia (espaçamento entre os quadros)
        sheet.createRow(rowNum);
        rowNum++;

        // Linha 1: Base de calculo do imposto
        rowNum = addSummaryRow(sheet, rowNum, "Base de calculo do imposto",
                baseCalculoImposto, totalStyle, numberStyle,
                "Rendimentos tributáveis menos deduções (base para cálculo do IR)");

        // Linha 2: IMPOSTO DEVIDO
        rowNum = addSummaryRow(sheet, rowNum, "IMPOSTO DEVIDO",
                totalImpostoDevido, totalStyle, numberStyle,
                "Imposto calculado aplicando a tabela progressiva sobre a base de cálculo");

        // Linha 3: IMPOSTO PAGO (Imposto retido na fonte)
        rowNum = addSummaryRow(sheet, rowNum, "IMPOSTO PAGO (Imposto retido na fonte)",
                impostoPagoTotal, totalStyle, numberStyle,
                "Total de IR já retido na fonte durante o ano (antecipações)");

        // Linha 4: SALDO IMPOSTO A PAGAR (Conforme declaração entregue)
        rowNum = addSummaryRow(sheet, rowNum, "SALDO IMPOSTO A PAGAR (Conforme declaração entregue)",
                saldoImpostoPagar, totalStyle, numberStyle,
                "Imposto Devido - Imposto Pago = saldo a pagar/restituir (conforme DIRPF)");

        // =============================================
        // QUADRO 3: ESTUDO CONTRIBUIÇÕES EXTRAORDINÁRIAS
        // =============================================

        // Calcular Base de cálculo (Estudo) = Rendimentos - Deduções (Total novo
        // cálculo)
        BigDecimal baseCalculoEstudo = rendimentosTributaveis.subtract(deducoesTotalNovoCalculo);

        // Calcular Imposto Devido (Estudo) usando tabela de tributação do banco
        BigDecimal impostoDevidoEstudo = calcularImpostoComTabelaBanco(baseCalculoEstudo, ano, tabelasTributacao, parametrosTributacao);

        // Saldo Imposto a Pagar (Estudo) = Imposto Devido (Estudo) - Imposto Pago
        BigDecimal saldoImpostoEstudo = impostoDevidoEstudo.subtract(impostoPagoTotal);

        // Resultado = Saldo (Declaração) - Saldo (Estudo)
        BigDecimal resultadoRestituir = saldoImpostoPagar.subtract(saldoImpostoEstudo);

        // Linha vazia (espaçamento entre os quadros)
        sheet.createRow(rowNum);
        rowNum++;

        // Linha 1: Base de cálculo (Estudo)
        rowNum = addSummaryRow(sheet, rowNum,
                "Base de calculo de imposto (Conforme estudo contribuições extraordinárias)",
                baseCalculoEstudo, totalStyle, numberStyle,
                "Rendimentos - Deduções (com novo cálculo de contribuições extraordinárias)");

        // Linha 2: IMPOSTO DEVIDO (Estudo)
        rowNum = addSummaryRow(sheet, rowNum, "IMPOSTO DEVIDO",
                impostoDevidoEstudo, totalStyle, numberStyle,
                "Imposto recalculado com base nas contribuições extraordinárias corrigidas");

        // Linha 3: IMPOSTO PAGO (mesmo valor)
        rowNum = addSummaryRow(sheet, rowNum, "IMPOSTO PAGO (Imposto retido na fonte)",
                impostoPagoTotal, totalStyle, numberStyle,
                "Mesmo valor do imposto pago (não muda no estudo)");

        // Linha 4: SALDO IMPOSTO A PAGAR (Estudo)
        rowNum = addSummaryRow(sheet, rowNum, "SALDO IMPOSTO A PAGAR (Conforme estudo contribuições extraordinárias)",
                saldoImpostoEstudo, totalStyle, numberStyle,
                "Imposto Devido (estudo) - Imposto Pago = saldo recalculado");

        // =============================================
        // RESULTADO FINAL
        // =============================================

        // Linha vazia (espaçamento)
        sheet.createRow(rowNum);
        rowNum++;

        // Linha: Resultado de imposto a restituir
        rowNum = addSummaryRow(sheet, rowNum, "Resultado de imposto a restituir",
                resultadoRestituir, totalStyle, numberStyle,
                "Saldo DIRPF - Saldo Estudo = valor a ser restituído pela correção");

        return rowNum;
    }

    /**
     * Adiciona uma linha no quadro resumo de IR.
     * Ambas as células (label e valor) recebem o mesmo estilo (amarelo).
     * A coluna D recebe a explicação do que significa cada linha.
     */
    private int addSummaryRow(Sheet sheet, int rowNum, String label, BigDecimal valor,
            CellStyle style, CellStyle numberStyle, String explicacao) {
        Row row = sheet.createRow(rowNum);

        // Coluna B: Label
        Cell labelCell = row.createCell(1);
        labelCell.setCellValue(label);
        labelCell.setCellStyle(style);

        // Coluna C: Valor (com estilo amarelo também)
        Cell valorCell = row.createCell(2);
        valorCell.setCellValue(valor != null ? valor.doubleValue() : 0.0);
        valorCell.setCellStyle(style); // Mesmo estilo amarelo

        // Coluna D: Explicação
        if (explicacao != null && !explicacao.isEmpty()) {
            Cell explicacaoCell = row.createCell(3);
            explicacaoCell.setCellValue(explicacao);
        }

        return rowNum + 1;
    }

    /**
     * Calcula o total de todas as rubricas dos contracheques para um ano
     * específico.
     */
    private BigDecimal calcularTotalContracheques(ConsolidatedResponse consolidatedResponse, String ano) {
        BigDecimal total = BigDecimal.ZERO;

        for (ConsolidationRow rubrica : consolidatedResponse.getRubricas()) {
            // Soma simples para o ano
            BigDecimal somaAno = BigDecimal.ZERO;
            for (int mes = 1; mes <= 12; mes++) {
                String mesStr = String.format("%02d", mes);
                String referencia = ano + "-" + mesStr;
                somaAno = somaAno.add(rubrica.getValores().getOrDefault(referencia, BigDecimal.ZERO));
            }
            // Aplicar a regra NOV - FEV se necessário
            total = total.add(calcularTotalRubricaAno(rubrica, ano, somaAno));
        }

        log.debug("Total contracheques para ano {}: {}", ano, total);
        return total;
    }

    /**
     * Adiciona informações da pessoa no topo da planilha (linha 1).
     * Formato: B1 = "CPF [cpf_formatado]", D1 = "[Nome]"
     */
    private int addPersonInfo(Sheet sheet, Person person, int startRow, CellStyle style) {
        Row row1 = sheet.createRow(startRow);

        // Coluna B (índice 1): CPF formatado
        Cell cpfCell = row1.createCell(1);
        String cpfFormatado = formatCPF(person.getCpf());
        cpfCell.setCellValue("CPF " + cpfFormatado);
        cpfCell.setCellStyle(style);

        // Coluna D (índice 3): Nome
        Cell nomeCell = row1.createCell(3);
        nomeCell.setCellValue(person.getNome() != null ? person.getNome() : "");
        nomeCell.setCellStyle(style);

        return startRow + 1;
    }

    /**
     * Formata CPF no padrão: 000.000.000-00
     */
    private String formatCPF(String cpf) {
        if (cpf == null || cpf.trim().isEmpty()) {
            return "";
        }

        // Remove caracteres não numéricos
        String apenasNumeros = cpf.replaceAll("[^0-9]", "");

        // Formata se tiver 11 dígitos
        if (apenasNumeros.length() == 11) {
            return apenasNumeros.substring(0, 3) + "." +
                    apenasNumeros.substring(3, 6) + "." +
                    apenasNumeros.substring(6, 9) + "-" +
                    apenasNumeros.substring(9, 11);
        }

        // Se não tiver 11 dígitos, retorna como está
        return cpf;
    }

    /**
     * Adiciona cabeçalho da matriz (Código, Rubrica, meses, Total).
     */
    private int addMatrixHeader(Sheet sheet, String ano, int startRow, CellStyle style) {
        Row headerRow = sheet.createRow(startRow);
        int colNum = 0;

        headerRow.createCell(colNum++).setCellValue("Código");
        headerRow.createCell(colNum++).setCellValue("Rubrica");

        // Adicionar meses do ano (01 a 12) - mês 13 não é uma coluna separada
        for (int mes = 1; mes <= 12; mes++) {
            String mesStr = String.format("%02d", mes);
            headerRow.createCell(colNum++).setCellValue(ano + "/" + mesStr);
        }

        headerRow.createCell(colNum).setCellValue("Total");

        // Aplicar estilo ao cabeçalho
        for (int i = 0; i <= colNum; i++) {
            Cell cell = headerRow.getCell(i);
            if (cell != null) {
                cell.setCellStyle(style);
            }
        }

        return startRow + 1;
    }

    /**
     * Adiciona dados da matriz (rubricas e valores).
     */
    private int addMatrixData(Sheet sheet, ConsolidatedResponse consolidatedResponse, String ano,
            int startRow, CellStyle numberStyle, CellStyle defaultStyle) {
        int rowNum = startRow;

        // Filtrar valores apenas do ano atual
        for (ConsolidationRow rubrica : consolidatedResponse.getRubricas()) {

            // Verificar se a rubrica tem valores neste ano
            boolean temValorNoAno = false;
            for (int mes = 1; mes <= 12; mes++) {
                String mesStr = String.format("%02d", mes);
                String referencia = ano + "-" + mesStr;
                BigDecimal valor = rubrica.getValores().getOrDefault(referencia, BigDecimal.ZERO);
                if (valor.abs().compareTo(new BigDecimal("0.001")) > 0) {
                    temValorNoAno = true;
                    break;
                }
            }

            // Se não tiver valor em nenhum mês do ano, não exibir a linha
            if (!temValorNoAno) {
                continue;
            }

            Row row = sheet.createRow(rowNum++);
            int colNum = 0;

            // Código
            Cell codigoCell = row.createCell(colNum++);
            codigoCell.setCellValue(rubrica.getCodigo());
            codigoCell.setCellStyle(defaultStyle);

            // Descrição
            Cell descricaoCell = row.createCell(colNum++);
            descricaoCell.setCellValue(rubrica.getDescricao());
            descricaoCell.setCellStyle(defaultStyle);

            // Valores dos meses (01 a 12) - valores com ref "YYYY-13" já estão nos meses
            // corretos
            BigDecimal somaTotal = BigDecimal.ZERO;
            for (int mes = 1; mes <= 12; mes++) {
                String mesStr = String.format("%02d", mes);
                String referencia = ano + "-" + mesStr;
                BigDecimal valor = rubrica.getValores().getOrDefault(referencia, BigDecimal.ZERO);

                // Log para debug (apenas para rubricas problemáticas)
                if (rubrica.getCodigo().equals("4364") || rubrica.getCodigo().equals("4459")) {
                    if (valor.compareTo(BigDecimal.ZERO) > 0 || (mes == 11 || mes == 12)) {
                        log.debug("Excel - Rubrica {} - Mês {} (ref: {}): valor = {}",
                                rubrica.getCodigo(), mesStr, referencia, valor);
                    }
                }

                Cell valorCell = row.createCell(colNum++);
                // POI setCellValue aceita double
                valorCell.setCellValue(valor.doubleValue());
                valorCell.setCellStyle(numberStyle);

                somaTotal = somaTotal.add(valor);
            }

            // Calcular Total: se tiver valor em FEV (02) e NOV (11), faz NOV - FEV
            // Caso contrário, soma simples
            BigDecimal totalRubrica = calcularTotalRubricaAno(rubrica, ano, somaTotal);

            Cell totalCell = row.createCell(colNum);
            totalCell.setCellValue(totalRubrica.doubleValue());
            totalCell.setCellStyle(numberStyle);
        }

        return rowNum;
    }

    /**
     * Calcula o total de uma rubrica para um ano específico.
     * Para rubricas com referência YYYY-13 (identificadas por terem valores APENAS
     * em FEV e NOV, sem valores nos outros meses), o total é calculado como NOV -
     * FEV.
     * Para rubricas normais (com valores em outros meses), retorna a soma simples.
     */
    private BigDecimal calcularTotalRubricaAno(ConsolidationRow rubrica, String ano, BigDecimal somaSimples) {
        String refFev = ano + "-02";
        String refNov = ano + "-11";

        BigDecimal valorFev = rubrica.getValores().getOrDefault(refFev, BigDecimal.ZERO);
        BigDecimal valorNov = rubrica.getValores().getOrDefault(refNov, BigDecimal.ZERO);

        // Verificar se tem valores em outros meses além de FEV e NOV
        boolean temValorOutrosMeses = false;
        for (int mes = 1; mes <= 12; mes++) {
            if (mes == 2 || mes == 11) {
                continue; // Pular FEV e NOV
            }
            String mesStr = String.format("%02d", mes);
            String referencia = ano + "-" + mesStr;
            BigDecimal valor = rubrica.getValores().getOrDefault(referencia, BigDecimal.ZERO);
            if (valor.compareTo(BigDecimal.ZERO) > 0) {
                temValorOutrosMeses = true;
                break;
            }
        }

        // Se tiver valores em outros meses, usar soma simples (rubrica normal)
        if (temValorOutrosMeses) {
            return somaSimples;
        }

        // Se tiver valores APENAS em FEV e NOV (rubrica com referência YYYY-13)
        // O total deve ser NOV - FEV
        if (valorFev.compareTo(BigDecimal.ZERO) > 0 && valorNov.compareTo(BigDecimal.ZERO) > 0) {
            log.info("Rubrica {} ano {}: Total = NOV ({}) (regra YYYY-13 - último valor)",
                    rubrica.getCodigo(), ano, valorNov);
            return valorNov;
        }

        // Caso contrário (só tem valor em FEV ou só em NOV, ou zeros), retorna a soma
        // simples
        return somaSimples;
    }

    /**
     * Adiciona linha de totais mensais.
     */
    private int addMonthlyTotals(Sheet sheet, ConsolidatedResponse consolidatedResponse, String ano,
            int startRow, CellStyle totalStyle, CellStyle numberStyle) {
        Row totalRow = sheet.createRow(startRow);
        int colNum = 0;

        // Mesclar células "TOTAL" e "Mensal"
        Cell totalLabel1 = totalRow.createCell(colNum++);
        totalLabel1.setCellValue("TOTAL");
        totalLabel1.setCellStyle(totalStyle);

        Cell totalLabel2 = totalRow.createCell(colNum++);
        totalLabel2.setCellValue("Mensal");
        totalLabel2.setCellStyle(totalStyle);

        // Totais por mês (01 a 12) - valores com ref "YYYY-13" já estão nos meses
        // corretos
        for (int mes = 1; mes <= 12; mes++) {
            String mesStr = String.format("%02d", mes);
            String referencia = ano + "-" + mesStr;
            BigDecimal totalMes = consolidatedResponse.getTotaisMensais().getOrDefault(referencia, BigDecimal.ZERO);

            Cell totalMesCell = totalRow.createCell(colNum++);
            totalMesCell.setCellValue(totalMes.doubleValue());
            totalMesCell.setCellStyle(totalStyle);
        }

        // Total geral do ano: soma dos totais das rubricas (com regra NOV - FEV
        // aplicada)
        BigDecimal totalGeralAno = BigDecimal.ZERO;
        for (ConsolidationRow rubrica : consolidatedResponse.getRubricas()) {
            // Calcular a soma simples para este ano
            BigDecimal somaAno = BigDecimal.ZERO;
            for (int mes = 1; mes <= 12; mes++) {
                String mesStr = String.format("%02d", mes);
                String referencia = ano + "-" + mesStr;
                somaAno = somaAno.add(rubrica.getValores().getOrDefault(referencia, BigDecimal.ZERO));
            }
            // Aplicar a regra NOV - FEV se necessário
            totalGeralAno = totalGeralAno.add(calcularTotalRubricaAno(rubrica, ano, somaAno));
        }

        Cell totalGeralCell = totalRow.createCell(colNum);
        totalGeralCell.setCellValue(totalGeralAno.doubleValue());
        totalGeralCell.setCellStyle(totalStyle);

        return startRow + 1;
    }

    /**
     * Ajusta largura das colunas automaticamente.
     */
    private void autoSizeColumns(Sheet sheet, int numColumns) {
        // Ajustar colunas principais
        sheet.setColumnWidth(0, 4000); // Código
        sheet.setColumnWidth(1, 18300); // Rubrica (~500 pixels)

        // Ajustar colunas de valores
        for (int i = 2; i < 14; i++) { // 12 meses + total
            sheet.setColumnWidth(i, 3500);
        }
    }

    /**
     * Adiciona cabeçalho consolidado com todos os anos.
     */
    private int addConsolidatedHeader(Sheet sheet, TreeSet<String> anos, int startRow, CellStyle style) {
        Row headerRow = sheet.createRow(startRow);
        int colNum = 0;

        headerRow.createCell(colNum++).setCellValue("Código");
        headerRow.createCell(colNum++).setCellValue("Rubrica");

        // Adicionar meses de todos os anos em ordem (01 a 12) - mês 13 não é uma coluna
        // separada
        for (String ano : anos) {
            for (int mes = 1; mes <= 12; mes++) {
                String mesStr = String.format("%02d", mes);
                headerRow.createCell(colNum++).setCellValue(ano + "/" + mesStr);
            }
        }

        headerRow.createCell(colNum).setCellValue("Total");

        // Aplicar estilo ao cabeçalho
        for (int i = 0; i <= colNum; i++) {
            Cell cell = headerRow.getCell(i);
            if (cell != null) {
                cell.setCellStyle(style);
            }
        }

        return startRow + 1;
    }

    /**
     * Adiciona dados consolidados (todas as rubricas de todos os anos).
     */
    private int addConsolidatedData(Sheet sheet, ConsolidatedResponse consolidatedResponse,
            TreeSet<String> anos, int startRow,
            CellStyle numberStyle, CellStyle defaultStyle) {
        int rowNum = startRow;

        for (ConsolidationRow rubrica : consolidatedResponse.getRubricas()) {

            // Verificar se a rubrica tem valores em algum dos anos
            boolean temValorGeral = false;
            for (String ano : anos) {
                for (int mes = 1; mes <= 12; mes++) {
                    String mesStr = String.format("%02d", mes);
                    String referencia = ano + "-" + mesStr;
                    BigDecimal valor = rubrica.getValores().getOrDefault(referencia, BigDecimal.ZERO);
                    if (valor.abs().compareTo(new BigDecimal("0.001")) > 0) {
                        temValorGeral = true;
                        break;
                    }
                }
                if (temValorGeral)
                    break;
            }

            // Se não tiver valor em nenhum ano, não exibir a linha
            if (!temValorGeral) {
                continue;
            }

            Row row = sheet.createRow(rowNum++);
            int colNum = 0;

            // Código
            Cell codigoCell = row.createCell(colNum++);
            codigoCell.setCellValue(rubrica.getCodigo());
            codigoCell.setCellStyle(defaultStyle);

            // Descrição
            Cell descricaoCell = row.createCell(colNum++);
            descricaoCell.setCellValue(rubrica.getDescricao());
            descricaoCell.setCellStyle(defaultStyle);

            // Valores de todos os meses de todos os anos (01 a 12) - valores com ref
            // "YYYY-13" já estão nos meses corretos
            BigDecimal totalGeralRubrica = BigDecimal.ZERO;
            for (String ano : anos) {
                BigDecimal somaAno = BigDecimal.ZERO;
                for (int mes = 1; mes <= 12; mes++) {
                    String mesStr = String.format("%02d", mes);
                    String referencia = ano + "-" + mesStr;
                    BigDecimal valor = rubrica.getValores().getOrDefault(referencia, BigDecimal.ZERO);

                    Cell valorCell = row.createCell(colNum++);
                    valorCell.setCellValue(valor.doubleValue());
                    valorCell.setCellStyle(numberStyle);

                    somaAno = somaAno.add(valor);
                }

                // Calcular total do ano: se tiver valor em FEV e NOV, faz NOV - FEV
                BigDecimal totalAno = calcularTotalRubricaAno(rubrica, ano, somaAno);
                totalGeralRubrica = totalGeralRubrica.add(totalAno);
            }

            // Total geral da rubrica (soma dos totais de cada ano, com regra NOV - FEV
            // aplicada)
            Cell totalCell = row.createCell(colNum);
            totalCell.setCellValue(totalGeralRubrica.doubleValue());
            totalCell.setCellStyle(numberStyle);
        }

        return rowNum;
    }

    /**
     * Adiciona linha de totais consolidados.
     */
    private int addConsolidatedTotals(Sheet sheet, ConsolidatedResponse consolidatedResponse,
            TreeSet<String> anos, int startRow,
            CellStyle totalStyle, CellStyle numberStyle) {
        Row totalRow = sheet.createRow(startRow);
        int colNum = 0;

        // "TOTAL"
        Cell totalLabel1 = totalRow.createCell(colNum++);
        totalLabel1.setCellValue("TOTAL");
        totalLabel1.setCellStyle(totalStyle);

        // "Mensal"
        Cell totalLabel2 = totalRow.createCell(colNum++);
        totalLabel2.setCellValue("Mensal");
        totalLabel2.setCellStyle(totalStyle);

        // Totais por mês de todos os anos (01 a 12) - valores com ref "YYYY-13" já
        // estão nos meses corretos
        for (String ano : anos) {
            for (int mes = 1; mes <= 12; mes++) {
                String mesStr = String.format("%02d", mes);
                String referencia = ano + "-" + mesStr;
                BigDecimal totalMes = consolidatedResponse.getTotaisMensais().getOrDefault(referencia, BigDecimal.ZERO);

                Cell totalMesCell = totalRow.createCell(colNum++);
                totalMesCell.setCellValue(totalMes.doubleValue());
                totalMesCell.setCellStyle(totalStyle);
            }
        }

        // Total geral consolidado: soma dos totais das rubricas (com regra NOV - FEV
        // aplicada)
        BigDecimal totalGeralConsolidado = BigDecimal.ZERO;
        for (ConsolidationRow rubrica : consolidatedResponse.getRubricas()) {
            BigDecimal totalRubrica = BigDecimal.ZERO;
            for (String ano : anos) {
                // Calcular a soma simples para este ano
                BigDecimal somaAno = BigDecimal.ZERO;
                for (int mes = 1; mes <= 12; mes++) {
                    String mesStr = String.format("%02d", mes);
                    String referencia = ano + "-" + mesStr;
                    somaAno = somaAno.add(rubrica.getValores().getOrDefault(referencia, BigDecimal.ZERO));
                }
                // Aplicar a regra NOV - FEV se necessário
                totalRubrica = totalRubrica.add(calcularTotalRubricaAno(rubrica, ano, somaAno));
            }
            totalGeralConsolidado = totalGeralConsolidado.add(totalRubrica);
        }

        Cell totalGeralCell = totalRow.createCell(colNum);
        totalGeralCell.setCellValue(totalGeralConsolidado.doubleValue());
        totalGeralCell.setCellStyle(totalStyle);

        return startRow + 1;
    }

    /**
     * Ajusta largura das colunas da aba consolidada.
     */
    private void autoSizeConsolidatedColumns(Sheet sheet, int numAnos) {
        // Colunas principais
        sheet.setColumnWidth(0, 4000); // Código
        sheet.setColumnWidth(1, 12000); // Rubrica

        // Colunas de valores (12 meses por ano)
        int totalColunas = numAnos * 12 + 1; // meses + total
        for (int i = 2; i < totalColunas + 1; i++) {
            sheet.setColumnWidth(i, 3500);
        }
    }

    /**
     * Cria estilo para cabeçalho.
     */
    private CellStyle createHeaderStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        font.setFontName("Arial");
        font.setFontHeightInPoints((short) 10);
        style.setFont(font);
        style.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setAlignment(HorizontalAlignment.CENTER);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        return style;
    }

    /**
     * Cria estilo para números.
     */
    private CellStyle createNumberStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        DataFormat format = workbook.createDataFormat();
        style.setDataFormat(format.getFormat("#,##0.00"));
        style.setAlignment(HorizontalAlignment.RIGHT);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        Font font = workbook.createFont();
        font.setFontName("Arial");
        font.setFontHeightInPoints((short) 10);
        style.setFont(font);
        return style;
    }

    /**
     * Cria estilo padrão.
     */
    private CellStyle createDefaultStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setFontName("Arial");
        font.setFontHeightInPoints((short) 10);
        style.setFont(font);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        return style;
    }

    /**
     * Cria estilo para totais.
     */
    private CellStyle createTotalStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        font.setFontName("Arial");
        font.setFontHeightInPoints((short) 10);
        style.setFont(font);
        style.setFillForegroundColor(IndexedColors.YELLOW.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setAlignment(HorizontalAlignment.RIGHT);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        style.setBorderTop(BorderStyle.DOUBLE);
        style.setBorderBottom(BorderStyle.DOUBLE);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        DataFormat format = workbook.createDataFormat();
        style.setDataFormat(format.getFormat("#,##0.00"));
        return style;
    }

    /**
     * Cria estilo para informações da pessoa (sem negrito).
     */
    private CellStyle createInfoStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setFontName("Arial");
        font.setFontHeightInPoints((short) 10);
        style.setFont(font);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        return style;
    }

    // =============================================
    // NOVOS MÉTODOS: BUSCA DE DECLARAÇÕES IRPF
    // =============================================

    /**
     * Busca declarações IRPF completas (IrpfDeclaracaoData) para a pessoa.
     * Retorna mapa: anoCalendario (ex: "2021") -> IrpfDeclaracaoData
     */
    private Mono<Map<String, IrpfDeclaracaoData>> buscarIrpfDeclaracoes(Person person) {
        log.info("Buscando declarações IRPF para pessoa: {} ({})", person.getNome(), person.getCpf());

        return documentRepository.findByTenantIdAndCpf(person.getTenantId(), person.getCpf())
                .filter(doc -> doc.getTipo() == DocumentType.INCOME_TAX && doc.getIrpfData() != null)
                .collectList()
                .map(docs -> {
                    Map<String, IrpfDeclaracaoData> map = new HashMap<>();
                    for (var doc : docs) {
                        IrpfDeclaracaoData data = doc.getIrpfData();
                        String anoCalendario = data.getAnoCalendario();
                        if (anoCalendario != null && !anoCalendario.isBlank()) {
                            map.put(anoCalendario.trim(), data);
                            log.debug("Declaração IRPF encontrada: exercício={}, anoCalendario={}",
                                    data.getExercicio(), anoCalendario);
                        }
                    }
                    log.info("Declarações IRPF mapeadas para anos-calendário: {}", map.keySet());
                    return map;
                })
                .onErrorResume(e -> {
                    log.warn("Erro ao buscar declarações IRPF (continuando sem dados): {}", e.getMessage());
                    return Mono.just(Collections.emptyMap());
                });
    }

    // =============================================
    // BLOCOS DE SIMULAÇÃO IRPF (layout referência)
    // =============================================

    /**
     * Bloco 1 — espelho exato da declaração entregue no modelo Simplificado (valores do PDF/RESUMO).
     */
    private int addBlocoConformeDeclaracaoSimplificada(
            Sheet sheet, Person person, IrpfDeclaracaoData data, int startRow,
            CellStyle titleStyle, CellStyle bannerSubtitleStyle, CellStyle sectionHeaderStyle,
            CellStyle valueStyle, CellStyle subLabelStyle, CellStyle totalStyle,
            CellStyle totalLabelStyle, CellStyle aliquotaStyle) {

        int row = startRow;
        sheet.createRow(row++);

        String titulo = String.format(
                "CONFORME DECLARAÇÃO ENTREGUE - EXERCÍCIO %s / ANO-CALENDÁRIO %s",
                data.getExercicio() != null ? data.getExercicio() : "?",
                data.getAnoCalendario() != null ? data.getAnoCalendario() : "?");
        row = addSimTituloBloco(sheet, row, titulo, titleStyle);
        row = addSimNomeCpfLinha(sheet, row, person, bannerSubtitleStyle);
        row = addSimTituloBloco(sheet, row,
                "DECLARAÇÃO DE AJUSTE ANUAL - TRIBUTAÇÃO UTILIZANDO O DESCONTO SIMPLIFICADO", bannerSubtitleStyle);
        row = addSimTituloBloco(sheet, row, "RENDIMENTOS TRIBUTÁVEIS E DESCONTO SIMPLIFICADO", sectionHeaderStyle);

        row = addSimRendimentosTributaveisDetalhe(sheet, row, data, subLabelStyle, valueStyle);
        row = addSimDestaqueRow(sheet, row, "TOTAL DE RENDIMENTOS TRIBUTÁVEIS",
                nvl(data.getRendimentosTributaveisTotal()), totalStyle, totalLabelStyle);

        row = addSimDetalhe(sheet, row, "Desconto Simplificado", data.getDescontoSimplificado(), subLabelStyle, valueStyle);
        row = addSimDetalhe(sheet, row, "Base de cálculo do Imposto", data.getBaseCalculoImposto(), subLabelStyle, valueStyle);
        row = addSimDetalhe(sheet, row, "Imposto devido", data.getImpostoDevido(), subLabelStyle, valueStyle);
        row = addSimDetalhe(sheet, row, "Imposto devido RRA", data.getImpostoSobreRRA(), subLabelStyle, valueStyle);
        row = addSimAliquotaDeclaracaoRow(sheet, row, "Aliquota efetiva (%)", data.getAliquotaEfetiva(),
                subLabelStyle, valueStyle);
        row = addSimDestaqueRow(sheet, row, "Total do imposto devido", data.getTotalImpostoDevido(),
                totalStyle, totalLabelStyle);

        sheet.createRow(row++);
        row = addSimTituloBloco(sheet, row, "IMPOSTO PAGO", sectionHeaderStyle);
        row = addSimImpostoPagoLinhas(sheet, row, data, subLabelStyle, valueStyle);
        row = addSimDestaqueRow(sheet, row, "Total do imposto pago", data.getImpostoPagoTotal(),
                totalStyle, totalLabelStyle);

        row = addSimDestaqueRow(sheet, row, "IMPOSTO A RESTITUIR", nvl(data.getImpostoRestituir()),
                totalStyle, totalLabelStyle);
        row = addSimDestaqueRow(sheet, row, "SALDO IMPOSTO A PAGAR", nvl(data.getSaldoImpostoPagar()),
                totalStyle, totalLabelStyle);

        sheet.createRow(row++);
        return row;
    }

    /**
     * Bloco 1 — espelho exato da declaração entregue no modelo Completo (deduções legais, valores do PDF/RESUMO).
     */
    private int addBlocoConformeDeclaracaoCompleta(
            Sheet sheet, Person person, IrpfDeclaracaoData data, int startRow,
            CellStyle titleStyle, CellStyle bannerSubtitleStyle, CellStyle sectionHeaderStyle,
            CellStyle valueStyle, CellStyle subLabelStyle, CellStyle totalStyle,
            CellStyle totalLabelStyle, CellStyle aliquotaStyle) {

        ExcelIrpfDeducoesResumoDTO deducoes = deducoesResumoHelper.montarConformeDeclaracao(data);

        int row = startRow;
        sheet.createRow(row++);

        String titulo = String.format(
                "CONFORME DECLARAÇÃO ENTREGUE - EXERCÍCIO %s / ANO-CALENDÁRIO %s",
                data.getExercicio() != null ? data.getExercicio() : "?",
                data.getAnoCalendario() != null ? data.getAnoCalendario() : "?");
        row = addSimTituloBloco(sheet, row, titulo, titleStyle);
        row = addSimNomeCpfLinha(sheet, row, person, bannerSubtitleStyle);
        row = addSimTituloBloco(sheet, row,
                "DECLARAÇÃO DE AJUSTE ANUAL - TRIBUTAÇÃO UTILIZANDO AS DEDUÇÕES LEGAIS",
                bannerSubtitleStyle);

        row = addSimTituloBloco(sheet, row, "RENDIMENTOS TRIBUTÁVEIS", sectionHeaderStyle);
        row = addSimRendimentosTributaveisDetalhe(sheet, row, data, subLabelStyle, valueStyle);
        row = addSimDestaqueRow(sheet, row, "TOTAL DE RENDIMENTOS TRIBUTÁVEIS",
                nvl(data.getRendimentosTributaveisTotal()), totalStyle, totalLabelStyle);

        sheet.createRow(row++);
        row = addSimSecaoDeducoesResumo(sheet, row, deducoes, sectionHeaderStyle, subLabelStyle,
                valueStyle, totalStyle, totalLabelStyle, null, true);

        sheet.createRow(row++);
        row = addSimTituloBloco(sheet, row, "IMPOSTO DEVIDO", sectionHeaderStyle);
        row = addSimDetalhe(sheet, row, "Base de cálculo do Imposto", data.getBaseCalculoImposto(), subLabelStyle, valueStyle);
        row = addSimDetalhe(sheet, row, "Imposto devido", data.getImpostoDevido(), subLabelStyle, valueStyle);
        row = addSimDetalhe(sheet, row, "Dedução de incentivo", data.getDeducaoIncentivo(), subLabelStyle, valueStyle);
        row = addSimDetalhe(sheet, row, "Imposto devido I", data.getImpostoDevidoI(), subLabelStyle, valueStyle);
        if (deveExibirImpostoDevidoIIEspelho(data)) {
            row = addSimDetalhe(sheet, row, "Imposto devido II", data.getImpostoDevidoII(), subLabelStyle, valueStyle);
        }
        row = addSimDetalhe(sheet, row, "Imposto devido RRA", data.getImpostoSobreRRA(), subLabelStyle, valueStyle);
        row = addSimAliquotaDeclaracaoRow(sheet, row, "Aliquota efetiva (%)", data.getAliquotaEfetiva(),
                subLabelStyle, valueStyle);
        row = addSimDestaqueRow(sheet, row, "Total do imposto devido", data.getTotalImpostoDevido(),
                totalStyle, totalLabelStyle);

        sheet.createRow(row++);
        row = addSimTituloBloco(sheet, row, "IMPOSTO PAGO", sectionHeaderStyle);
        row = addSimImpostoPagoLinhas(sheet, row, data, subLabelStyle, valueStyle);
        row = addSimDestaqueRow(sheet, row, "Total do imposto pago", data.getImpostoPagoTotal(),
                totalStyle, totalLabelStyle);

        row = addSimDestaqueRow(sheet, row, "IMPOSTO A RESTITUIR", nvl(data.getImpostoRestituir()),
                totalStyle, totalLabelStyle);
        row = addSimDestaqueRow(sheet, row, "SALDO IMPOSTO A PAGAR", nvl(data.getSaldoImpostoPagar()),
                totalStyle, totalLabelStyle);

        sheet.createRow(row++);
        return row;
    }

    /**
     * Bloco 2 — simulação modelo Completo com previdência complementar da planilha de contracheques.
     */
    private int addBlocoSimulacaoCompletaPlanilha(
            Sheet sheet, Person person, String ano, IrpfDeclaracaoData data,
            BigDecimal prevComplPlanilha,
            Map<String, java.util.List<br.com.verticelabs.pdfprocessor.domain.model.IrTabelaTributacao>> tabelasTributacao,
            Map<String, IrParametrosAnuais> parametrosTributacao, int startRow,
            CellStyle titleStyle, CellStyle bannerSubtitleStyle, CellStyle sectionHeaderStyle,
            CellStyle valueStyle, CellStyle subLabelStyle, CellStyle totalStyle,
            CellStyle totalLabelStyle, CellStyle aliquotaStyle, CellStyle highlightGreenStyle) {

        java.util.List<br.com.verticelabs.pdfprocessor.domain.model.IrTabelaTributacao> faixasTabela =
                tabelasTributacao.getOrDefault(ano, Collections.emptyList());
        IrParametrosAnuais paramsAno = parametrosTributacao != null ? parametrosTributacao.get(ano) : null;

        SimuladorIrpfRequest request = excelIrpfSimulacaoMapper.fromDeclaracao(
                data, prevComplPlanilha, true);
        request.setInssDomesticoComoCreditoImposto(true);

        SimuladorIrpfResponse response = simuladorMotorService.simular(request, faixasTabela, paramsAno);
        ModeloTributacaoResultDTO modelo = response.getModeloCompleto();
        ExcelIrpfDeducoesResumoDTO deducoes = deducoesResumoHelper.montar(
                data, request, prevComplPlanilha, paramsAno);

        int row = startRow;
        sheet.createRow(row++);

        String titulo = String.format(
                "SIMULAÇÃO IRPF — EXERCÍCIO %s / ANO-CALENDÁRIO %s — COM APROVEITAMENTO DAS CONTRIBUIÇÕES EXTRAS",
                data.getExercicio() != null ? data.getExercicio() : "?",
                data.getAnoCalendario() != null ? data.getAnoCalendario() : "?");
        row = addSimTituloBloco(sheet, row, titulo, titleStyle);
        row = addSimNomeCpfLinha(sheet, row, person, bannerSubtitleStyle);

        // Bloco 2 é sempre modelo Completo (deduções legais), independente da declaração entregue
        row = addSimTituloBloco(sheet, row,
                "DECLARAÇÃO DE AJUSTE ANUAL - TRIBUTAÇÃO UTILIZANDO AS DEDUÇÕES LEGAIS",
                bannerSubtitleStyle);

        row = addSimTituloBloco(sheet, row, "RENDIMENTOS TRIBUTÁVEIS", sectionHeaderStyle);
        row = addSimRendimentosTributaveisDetalhe(sheet, row, data, subLabelStyle, valueStyle);
        row = addSimDestaqueRow(sheet, row, "TOTAL DE RENDIMENTOS TRIBUTÁVEIS",
                nvl(data.getRendimentosTributaveisTotal()), totalStyle, totalLabelStyle);

        sheet.createRow(row++);
        row = addSimSecaoDeducoesResumo(sheet, row, deducoes, sectionHeaderStyle, subLabelStyle,
                valueStyle, totalStyle, totalLabelStyle, highlightGreenStyle, false);

        sheet.createRow(row++);
        row = addSimTituloBloco(sheet, row, "IMPOSTO DEVIDO", sectionHeaderStyle);
        row = addSimDetalhe(sheet, row, "Base de cálculo do imposto", modelo.getBaseCalculo(), subLabelStyle, valueStyle);
        row = addSimDetalhe(sheet, row, "Imposto devido", modelo.getImpostoDevidoFinal(), subLabelStyle, valueStyle);
        row = addSimDetalhe(sheet, row, "Dedução de incentivo", modelo.getDeducoesEspeciais(), subLabelStyle, valueStyle);
        row = addSimDetalhe(sheet, row, "Imposto devido I", modelo.getImpostoDevidoFinal(), subLabelStyle, valueStyle);

        if (nvl(modelo.getCreditoInssDomestico()).compareTo(BigDecimal.ZERO) > 0) {
            row = addSimDetalhe(sheet, row, "Contribuição Prev. Empregador Doméstico",
                    modelo.getCreditoInssDomestico(), subLabelStyle, valueStyle);
        }
        row = addSimDetalhe(sheet, row, "Imposto devido II",
                resolverImpostoDevidoIIExibicao(modelo), subLabelStyle, valueStyle);
        row = addSimDetalhe(sheet, row, "Imposto devido RRA", nvl(data.getImpostoSobreRRA()), subLabelStyle, valueStyle);
        row = addSimAliquotaDeclaracaoRow(sheet, row, "Aliquota efetiva (%)", modelo.getAliquotaEfetiva(),
                subLabelStyle, valueStyle);

        BigDecimal impostoProgressivoTotal = modelo.getImpostoDevidoII() != null
                ? modelo.getImpostoDevidoII()
                : modelo.getImpostoDevidoFinal();
        BigDecimal totalDevido = modelo.getResumo() != null && modelo.getResumo().getTotalImpostoDevido() != null
                ? modelo.getResumo().getTotalImpostoDevido()
                : nvl(impostoProgressivoTotal).add(nvl(data.getImpostoSobreRRA()));
        row = addSimDestaqueRow(sheet, row, "Total do imposto devido", totalDevido, totalStyle, totalLabelStyle);

        sheet.createRow(row++);
        row = addSimTituloBloco(sheet, row,
                "IMPOSTO PAGO — ESTUDO COM APROVEITAMENTO DAS CONTRIBUIÇÕES EXTRAS", sectionHeaderStyle);
        row = addSimImpostoPagoLinhas(sheet, row, data, subLabelStyle, valueStyle);
        BigDecimal totalPago = calcularTotalImpostoPagoDeclaracao(data);
        row = addSimDestaqueRow(sheet, row, "Total do imposto pago", totalPago, totalStyle, totalLabelStyle);

        sheet.createRow(row++);
        row = addSimTituloBloco(sheet, row,
                "RESULTADO — ESTUDO COM APROVEITAMENTO DAS CONTRIBUIÇÕES EXTRAS", sectionHeaderStyle);

        ExcelResumoGeralHelper.ResultadoBloco2Simulacao resultado =
                resumoGeralHelper.calcularResultadoPositivoDeTotais(totalPago, totalDevido);
        row = addSimDestaqueRow(sheet, row, "IMPOSTO A RESTITUIR", resultado.restituir(),
                totalStyle, totalLabelStyle);
        row = addSimDestaqueRow(sheet, row, "SALDO DE IMPOSTO A PAGAR", resultado.saldoPagar(),
                totalStyle, totalLabelStyle);

        sheet.createRow(row++);
        return row;
    }

    /** Imposto devido II só tem valor quando há crédito INSS doméstico (RESUMO IRPF). */
    private BigDecimal resolverImpostoDevidoIIExibicao(ModeloTributacaoResultDTO modelo) {
        if (modelo != null && modelo.getImpostoDevidoII() != null) {
            return modelo.getImpostoDevidoII();
        }
        return BigDecimal.ZERO;
    }

    // =============================================
    // ABA RESUMO GERAL
    // =============================================

    /** Largura colunas A–G (~105 px; 15 caracteres, igual ao arquivo de referência). */
    private static final int RESUMO_WIDTH_COL_AG = 15 * 256;
    /** Largura coluna H (~280 px; 40 caracteres, igual ao arquivo de referência). */
    private static final int RESUMO_WIDTH_COL_H = 40 * 256;
    private static final int RESUMO_LAST_COL = 7;
    /** Coluna F (0-based 5) — fim do merge do texto do economista no rodapé. */
    private static final int RESUMO_FOOTER_ECON_COL_FIM = 5;
    private static final ZoneId RESUMO_FUSO_HORARIO = ZoneId.of("America/Sao_Paulo");
    private static final DateTimeFormatter RESUMO_DATA_HORA_GERACAO =
            DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private void addResumoGeralSheet(
            Workbook workbook, Person person, List<ExcelResumoGeralLinhaDTO> linhas,
            br.com.verticelabs.pdfprocessor.application.empresas.EmpresaHonorariosResolver.HonorariosConfig honorariosConfig,
            CellStyle headerStyle, CellStyle numberStyle, CellStyle defaultStyle, CellStyle totalStyle) {

        Sheet sheet = workbook.createSheet("Resumo Geral");
        CellStyle resumoLabelStyle = createResumoLabelStyle(workbook);
        CellStyle resumoHeaderTableStyle = createResumoHeaderTableStyle(workbook);
        CellStyle resumoPercentStyle = createResumoPercentStyle(workbook);
        CellStyle resumoHonorariosStyle = createResumoHonorariosStyle(workbook);
        CellStyle resumoDateStyle = createResumoDateStyle(workbook);
        CellStyle resumoTotalLabelStyle = createResumoTotalLabelStyle(workbook);
        CellStyle resumoZeroDashStyle = createResumoZeroDashStyle(workbook);
        CellStyle resumoEmptyStyle = createResumoEmptyStyle(workbook);
        CellStyle resumoFooterDateTimeStyle = createResumoFooterDateTimeStyle(workbook);

        int row = 0;
        final int topRow = 0;

        // Linhas 1–4: cabeçalho (merge A:F; G e H reservados para borda direita)
        Row r1 = sheet.createRow(row++);
        Cell nomeCell = r1.createCell(0);
        String nome = person.getNome() != null ? person.getNome().toUpperCase() : "";
        nomeCell.setCellValue("NOME: " + nome);
        nomeCell.setCellStyle(resumoLabelStyle);
        sheet.addMergedRegion(new CellRangeAddress(0, 0, 0, 5));

        Row r2 = sheet.createRow(row++);
        Cell cpfCell = r2.createCell(0);
        cpfCell.setCellValue("CPF  " + formatCPF(person.getCpf()));
        cpfCell.setCellStyle(resumoLabelStyle);
        sheet.addMergedRegion(new CellRangeAddress(1, 1, 0, 5));

        Row r3 = sheet.createRow(row++);
        Cell selicCell = r3.createCell(0);
        selicCell.setCellValue("Atualização : SELIC RECEITA FEDERAL");
        selicCell.setCellStyle(resumoLabelStyle);
        sheet.addMergedRegion(new CellRangeAddress(2, 2, 0, 5));

        Row r4 = sheet.createRow(row++);
        Cell datasTitulo = r4.createCell(0);
        datasTitulo.setCellValue("Datas para atualização");
        datasTitulo.setCellStyle(resumoLabelStyle);
        sheet.addMergedRegion(new CellRangeAddress(3, 3, 0, 5));

        int dateGridStartRow = row;
        row = addResumoGeralGridDatas(sheet, row, resumoLabelStyle, resumoDateStyle);
        int dateGridEndRow = row - 1;

        // Cabeçalho da tabela (linha 9 no referencial)
        int tableHeaderRow = row;
        Row headerRow = sheet.createRow(row++);
        headerRow.setHeightInPoints(64.5f);
        String[] headers = {
                "Calendário",
                "Valores Restituidos / Pagos",
                "Valor Devido  e ou a Restituir",
                "Valor Principal a ser Restituido pela PGFN ao Contribuinte",
                "SELIC Acumulada - RFB",
                "Valor Correção R$",
                "Principal + Correção Valores a Receber",
                "Observações"
        };
        for (int i = 0; i < headers.length; i++) {
            Cell c = headerRow.createCell(i);
            c.setCellValue(headers[i]);
            c.setCellStyle(resumoHeaderTableStyle);
        }

        int firstDataRow = row;
        for (ExcelResumoGeralLinhaDTO linha : linhas) {
            Row dataRow = sheet.createRow(row++);
            setResumoAnoCell(dataRow, 0, linha.getAnoCalendario(), defaultStyle);
            setResumoMoneyCell(dataRow, 1, linha.getValorDeclaracao(), numberStyle);
            setResumoMoneyCell(dataRow, 2, linha.getValorSimulacao(), numberStyle);
            setResumoPrincipalCell(dataRow, 3, linha.getPrincipal(), numberStyle, resumoZeroDashStyle);
            setResumoPercentCell(dataRow, 4, linha.getPrincipal(), linha.getSelicAcumulada(), resumoPercentStyle);
            setResumoCorrecaoCell(dataRow, 5, linha.getPrincipal(), linha.getValorCorrecao(), numberStyle, resumoZeroDashStyle);
            setResumoCorrecaoCell(dataRow, 6, linha.getPrincipal(), linha.getPrincipalMaisCorrecao(), numberStyle, resumoZeroDashStyle);
            Cell obsCell = dataRow.createCell(7);
            obsCell.setCellValue(linha.getObservacao() != null ? linha.getObservacao() : "");
            obsCell.setCellStyle(defaultStyle);
        }
        int lastDataRow = row - 1;

        ExcelResumoGeralHelper.TotaisResumoGeral totais = resumoGeralHelper.calcularTotais(
                linhas, honorariosConfig.percentualFracao());
        int firstTotalRow = row;

        Row totalRow = sheet.createRow(row++);
        totalRow.setHeightInPoints(16.5f);
        Cell totalLabel = totalRow.createCell(0);
        totalLabel.setCellValue("Total da diferença R$");
        totalLabel.setCellStyle(resumoTotalLabelStyle);
        setResumoMoneyCell(totalRow, 3, totais.totalPrincipal(), totalStyle);
        setResumoMoneyCell(totalRow, 5, totais.totalCorrecao(), totalStyle);
        Cell totalGCell = totalRow.createCell(6);
        totalGCell.setCellValue(totais.totalPrincipalMaisCorrecao().doubleValue());
        totalGCell.setCellStyle(totalStyle);

        Row honorRow = sheet.createRow(row++);
        honorRow.setHeightInPoints(16.5f);
        Cell honorLabel = honorRow.createCell(0);
        honorLabel.setCellValue(honorariosConfig.formatLabelHonorarios());
        honorLabel.setCellStyle(resumoTotalLabelStyle);
        Cell honorCell = honorRow.createCell(6);
        honorCell.setCellValue(totais.honorarios().doubleValue());
        honorCell.setCellStyle(resumoHonorariosStyle);

        int receberRowIdx = row;
        Row receberRow = sheet.createRow(row++);
        receberRow.setHeightInPoints(16.5f);
        Cell receberLabel = receberRow.createCell(0);
        receberLabel.setCellValue("Valor a Receber");
        receberLabel.setCellStyle(resumoTotalLabelStyle);
        Cell receberCell = receberRow.createCell(6);
        receberCell.setCellValue(totais.valorReceber().doubleValue());
        Font boldFont = workbook.createFont();
        boldFont.setBold(true);
        boldFont.setFontName("Arial");
        boldFont.setFontHeightInPoints((short) 10);
        CellStyle receberStyle = workbook.createCellStyle();
        receberStyle.cloneStyleFrom(numberStyle);
        receberStyle.setFont(boldFont);
        receberCell.setCellStyle(receberStyle);

        // Bordas externas espessas + internas finas (bloco principal A1:H até Valor a Receber)
        applyResumoGeralBordas(sheet, workbook, resumoEmptyStyle,
                topRow, receberRowIdx, dateGridStartRow, dateGridEndRow,
                tableHeaderRow, firstDataRow, lastDataRow, firstTotalRow);

        // Rodapé (fora do bloco principal)
        row++;
        int footerRow1 = row++;
        int footerRow2 = row;

        Row respRow = sheet.createRow(footerRow1);
        Cell respLabelCell = respRow.createCell(0);
        respLabelCell.setCellValue(ExcelResumoGeralHelper.RODAPE_LABEL);
        respLabelCell.setCellStyle(resumoLabelStyle);
        Cell respNameCell = respRow.createCell(1);
        respNameCell.setCellValue(ExcelResumoGeralHelper.RODAPE_NOME);
        respNameCell.setCellStyle(defaultStyle);
        sheet.addMergedRegion(new CellRangeAddress(footerRow1, footerRow1, 1, RESUMO_LAST_COL));

        Row econRow = sheet.createRow(footerRow2);
        econRow.setHeightInPoints(15.75f);
        Cell econCell = econRow.createCell(1);
        econCell.setCellValue(ExcelResumoGeralHelper.RODAPE_ECONOMISTA);
        econCell.setCellStyle(defaultStyle);
        sheet.addMergedRegion(new CellRangeAddress(footerRow1, footerRow2, 0, 0));
        sheet.addMergedRegion(new CellRangeAddress(footerRow2, footerRow2, 1, RESUMO_FOOTER_ECON_COL_FIM));

        Cell geracaoCell = econRow.createCell(RESUMO_LAST_COL);
        geracaoCell.setCellValue(LocalDateTime.now(RESUMO_FUSO_HORARIO).format(RESUMO_DATA_HORA_GERACAO));
        geracaoCell.setCellStyle(resumoFooterDateTimeStyle);

        applyResumoGeralBordasRodape(sheet, workbook, resumoEmptyStyle, footerRow1, footerRow2);

        for (int col = 0; col < 7; col++) {
            sheet.setColumnWidth(col, RESUMO_WIDTH_COL_AG);
        }
        sheet.setColumnWidth(RESUMO_LAST_COL, RESUMO_WIDTH_COL_H);

        if (workbook instanceof XSSFWorkbook xssfWorkbook && sheet instanceof XSSFSheet xssfSheet) {
            resumoGeralLogoHelper.inserirLogoNaCelula(
                    xssfSheet, xssfWorkbook, RESUMO_WIDTH_COL_AG, RESUMO_WIDTH_COL_H);
        }

        log.debug("Resumo Geral: {} linhas de dados (linha {} a {})", linhas.size(), firstDataRow + 1, firstTotalRow);
    }

    /**
     * Aplica bordas externas espessas (MEDIUM) e internas finas na área principal A:H.
     */
    private void applyResumoGeralBordas(
            Sheet sheet, Workbook workbook, CellStyle emptyStyle,
            int topRow, int bottomRow,
            int dateGridStartRow, int dateGridEndRow,
            int tableHeaderRow, int firstDataRow, int lastDataRow, int firstTotalRow) {

        BorderStyle outer = BorderStyle.MEDIUM;
        BorderStyle inner = BorderStyle.THIN;
        BorderStyle separator = BorderStyle.DOUBLE;

        for (int r = topRow; r <= bottomRow; r++) {
            Row sheetRow = sheet.getRow(r);
            if (sheetRow == null) {
                sheetRow = sheet.createRow(r);
            }
            for (int c = 0; c <= RESUMO_LAST_COL; c++) {
                if (resumoGeralLogoHelper.isCelulaAreaLogo(r, c)) {
                    continue;
                }
                Cell cell = sheetRow.getCell(c);
                if (cell == null) {
                    cell = sheetRow.createCell(c);
                    cell.setCellStyle(emptyStyle);
                }

                CellStyle merged = workbook.createCellStyle();
                merged.cloneStyleFrom(cell.getCellStyle());

                BorderStyle top = inner;
                BorderStyle bottom = inner;
                BorderStyle left = (c == 0) ? outer : inner;
                BorderStyle right = (c == RESUMO_LAST_COL) ? outer : inner;

                if (r >= firstTotalRow) {
                    top = separator;
                    bottom = separator;
                }
                if (r == tableHeaderRow) {
                    top = outer;
                    bottom = outer;
                } else if (r == firstDataRow && r > tableHeaderRow) {
                    top = outer;
                }
                if (r >= dateGridStartRow && r <= dateGridEndRow && c <= 5) {
                    if (r == dateGridStartRow) {
                        top = outer;
                    }
                    if (r == dateGridEndRow) {
                        bottom = outer;
                    }
                }
                if (r == topRow) {
                    top = outer;
                }
                if (r == bottomRow) {
                    bottom = outer;
                }

                merged.setBorderTop(top);
                merged.setBorderBottom(bottom);
                merged.setBorderLeft(left);
                merged.setBorderRight(right);
                cell.setCellStyle(merged);
            }
        }

        aplicarBordaAreaLogo(sheet, workbook, emptyStyle);
    }

    /** Borda da área mesclada G1:H8 (topo/direita/base externas). */
    private void aplicarBordaAreaLogo(Sheet sheet, Workbook workbook, CellStyle emptyStyle) {
        BorderStyle outer = BorderStyle.MEDIUM;
        BorderStyle inner = BorderStyle.THIN;

        Row sheetRow = sheet.getRow(ExcelResumoGeralLogoHelper.LOGO_FIRST_ROW);
        if (sheetRow == null) {
            sheetRow = sheet.createRow(ExcelResumoGeralLogoHelper.LOGO_FIRST_ROW);
        }
        Cell cell = sheetRow.getCell(ExcelResumoGeralLogoHelper.LOGO_FIRST_COL);
        if (cell == null) {
            cell = sheetRow.createCell(ExcelResumoGeralLogoHelper.LOGO_FIRST_COL);
            cell.setCellStyle(emptyStyle);
        }
        CellStyle style = workbook.createCellStyle();
        style.cloneStyleFrom(cell.getCellStyle());
        style.setBorderTop(outer);
        style.setBorderBottom(outer);
        style.setBorderLeft(inner);
        style.setBorderRight(outer);
        cell.setCellStyle(style);
    }

    /** Borda espessa em torno do rodapé (Responsável / Economista). */
    private void applyResumoGeralBordasRodape(
            Sheet sheet, Workbook workbook, CellStyle emptyStyle, int footerRow1, int footerRow2) {
        BorderStyle outer = BorderStyle.MEDIUM;
        BorderStyle inner = BorderStyle.THIN;

        for (int r = footerRow1; r <= footerRow2; r++) {
            Row sheetRow = sheet.getRow(r);
            if (sheetRow == null) {
                sheetRow = sheet.createRow(r);
            }
            for (int c = 0; c <= RESUMO_LAST_COL; c++) {
                Cell cell = sheetRow.getCell(c);
                if (cell == null) {
                    cell = sheetRow.createCell(c);
                    cell.setCellStyle(emptyStyle);
                }
                CellStyle merged = workbook.createCellStyle();
                merged.cloneStyleFrom(cell.getCellStyle());
                merged.setBorderTop(r == footerRow1 ? outer : inner);
                merged.setBorderBottom(r == footerRow2 ? outer : inner);
                merged.setBorderLeft(c == 0 ? outer : inner);
                merged.setBorderRight(c == RESUMO_LAST_COL ? outer : inner);
                cell.setCellStyle(merged);
            }
        }
    }

    private int addResumoGeralGridDatas(Sheet sheet, int startRow, CellStyle labelStyle, CellStyle dateStyle) {
        List<Map.Entry<String, LocalDate>> entries = new ArrayList<>(ExcelResumoGeralHelper.DATAS_VENCIMENTO.entrySet());
        int idx = 0;
        while (idx < entries.size()) {
            Row row = sheet.createRow(startRow++);
            for (int colPair = 0; colPair < 3 && idx < entries.size(); colPair++, idx++) {
                Map.Entry<String, LocalDate> entry = entries.get(idx);
                int colAno = colPair * 2;
                int colData = colAno + 1;
                Cell anoCell = row.createCell(colAno);
                anoCell.setCellValue(Integer.parseInt(entry.getKey()));
                anoCell.setCellStyle(labelStyle);
                Cell dataCell = row.createCell(colData);
                if (entry.getValue() != null) {
                    dataCell.setCellValue(java.util.Date.from(
                            entry.getValue().atStartOfDay(ZoneId.of("America/Sao_Paulo")).toInstant()));
                    dataCell.setCellStyle(dateStyle);
                }
            }
        }
        return startRow;
    }

    private void setResumoAnoCell(Row row, int col, String ano, CellStyle style) {
        Cell cell = row.createCell(col);
        try {
            cell.setCellValue(Integer.parseInt(ano));
        } catch (NumberFormatException e) {
            cell.setCellValue(ano);
        }
        cell.setCellStyle(style);
    }

    private void setResumoMoneyCell(Row row, int col, BigDecimal valor, CellStyle style) {
        Cell cell = row.createCell(col);
        cell.setCellValue(nvl(valor).doubleValue());
        cell.setCellStyle(style);
    }

    private void setResumoPrincipalCell(Row row, int col, BigDecimal principal, CellStyle style, CellStyle dashStyle) {
        Cell cell = row.createCell(col);
        if (principal == null || principal.compareTo(BigDecimal.ZERO) <= 0) {
            cell.setCellValue("R$ -");
            cell.setCellStyle(dashStyle);
        } else {
            cell.setCellValue(principal.doubleValue());
            cell.setCellStyle(style);
        }
    }

    private void setResumoPercentCell(Row row, int col, BigDecimal principal, BigDecimal taxa, CellStyle style) {
        Cell cell = row.createCell(col);
        if (principal == null || principal.compareTo(BigDecimal.ZERO) <= 0 || taxa == null
                || taxa.compareTo(BigDecimal.ZERO) <= 0) {
            cell.setCellValue(0);
        } else {
            cell.setCellValue(taxa.divide(new BigDecimal("100"), 6, RoundingMode.HALF_UP).doubleValue());
        }
        cell.setCellStyle(style);
    }

    private void setResumoCorrecaoCell(Row row, int col, BigDecimal principal, BigDecimal valor,
            CellStyle style, CellStyle dashStyle) {
        Cell cell = row.createCell(col);
        if (principal == null || principal.compareTo(BigDecimal.ZERO) <= 0) {
            cell.setCellValue("R$ -");
            cell.setCellStyle(dashStyle);
        } else {
            cell.setCellValue(nvl(valor).doubleValue());
            cell.setCellStyle(style);
        }
    }

    private CellStyle createResumoLabelStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        font.setFontName("Arial");
        font.setFontHeightInPoints((short) 10);
        style.setFont(font);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        return style;
    }

    private CellStyle createResumoHeaderTableStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        font.setFontName("Arial");
        font.setFontHeightInPoints((short) 10);
        style.setFont(font);
        style.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setAlignment(HorizontalAlignment.CENTER);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        style.setWrapText(true);
        return style;
    }

    private CellStyle createResumoPercentStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setFontName("Arial");
        font.setFontHeightInPoints((short) 10);
        style.setFont(font);
        DataFormat format = workbook.createDataFormat();
        style.setDataFormat(format.getFormat("0.00%"));
        style.setAlignment(HorizontalAlignment.RIGHT);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        return style;
    }

    private CellStyle createResumoHonorariosStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setFontName("Arial");
        font.setFontHeightInPoints((short) 10);
        font.setColor(IndexedColors.RED.getIndex());
        style.setFont(font);
        DataFormat format = workbook.createDataFormat();
        style.setDataFormat(format.getFormat("#,##0.00"));
        style.setAlignment(HorizontalAlignment.RIGHT);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        return style;
    }

    private CellStyle createResumoDateStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setFontName("Arial");
        font.setFontHeightInPoints((short) 10);
        style.setFont(font);
        DataFormat format = workbook.createDataFormat();
        style.setDataFormat(format.getFormat("dd/mm/yyyy"));
        style.setAlignment(HorizontalAlignment.CENTER);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        return style;
    }

    private CellStyle createResumoTotalLabelStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        font.setFontName("Arial");
        font.setFontHeightInPoints((short) 10);
        style.setFont(font);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        return style;
    }

    private CellStyle createResumoZeroDashStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setFontName("Arial");
        font.setFontHeightInPoints((short) 10);
        style.setFont(font);
        style.setAlignment(HorizontalAlignment.RIGHT);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        return style;
    }

    private CellStyle createResumoEmptyStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setFontName("Arial");
        font.setFontHeightInPoints((short) 10);
        style.setFont(font);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        return style;
    }

    private CellStyle createResumoFooterDateTimeStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setFontName("Arial");
        font.setFontHeightInPoints((short) 10);
        style.setFont(font);
        style.setAlignment(HorizontalAlignment.RIGHT);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        return style;
    }

    /** Espelho entregue: exibe II só com INSS doméstico ou valor distinto de Imposto devido I. */
    private boolean deveExibirImpostoDevidoIIEspelho(IrpfDeclaracaoData data) {
        if (data == null || data.getImpostoDevidoII() == null
                || data.getImpostoDevidoII().compareTo(BigDecimal.ZERO) <= 0) {
            return false;
        }
        if (nvl(data.getContribuicaoPatronalPrevidenciaSocial()).compareTo(BigDecimal.ZERO) > 0) {
            return true;
        }
        BigDecimal impostoI = data.getImpostoDevidoI() != null ? data.getImpostoDevidoI() : data.getImpostoDevido();
        return impostoI == null || data.getImpostoDevidoII().compareTo(impostoI) != 0;
    }

    /** Seção DEDUÇÕES com rótulos do RESUMO IRPF (espelho entregue ou simulação planilha). */
    private int addSimSecaoDeducoesResumo(
            Sheet sheet, int row, ExcelIrpfDeducoesResumoDTO deducoes,
            CellStyle sectionHeaderStyle, CellStyle subLabelStyle, CellStyle valueStyle,
            CellStyle totalStyle, CellStyle totalLabelStyle, CellStyle highlightGreenStyle,
            boolean espelhoEntregue) {

        row = addSimTituloBloco(sheet, row, "DEDUÇÕES", sectionHeaderStyle);

        row = addSimDetalhe(sheet, row,
                "Contribuição à previdência oficial e à previdência complementar pública (até o limite do patrocinador)",
                deducoes.getPrevOficialPublica(), subLabelStyle, valueStyle);
        row = addSimDetalhe(sheet, row,
                "Contribuição à previdência oficial (Rendimentos recebidos acumuladamente)",
                deducoes.getPrevOficialRra(), subLabelStyle, valueStyle);

        CellStyle prevStyle = (!espelhoEntregue && highlightGreenStyle != null)
                ? highlightGreenStyle : valueStyle;
        row = addSimDetalhe(sheet, row,
                "Contribuição à previdência complementar (acima do limite do patrocinador) ou privada, a Fapi",
                deducoes.getPrevComplementarPrivadaEfetiva(), subLabelStyle, prevStyle);

        row = addSimDetalhe(sheet, row, "Dependentes", deducoes.getDependentes(), subLabelStyle, valueStyle);
        row = addSimDetalhe(sheet, row, "Despesas com instrução", deducoes.getDespesasInstrucao(), subLabelStyle, valueStyle);
        row = addSimDetalhe(sheet, row, "Despesas médicas", deducoes.getDespesasMedicas(), subLabelStyle, valueStyle);
        row = addSimDetalhe(sheet, row, "Pensão alimentícia judicial", deducoes.getPensaoJudicial(), subLabelStyle, valueStyle);
        row = addSimDetalhe(sheet, row, "Pensão alimentícia por escritura pública", deducoes.getPensaoEscritura(), subLabelStyle, valueStyle);
        row = addSimDetalhe(sheet, row,
                "Pensão alimentícia judicial (Rendimentos recebidos acumuladamente)",
                deducoes.getPensaoJudicialRra(), subLabelStyle, valueStyle);
        row = addSimDetalhe(sheet, row, "Livro caixa", deducoes.getLivroCaixa(), subLabelStyle, valueStyle);

        row = addSimNota(sheet, row,
                String.format("Limite de 12%% sobre os rendimentos tributáveis: R$ %,.2f",
                        deducoes.getLimite12PctRendimentos()),
                subLabelStyle);

        String totalLabel = espelhoEntregue ? "TOTAL" : "TOTAL DE DEDUÇÕES (modelo Completo)";
        row = addSimDestaqueRow(sheet, row, totalLabel, deducoes.getTotalDeducoes(), totalStyle, totalLabelStyle);
        return row;
    }

    private int addSimRendimentosTributaveisDetalhe(Sheet sheet, int row, IrpfDeclaracaoData data,
            CellStyle subLabelStyle, CellStyle valueStyle) {
        row = addSimDetalhe(sheet, row, "Recebidos de Pessoa Jurídica pelo titular",
                nvl(data.getRendimentosTributaveisTitularPJ()), subLabelStyle, valueStyle);
        row = addSimDetalhe(sheet, row, "Recebidos de Pessoa Jurídica pelos dependentes",
                nvl(data.getRendimentosTributaveisDependentesPJ()), subLabelStyle, valueStyle);
        row = addSimDetalhe(sheet, row, "Recebidos de Pessoa Física/Exterior pelo titular",
                nvl(data.getRendimentosTributaveisTitularPF()), subLabelStyle, valueStyle);
        row = addSimDetalhe(sheet, row, "Recebidos de Pessoa Física/Exterior pelos dependentes",
                nvl(data.getRendimentosTributaveisDependentesPF()), subLabelStyle, valueStyle);
        row = addSimDetalhe(sheet, row, "Recebidos acumuladamente pelo titular",
                nvl(data.getRendimentosAcumuladosTitular()), subLabelStyle, valueStyle);
        row = addSimDetalhe(sheet, row, "Recebidos acumuladamente pelos dependentes",
                nvl(data.getRendimentosAcumuladosDependentes()), subLabelStyle, valueStyle);
        row = addSimDetalhe(sheet, row, "Resultado tributável da Atividade Rural",
                nvl(data.getResultadoAtividadeRural()), subLabelStyle, valueStyle);
        return row;
    }

    private int addSimImpostoPagoLinhas(Sheet sheet, int row, IrpfDeclaracaoData data,
            CellStyle subLabelStyle, CellStyle valueStyle) {
        row = addSimDetalhe(sheet, row, "Imposto retido na fonte do titular",
                data.getImpostoRetidoFonteTitular(), subLabelStyle, valueStyle);
        row = addSimDetalhe(sheet, row, "Imp. retido na fonte dos dependentes",
                data.getImpostoRetidoDependentes(), subLabelStyle, valueStyle);
        row = addSimDetalhe(sheet, row, "Carnê-Leão do titular", data.getCarneLeaoTitular(), subLabelStyle, valueStyle);
        row = addSimDetalhe(sheet, row, "Carnê-Leão dos dependentes", data.getCarneLeaoDependentes(), subLabelStyle, valueStyle);
        row = addSimDetalhe(sheet, row, "Imposto complementar", data.getImpostoComplementar(), subLabelStyle, valueStyle);
        row = addSimDetalhe(sheet, row, "Imposto pago no exterior", data.getImpostoPagoExterior(), subLabelStyle, valueStyle);
        row = addSimDetalhe(sheet, row, "Imposto retido na fonte (Lei nº 11.033/2004)",
                data.getImpostoRetidoFonteLei11033(), subLabelStyle, valueStyle);
        row = addSimDetalhe(sheet, row, "Imposto retido RRA", data.getImpostoRetidoRRA(), subLabelStyle, valueStyle);
        return row;
    }

    private BigDecimal calcularTotalImpostoPagoDeclaracao(IrpfDeclaracaoData data) {
        return resumoGeralHelper.calcularTotalImpostoPagoDeclaracao(data);
    }

    private int addSimNomeCpfLinha(Sheet sheet, int row, Person person, CellStyle style) {
        Row r = sheet.createRow(row);
        Cell c = r.createCell(SIM_COL_LABEL_START);
        String nome = person.getNome() != null ? person.getNome().toUpperCase() : "";
        String cpf = formatCPF(person.getCpf());
        c.setCellValue(String.format("NOME: %s - CPF: %s", nome, cpf));
        c.setCellStyle(style);
        mergeSimRegion(sheet, row, SIM_COL_LABEL_START, SIM_COL_VALOR);
        fillSimRowCells(r, SIM_COL_LABEL_START, SIM_COL_VALOR, style);
        return row + 1;
    }

    // =============================================
    // NOVOS MÉTODOS: SIMULAÇÃO IRPF NO EXCEL (legado)
    // =============================================

    /**
     * Adiciona bloco completo de simulação IRPF.
     * Sim 1 (DECLARACAO): seções 1–7 + comparativo + 8–10 conforme declaração entregue.
     * Sim 2 (CONTRACHEQUES_EXTRA): seções 1–7 + comparativo + 8–10 estudo (sem repetir declaração).
     */
    private int addSimulacaoCompleta(Sheet sheet, String ano, IrpfDeclaracaoData data,
            Map<String, java.util.List<br.com.verticelabs.pdfprocessor.domain.model.IrTabelaTributacao>> tabelasTributacao,
            Map<String, IrParametrosAnuais> parametrosTributacao,
            ModoSimulacaoExcel modo, BigDecimal previdenciaPrivada,
            int startRow,
            CellStyle titleStyle, CellStyle labelStyle, CellStyle valueStyle,
            CellStyle subLabelStyle, CellStyle totalStyle, CellStyle totalLabelStyle, CellStyle aliquotaStyle,
            CellStyle highlightGreenStyle) {

        log.info("Adicionando simulação IRPF ({}) para ano-calendário {} (exercício {})",
                modo, ano, data.getExercicio());

        java.util.List<br.com.verticelabs.pdfprocessor.domain.model.IrTabelaTributacao> faixasTabela =
                tabelasTributacao.getOrDefault(ano, Collections.emptyList());
        IrParametrosAnuais paramsAno = parametrosTributacao != null ? parametrosTributacao.get(ano) : null;

        boolean usarPrevidenciaPlanilha = modo == ModoSimulacaoExcel.SIMULACAO_COMPLETA_PLANILHA;
        SimuladorIrpfRequest request = excelIrpfSimulacaoMapper.fromDeclaracao(
                data, previdenciaPrivada, usarPrevidenciaPlanilha);
        SimuladorIrpfResponse response = simuladorMotorService.simular(request, faixasTabela, paramsAno);
        ModeloTributacaoResultDTO modeloCompleto = response.getModeloCompleto();
        ModeloTributacaoResultDTO modeloSimplificado = response.getModeloSimplificado();

        int anoCalendarioInt = parseAnoCalendario(ano);
        BigDecimal despesasMedicasExib = valorOuFallback(request.getDespesasMedicas(), data.getDespesasMedicas());
        BigDecimal despesasInstrucaoExib = despesasInstrucaoParaExibicao(request, data);
        BigDecimal pensaoJudicialExib = valorOuFallback(request.getPensaoAlimenticia(), data.getPensaoAlimenticiaJudicial());
        BigDecimal pensaoEscrituraExib = nvl(data.getPensaoAlimenticiaEscrituraPublica());
        BigDecimal prevComplExib = usarPrevidenciaPlanilha
                ? valorOuFallback(previdenciaPrivada, request.getPrevidenciaPrivada())
                : valorOuFallback(request.getPrevidenciaPrivada(), previdenciaPrivada);
        BigDecimal inssDomesticoEfetivo = IrDoacoesDeducaoCalculator.calcularInssDomesticoEfetivo(
                valorOuFallback(request.getPrevidenciaEmpregadoDomestico(), data.getContribuicaoPatronalPrevidenciaSocial()),
                anoCalendarioInt, paramsAno);

        int row = startRow;

        sheet.createRow(row++);

        // === TÍTULO ===
        Row titleRow = sheet.createRow(row++);
        Cell titleCell = titleRow.createCell(SIM_COL_LABEL_START);
        String tituloBase = String.format("SIMULAÇÃO IRPF — EXERCÍCIO %s / ANO-CALENDÁRIO %s",
                data.getExercicio() != null ? data.getExercicio() : "?",
                data.getAnoCalendario() != null ? data.getAnoCalendario() : "?");
        if (modo == ModoSimulacaoExcel.SIMULACAO_COMPLETA_PLANILHA) {
            tituloBase += " — COM APROVEITAMENTO DAS CONTRIBUIÇÕES EXTRAS";
        }
        titleCell.setCellValue(tituloBase);
        titleCell.setCellStyle(titleStyle);
        mergeSimRegion(sheet, row - 1, SIM_COL_LABEL_START, SIM_COL_VALOR);
        fillSimRowCells(titleRow, SIM_COL_LABEL_START, SIM_COL_VALOR, titleStyle);

        if (data.getTipoTributacao() != null || data.getTipoDeclaracao() != null) {
            Row subRow = sheet.createRow(row++);
            Cell subCell = subRow.createCell(SIM_COL_LABEL_START);
            String sub = String.format("Tributação: %s | Declaração: %s",
                    data.getTipoTributacao() != null ? data.getTipoTributacao() : "-",
                    data.getTipoDeclaracao() != null ? data.getTipoDeclaracao() : "-");
            subCell.setCellValue(sub);
            subCell.setCellStyle(subLabelStyle);
            mergeSimRegion(sheet, row - 1, SIM_COL_LABEL_START, SIM_COL_LABEL_END);
            fillSimRowCells(subRow, SIM_COL_LABEL_START, SIM_COL_LABEL_END, subLabelStyle);
        }

        if ("SIMPLIFICADO".equalsIgnoreCase(data.getTipoTributacao())) {
            row = addSimNota(sheet, row,
                    "Declaração entregue no modelo Simplificado — abaixo, comparativo Completo vs Simplificado.",
                    subLabelStyle);
        }

        sheet.createRow(row++);

        // === 1. RENDIMENTOS TRIBUTÁVEIS (discriminado conforme RESUMO da declaração) ===
        row = addSimSectionHeader(sheet, row, "1", "RENDIMENTOS TRIBUTÁVEIS", labelStyle);

        int rendInicio = row;
        row = addSimDetalhe(sheet, row, "Recebidos de Pessoa Jurídica pelo titular",
                nvl(data.getRendimentosTributaveisTitularPJ()), subLabelStyle, valueStyle);
        row = addSimDetalhe(sheet, row, "Recebidos de Pessoa Jurídica pelos dependentes",
                nvl(data.getRendimentosTributaveisDependentesPJ()), subLabelStyle, valueStyle);
        row = addSimDetalhe(sheet, row, "Recebidos de Pessoa Física/Exterior pelo titular",
                nvl(data.getRendimentosTributaveisTitularPF()), subLabelStyle, valueStyle);
        row = addSimDetalhe(sheet, row, "Recebidos de Pessoa Física/Exterior pelos dependentes",
                nvl(data.getRendimentosTributaveisDependentesPF()), subLabelStyle, valueStyle);
        row = addSimDetalhe(sheet, row, "Recebidos acumuladamente pelo titular",
                nvl(data.getRendimentosAcumuladosTitular()), subLabelStyle, valueStyle);
        row = addSimDetalhe(sheet, row, "Recebidos acumuladamente pelos dependentes",
                nvl(data.getRendimentosAcumuladosDependentes()), subLabelStyle, valueStyle);
        row = addSimDetalhe(sheet, row, "Resultado tributável da Atividade Rural",
                nvl(data.getResultadoAtividadeRural()), subLabelStyle, valueStyle);
        int rendFim = row - 1;

        String formulaRendTotal = String.format("SUM(%s:%s)", cellRef(rendInicio), cellRef(rendFim));
        SimValueRef refRendimentos = addSimRowFormula(sheet, row, "1.8", "TOTAL DE RENDIMENTOS TRIBUTÁVEIS",
                formulaRendTotal, totalLabelStyle, totalStyle, true);
        row = refRendimentos.nextRow();
        row = addSimNota(sheet, row,
                "Não incluir a parcela isenta de aposentadoria, reserva remunerada, reforma e pensão para declarante com 65 anos ou mais.",
                subLabelStyle);

        sheet.createRow(row++);

        // === 2. DEDUÇÕES ===
        row = addSimSectionHeader(sheet, row, "2", "DEDUÇÕES", labelStyle);

        BigDecimal prevOficialExib = IrpfPrevidenciaOficialResolver.resolver(data);
        row = addSimRow(sheet, row, "2.1", "Previdência oficial",
                prevOficialExib, subLabelStyle, valueStyle, false);
        SimValueRef refDedInicio = new SimValueRef(row - 1);

        int numDependentes = inferirQtdDependentes(data, paramsAno);
        String labelDep = String.format("Dependentes (%d dependente%s)", numDependentes,
                numDependentes == 1 ? "" : "s");
        row = addSimRow(sheet, row, "2.2", labelDep,
                data.getDeducaoDependentes(), subLabelStyle, valueStyle, false);

        int numAlimentandos = (data.getAlimentandos() != null) ? data.getAlimentandos().size() : 0;
        String labelAlim = String.format("Alimentandos com decisão judicial (%d)", numAlimentandos);
        row = addSimRow(sheet, row, "2.3", labelAlim,
                pensaoJudicialExib, subLabelStyle, valueStyle, false);

        row = addSimRow(sheet, row, "2.4", "Despesa com instrução",
                despesasInstrucaoExib, subLabelStyle, valueStyle, false);
        row = addSimNota(sheet, row, "Limitada a R$ 3.561,50 por titular/dependente/alimentando.", subLabelStyle);

        row = addSimRow(sheet, row, "2.5", "Despesa médica",
                despesasMedicasExib, subLabelStyle, valueStyle, false);

        row = addSimRow(sheet, row, "2.6", "Pensão alimentícia (escritura pública)",
                pensaoEscrituraExib, subLabelStyle, valueStyle, false);

        row = addSimRow(sheet, row, "2.7",
                "Outras deduções (contribuições normais e extraordinárias à previdência complementar)",
                null, subLabelStyle, valueStyle, false);

        CellStyle prevValueStyle = (modo == ModoSimulacaoExcel.SIMULACAO_COMPLETA_PLANILHA && highlightGreenStyle != null)
                ? highlightGreenStyle : valueStyle;
        String labelPrev = modo == ModoSimulacaoExcel.ESPELHO_ENTREGUE
                ? "Previdência Privada, Funpresp, FAPI, Carne-Leão, Livro Caixa"
                : "Contribuição previdência complementar — Total planilha conforme apontamentos nos contracheques";
        row = addSimRow(sheet, row, "2.7.1", labelPrev,
                prevComplExib, subLabelStyle, prevValueStyle, false);

        row = addSimRow(sheet, row, "2.7.2",
                "INSS patronal empregador doméstico (Contrib. Prev. Empregador Doméstico)",
                inssDomesticoEfetivo, subLabelStyle, valueStyle, false);
        SimValueRef refDedFim = new SimValueRef(row - 1);

        BigDecimal rendTrib = nvl(data.getRendimentosTributaveisTotal());
        BigDecimal limite12 = rendTrib.multiply(new BigDecimal("0.12")).setScale(2, RoundingMode.HALF_UP);
        row = addSimNota(sheet, row,
                String.format("Limite de 12%% sobre os rendimentos tributáveis: R$ %,.2f", limite12),
                subLabelStyle);

        sheet.createRow(row++);
        String formulaTotalDed = String.format("SUM(%s:%s)", refDedInicio.cell(), refDedFim.cell());
        SimValueRef refTotalDeducoes = addSimRowFormula(sheet, row, "2.8", "TOTAL DE DEDUÇÕES (modelo Completo)",
                formulaTotalDed, totalLabelStyle, totalStyle, true);
        row = refTotalDeducoes.nextRow();

        if (data.getDeducoesTotal() != null
                && nvl(modeloCompleto.getTotalDeducoes()).compareTo(nvl(data.getDeducoesTotal())) != 0) {
            row = addSimNota(sheet, row,
                    String.format("Total declarado entregue: R$ %,.2f", data.getDeducoesTotal()),
                    subLabelStyle);
        }

        sheet.createRow(row++);

        // === MODELO COMPLETO — seções 3–7 ===
        ModeloCalculoRefs refsCompleto = addSimModeloCalculo(sheet, row, "COMPLETO", modeloCompleto, data, modo,
                refRendimentos, refTotalDeducoes, null, false,
                labelStyle, valueStyle, subLabelStyle, totalStyle, totalLabelStyle, aliquotaStyle);
        row = refsCompleto.nextRow();

        sheet.createRow(row++);

        // === MODELO SIMPLIFICADO — seções 3–7 ===
        ModeloCalculoRefs refsSimplificado = addSimModeloCalculo(sheet, row, "SIMPLIFICADO", modeloSimplificado, data, modo,
                refRendimentos, null, modeloSimplificado.getDescontoSimplificado(), true,
                labelStyle, valueStyle, subLabelStyle, totalStyle, totalLabelStyle, aliquotaStyle);
        row = refsSimplificado.nextRow();

        sheet.createRow(row++);

        // === COMPARATIVO COMPLETO vs SIMPLIFICADO (referencia células dos modelos) ===
        row = addSimComparativoModelos(sheet, row, response, refsCompleto, refsSimplificado,
                labelStyle, valueStyle, subLabelStyle);

        // === 8–10: declaração entregue (sim 1) ou estudo contracheques (sim 2) ===
        sheet.createRow(row++);
        if (modo == ModoSimulacaoExcel.ESPELHO_ENTREGUE) {
        row = addSimDeclaracaoEntregueSections(sheet, row, data, labelStyle, subLabelStyle, valueStyle,
                totalStyle, totalLabelStyle, aliquotaStyle);
        } else {
            // Estudo com aproveitamento: sempre modelo Completo (deduções da planilha + pagamentos)
            row = addSimEstudoContrachequesSections(sheet, row, refsCompleto, data,
                    labelStyle, subLabelStyle, valueStyle, totalStyle, totalLabelStyle);
        }

        sheet.createRow(row++);
        return row;
    }

    /** Comparativo lado a lado: Completo vs Simplificado (referencia células calculadas). */
    private int addSimComparativoModelos(Sheet sheet, int row, SimuladorIrpfResponse response,
            ModeloCalculoRefs refsCompleto, ModeloCalculoRefs refsSimplificado,
            CellStyle labelStyle, CellStyle valueStyle, CellStyle subLabelStyle) {

        boolean recomendaCompleto = "COMPLETO".equals(response.getModeloRecomendado());

        row = addSimTituloBloco(sheet, row, "COMPARATIVO DE MODELOS", labelStyle);

        Row headerRow = sheet.createRow(row++);
        headerRow.createCell(SIM_COL_LABEL_START).setCellValue("");
        headerRow.getCell(SIM_COL_LABEL_START).setCellStyle(subLabelStyle);
        Cell hdrCompleto = headerRow.createCell(SIM_COL_COMPLETO);
        hdrCompleto.setCellValue(recomendaCompleto ? "Completo ★ Recomendado" : "Completo");
        hdrCompleto.setCellStyle(labelStyle);
        Cell hdrSimpl = headerRow.createCell(SIM_COL_VALOR);
        hdrSimpl.setCellValue(recomendaCompleto ? "Simplificado" : "Simplificado ★ Recomendado");
        hdrSimpl.setCellStyle(labelStyle);

        row = addSimComparativoLinhaFormula(sheet, row, "Imposto devido",
                refsCompleto.impostoDevido().cell(), refsSimplificado.impostoDevido().cell(),
                subLabelStyle, valueStyle);
        row = addSimComparativoLinhaFormula(sheet, row, "Imposto a restituir",
                refsCompleto.impostoRestituir().cell(), refsSimplificado.impostoRestituir().cell(),
                subLabelStyle, valueStyle);
        row = addSimComparativoLinhaFormula(sheet, row, "Saldo de imposto a pagar",
                refsCompleto.saldoPagar().cell(), refsSimplificado.saldoPagar().cell(),
                subLabelStyle, valueStyle);

        String recomendado = recomendaCompleto ? "Completo" : "Simplificado";
        row = addSimNota(sheet, row,
                "Modelo recomendado: " + recomendado + " (maior restituição ou menor valor a pagar)",
                subLabelStyle);

        return row;
    }

    private int addSimComparativoLinhaFormula(Sheet sheet, int row, String label,
            String refCompleto, String refSimplificado,
            CellStyle labelStyle, CellStyle valueStyle) {
        Row r = sheet.createRow(row);
        Cell lbl = r.createCell(SIM_COL_LABEL_START);
        lbl.setCellValue("    " + label);
        lbl.setCellStyle(labelStyle);

        Cell valC = r.createCell(SIM_COL_COMPLETO);
        valC.setCellFormula(refCompleto);
        valC.setCellStyle(valueStyle);

        Cell valS = r.createCell(SIM_COL_VALOR);
        valS.setCellFormula(refSimplificado);
        valS.setCellStyle(valueStyle);

        return row + 1;
    }

    /**
     * Seções 3–7 para um modelo de tributação (Completo ou Simplificado).
     * Retorna referências das células-chave para o comparativo e fórmulas encadeadas.
     */
    private ModeloCalculoRefs addSimModeloCalculo(Sheet sheet, int row, String nomeModelo,
            ModeloTributacaoResultDTO modelo, IrpfDeclaracaoData data, ModoSimulacaoExcel modo,
            SimValueRef refRendimentos, SimValueRef refTotalDeducoes,
            BigDecimal descontoSimplificadoValor, boolean simplificado,
            CellStyle labelStyle, CellStyle valueStyle, CellStyle subLabelStyle,
            CellStyle totalStyle, CellStyle totalLabelStyle, CellStyle aliquotaStyle) {

        row = addSimTituloBloco(sheet, row, "MODELO " + nomeModelo, labelStyle);

        BigDecimal aliquotaEfetiva = modelo.getAliquotaEfetiva();
        SimValueRef refDescontoSimplificado = null;

        // === 3. BASE DE CÁLCULO ===
        String formulaBase;
        if (simplificado) {
            SimValueRef refDesc = addSimRowValor(sheet, row, "3a", "Desconto simplificado — " + nomeModelo,
                    descontoSimplificadoValor, subLabelStyle, valueStyle, false);
            row = refDesc.nextRow();
            refDescontoSimplificado = refDesc;
            formulaBase = String.format("%s-%s", refRendimentos.cell(), refDesc.cell());
        } else {
            formulaBase = String.format("%s-%s", refRendimentos.cell(), refTotalDeducoes.cell());
        }
        SimValueRef refBase = addSimRowFormula(sheet, row, "3", "BASE DE CÁLCULO — " + nomeModelo,
                formulaBase, labelStyle, valueStyle, true);
        row = refBase.nextRow();

        if (simplificado && descontoSimplificadoValor != null) {
            row = addSimNota(sheet, row,
                    String.format("Desconto simplificado aplicado: R$ %,.2f", descontoSimplificadoValor),
                    subLabelStyle);
        }

        sheet.createRow(row++);

        // === 4. IMPOSTO ===
        row = addSimSectionHeader(sheet, row, "4", "IMPOSTO — " + nomeModelo, labelStyle);
        row = addSimNota(sheet, row, "Demonstrativo da Apuração do Imposto", subLabelStyle);

        Row faixaHeader = sheet.createRow(row++);
        faixaHeader.createCell(SIM_COL_LABEL_START).setCellValue("Faixa");
        faixaHeader.createCell(SIM_COL_BASE).setCellValue("Base");
        faixaHeader.createCell(SIM_COL_ALIQ).setCellValue("Alíquota");
        faixaHeader.createCell(SIM_COL_VALOR).setCellValue("Imposto");
        for (int c : new int[] { SIM_COL_LABEL_START, SIM_COL_BASE, SIM_COL_ALIQ, SIM_COL_VALOR }) {
            faixaHeader.getCell(c).setCellStyle(subLabelStyle);
        }

        int primeiraFaixaImposto = -1;
        int ultimaFaixaImposto = -1;
        if (modelo.getFaixas() != null) {
            for (ResultadoFaixaCalculoDTO faixaCalc : modelo.getFaixas()) {
                Row faixaRow = sheet.createRow(row);
                Cell faixaLabel = faixaRow.createCell(SIM_COL_LABEL_START);
                faixaLabel.setCellValue(faixaCalc.getFaixa() != null ? "Faixa " + faixaCalc.getFaixa() : "");
                faixaLabel.setCellStyle(subLabelStyle);

                String baseCell = CellReference.convertNumToColString(SIM_COL_BASE) + (row + 1);
                String aliqCell = CellReference.convertNumToColString(SIM_COL_ALIQ) + (row + 1);

                Cell baseCellObj = faixaRow.createCell(SIM_COL_BASE);
                baseCellObj.setCellValue(faixaCalc.getBaseNaFaixa().doubleValue());
                baseCellObj.setCellStyle(valueStyle);

                BigDecimal aliq = nvl(faixaCalc.getAliquota());
                Cell aliqCellObj = faixaRow.createCell(SIM_COL_ALIQ);
                aliqCellObj.setCellValue(aliq.doubleValue());
                aliqCellObj.setCellStyle(valueStyle);

                Cell impCell = faixaRow.createCell(SIM_COL_VALOR);
                if (aliq.compareTo(BigDecimal.ZERO) > 0) {
                    impCell.setCellFormula(baseCell + "*" + aliqCell);
                } else {
                    impCell.setCellValue(0.0);
                }
                impCell.setCellStyle(valueStyle);

                if (primeiraFaixaImposto < 0) {
                    primeiraFaixaImposto = row;
                }
                ultimaFaixaImposto = row;
                row++;
            }
        }

        sheet.createRow(row++);
        String formulaImpostoTotal = ultimaFaixaImposto >= primeiraFaixaImposto
                ? String.format("SUM(%s:%s)",
                cellRef(primeiraFaixaImposto), cellRef(ultimaFaixaImposto))
                : "0";
        SimValueRef refImpostoProgressivo = addSimRowFormula(sheet, row, "4T", "Total imposto progressivo — " + nomeModelo,
                formulaImpostoTotal, subLabelStyle, totalStyle, false);
        row = refImpostoProgressivo.nextRow();

        if (modelo.getReducaoAnual() != null && modelo.getReducaoAnual().compareTo(BigDecimal.ZERO) > 0) {
            row = addSimDetalhe(sheet, row, "Redução anual (Lei 15.270/2025)",
                    modelo.getReducaoAnual(), subLabelStyle, valueStyle);
            row = addSimDetalhe(sheet, row, "Imposto após redução anual",
                    modelo.getImpostoDevidoAposReducao(), subLabelStyle, valueStyle);
        }

        sheet.createRow(row++);

        // === 5. DEDUÇÕES ESPECIAIS ===
        row = addSimSectionHeader(sheet, row, "5", "DEDUÇÕES ESPECIAIS — " + nomeModelo, labelStyle);
        SimValueRef refIncentivo = addSimRowValor(sheet, row, "5.1",
                "Incentivo (doações de incentivo no ano-calendário — limite 6% do imposto)",
                nvl(data.getDeducaoIncentivo()), subLabelStyle, valueStyle, false);
        row = refIncentivo.nextRow();

        sheet.createRow(row++);

        // === 6. IMPOSTO DEVIDO (4 − 5) ===
        String formulaImpostoDevido = String.format("MAX(0,%s-%s)",
                refImpostoProgressivo.cell(), refIncentivo.cell());
        SimValueRef refImpostoDevido = addSimRowFormula(sheet, row, "6", "IMPOSTO DEVIDO (4 – 5) — " + nomeModelo,
                formulaImpostoDevido, totalLabelStyle, totalStyle, true);
        row = refImpostoDevido.nextRow();

        // Imposto pago total (entrada fixa para fórmulas de restituição/saldo)
        SimValueRef refImpostoPago = addSimRowValor(sheet, row, "6a", "Imposto pago total (entrada) — " + nomeModelo,
                modelo.getImpostoPagoTotal(), subLabelStyle, valueStyle, false);
        row = refImpostoPago.nextRow();

        String formulaRestituir = String.format("IF(%s>%s,%s-%s,0)",
                refImpostoPago.cell(), refImpostoDevido.cell(),
                refImpostoPago.cell(), refImpostoDevido.cell());
        SimValueRef refRestituir = addSimRowFormula(sheet, row, "6b", "Imposto a restituir — " + nomeModelo,
                formulaRestituir, subLabelStyle, valueStyle, false);
        row = refRestituir.nextRow();

        String formulaSaldo = String.format("IF(%s>%s,%s-%s,0)",
                refImpostoDevido.cell(), refImpostoPago.cell(),
                refImpostoDevido.cell(), refImpostoPago.cell());
        SimValueRef refSaldo = addSimRowFormula(sheet, row, "6c", "Saldo de imposto a pagar — " + nomeModelo,
                formulaSaldo, subLabelStyle, valueStyle, false);
        row = refSaldo.nextRow();

        sheet.createRow(row++);

        // === 7. ALÍQUOTA EFETIVA ===
        Row aliqRow = sheet.createRow(row++);
        aliqRow.createCell(SIM_COL_LABEL_START).setCellValue("7. Alíquota efetiva — " + nomeModelo);
        aliqRow.getCell(SIM_COL_LABEL_START).setCellStyle(aliquotaStyle);
        mergeSimRegion(sheet, row - 1, SIM_COL_LABEL_START, SIM_COL_LABEL_END);
        fillSimRowCells(aliqRow, SIM_COL_LABEL_START, SIM_COL_LABEL_END, aliquotaStyle);
        Cell aliqValCell = aliqRow.createCell(SIM_COL_VALOR);
        aliqValCell.setCellValue(aliquotaEfetiva.setScale(2, RoundingMode.HALF_UP).toPlainString() + "%");
        aliqValCell.setCellStyle(aliquotaStyle);
        row = addSimNota(sheet, row, "Percentual do imposto sobre os rendimentos tributáveis", subLabelStyle);

        String labelDed = simplificado ? "Desconto simplificado" :
                (modo == ModoSimulacaoExcel.SIMULACAO_COMPLETA_PLANILHA ? "Total de deduções (planilha)" : "Total de deduções");
        row = addSimDetalhe(sheet, row, "Rendimentos tributáveis",
                data.getRendimentosTributaveisTotal(), subLabelStyle, valueStyle);
        if (simplificado && refDescontoSimplificado != null) {
            row = addSimDetalheFormula(sheet, row, labelDed, refDescontoSimplificado.cell(), subLabelStyle, valueStyle);
        } else if (refTotalDeducoes != null) {
            row = addSimDetalheFormula(sheet, row, labelDed, refTotalDeducoes.cell(), subLabelStyle, valueStyle);
        }
        row = addSimDetalheFormula(sheet, row, "Base de cálculo", refBase.cell(), subLabelStyle, valueStyle);
        row = addSimDetalheFormula(sheet, row, "Imposto calculado", refImpostoProgressivo.cell(), subLabelStyle, valueStyle);

        Row impDevidoRow = sheet.createRow(row++);
        Cell impDevidoLbl = impDevidoRow.createCell(SIM_COL_LABEL_START);
        impDevidoLbl.setCellValue("    Imposto devido");
        impDevidoLbl.setCellStyle(totalLabelStyle);
        mergeSimRegion(sheet, row - 1, SIM_COL_LABEL_START, SIM_COL_LABEL_END);
        fillSimRowCells(impDevidoRow, SIM_COL_LABEL_START, SIM_COL_LABEL_END, totalLabelStyle);
        Cell impDevidoVal = impDevidoRow.createCell(SIM_COL_VALOR);
        impDevidoVal.setCellFormula(refImpostoDevido.cell());
        impDevidoVal.setCellStyle(totalStyle);

        return new ModeloCalculoRefs(row, refImpostoDevido, refRestituir, refSaldo, refBase, refImpostoPago);
    }

    /** Seções 8–10 do estudo com previdência complementar dos contracheques (simulação 2). */
    private int addSimEstudoContrachequesSections(Sheet sheet, int row, ModeloCalculoRefs refsEstudo,
            IrpfDeclaracaoData data,
            CellStyle labelStyle, CellStyle subLabelStyle, CellStyle valueStyle,
            CellStyle totalStyle, CellStyle totalLabelStyle) {

        final String sufixo = " — ESTUDO COM APROVEITAMENTO DAS CONTRIBUIÇÕES EXTRAS";

        row = addSimSectionHeader(sheet, row, "8", "IMPOSTO DEVIDO" + sufixo, labelStyle);
        row = addSimDetalheFormula(sheet, row, "Base de cálculo do imposto",
                refsEstudo.baseCalculo().cell(), subLabelStyle, valueStyle);
        row = addSimDetalheFormula(sheet, row, "Imposto devido",
                refsEstudo.impostoDevido().cell(), subLabelStyle, valueStyle);
        if (data.getDeducaoIncentivo() != null && data.getDeducaoIncentivo().compareTo(BigDecimal.ZERO) > 0) {
            row = addSimDetalhe(sheet, row, "Dedução de incentivo", data.getDeducaoIncentivo(), subLabelStyle, valueStyle);
        }
        row = addSimDestaqueRowFormula(sheet, row, "Total do imposto devido",
                refsEstudo.impostoDevido().cell(), totalStyle, totalLabelStyle);

        sheet.createRow(row++);

        row = addSimSectionHeader(sheet, row, "9", "IMPOSTO PAGO" + sufixo, labelStyle);
        row = addSimDetalhe(sheet, row, "Imposto retido na fonte do titular",
                data.getImpostoRetidoFonteTitular(), subLabelStyle, valueStyle);
        row = addSimDetalhe(sheet, row, "Imp. retido na fonte dos dependentes",
                data.getImpostoRetidoDependentes(), subLabelStyle, valueStyle);
        row = addSimDetalhe(sheet, row, "Carnê-Leão do titular", data.getCarneLeaoTitular(), subLabelStyle, valueStyle);
        row = addSimDetalhe(sheet, row, "Carnê-Leão dos dependentes", data.getCarneLeaoDependentes(), subLabelStyle, valueStyle);
        row = addSimDetalhe(sheet, row, "Imposto complementar", data.getImpostoComplementar(), subLabelStyle, valueStyle);
        row = addSimDetalhe(sheet, row, "Imposto pago no exterior", data.getImpostoPagoExterior(), subLabelStyle, valueStyle);
        row = addSimDetalhe(sheet, row, "Imposto retido na fonte (Lei nº 11.033/2004)",
                data.getImpostoRetidoFonteLei11033(), subLabelStyle, valueStyle);
        row = addSimDetalhe(sheet, row, "Imposto retido RRA", data.getImpostoRetidoRRA(), subLabelStyle, valueStyle);
        row = addSimDestaqueRowFormula(sheet, row, "Total do imposto pago",
                refsEstudo.impostoPago().cell(), totalStyle, totalLabelStyle);

        sheet.createRow(row++);

        row = addSimSectionHeader(sheet, row, "10", "RESULTADO" + sufixo, labelStyle);
        row = addSimDetalheFormula(sheet, row, "Imposto a restituir",
                refsEstudo.impostoRestituir().cell(), subLabelStyle, valueStyle);
        row = addSimDestaqueRowFormula(sheet, row, "Saldo de imposto a pagar",
                refsEstudo.saldoPagar().cell(), totalStyle, totalLabelStyle);

        return row;
    }

    /** Seções 8–10 espelhando a declaração entregue (simulação 1). */
    private int addSimDeclaracaoEntregueSections(Sheet sheet, int row, IrpfDeclaracaoData data,
            CellStyle labelStyle, CellStyle subLabelStyle, CellStyle valueStyle,
            CellStyle totalStyle, CellStyle totalLabelStyle, CellStyle aliquotaStyle) {

        row = addSimSectionHeader(sheet, row, "8", "IMPOSTO DEVIDO — CONFORME DECLARAÇÃO ENTREGUE", labelStyle);
        row = addSimDetalhe(sheet, row, "Base de cálculo do imposto", data.getBaseCalculoImposto(), subLabelStyle, valueStyle);
        row = addSimDetalhe(sheet, row, "Imposto devido", data.getImpostoDevido(), subLabelStyle, valueStyle);
        row = addSimDetalhe(sheet, row, "Dedução de incentivo", data.getDeducaoIncentivo(), subLabelStyle, valueStyle);
        row = addSimDetalhe(sheet, row, "Imposto devido I", data.getImpostoDevidoI(), subLabelStyle, valueStyle);
        if (data.getImpostoDevidoII() != null) {
            row = addSimDetalhe(sheet, row, "Imposto devido II", data.getImpostoDevidoII(), subLabelStyle, valueStyle);
        }
        row = addSimDetalhe(sheet, row, "Imposto devido RRA", data.getImpostoSobreRRA(), subLabelStyle, valueStyle);
        row = addSimAliquotaDeclaracaoRow(sheet, row, "Alíquota efetiva (%)", data.getAliquotaEfetiva(),
                subLabelStyle, aliquotaStyle);
        row = addSimDestaqueRow(sheet, row, "Total do imposto devido", data.getTotalImpostoDevido(), totalStyle, totalLabelStyle);

        sheet.createRow(row++);

        row = addSimSectionHeader(sheet, row, "9", "IMPOSTO PAGO — CONFORME DECLARAÇÃO ENTREGUE", labelStyle);
        row = addSimDetalhe(sheet, row, "Imposto retido na fonte do titular",
                data.getImpostoRetidoFonteTitular(), subLabelStyle, valueStyle);
        row = addSimDetalhe(sheet, row, "Imp. retido na fonte dos dependentes",
                data.getImpostoRetidoDependentes(), subLabelStyle, valueStyle);
        row = addSimDetalhe(sheet, row, "Carnê-Leão do titular", data.getCarneLeaoTitular(), subLabelStyle, valueStyle);
        row = addSimDetalhe(sheet, row, "Carnê-Leão dos dependentes", data.getCarneLeaoDependentes(), subLabelStyle, valueStyle);
        row = addSimDetalhe(sheet, row, "Imposto complementar", data.getImpostoComplementar(), subLabelStyle, valueStyle);
        row = addSimDetalhe(sheet, row, "Imposto pago no exterior", data.getImpostoPagoExterior(), subLabelStyle, valueStyle);
        row = addSimDetalhe(sheet, row, "Imposto retido na fonte (Lei nº 11.033/2004)",
                data.getImpostoRetidoFonteLei11033(), subLabelStyle, valueStyle);
        row = addSimDetalhe(sheet, row, "Imposto retido RRA", data.getImpostoRetidoRRA(), subLabelStyle, valueStyle);
        row = addSimDestaqueRow(sheet, row, "Total do imposto pago", data.getImpostoPagoTotal(), totalStyle, totalLabelStyle);
        row = addSimDestaqueRow(sheet, row, "Total do imposto pago na fonte do titular",
                data.getImpostoRetidoFonteTitular(), totalStyle, totalLabelStyle);

        sheet.createRow(row++);

        row = addSimSectionHeader(sheet, row, "10", "RESULTADO — CONFORME DECLARAÇÃO ENTREGUE", labelStyle);
        row = addSimDetalhe(sheet, row, "Imposto a restituir", data.getImpostoRestituir(), subLabelStyle, valueStyle);
        row = addSimDestaqueRow(sheet, row, "Saldo de imposto a pagar", data.getSaldoImpostoPagar(), totalStyle, totalLabelStyle);

        return row;
    }

    /** Linha de destaque (fundo amarelo) com fórmula — label mesclado B:E, valor col F. */
    private int addSimDestaqueRowFormula(Sheet sheet, int row, String label, String cellRef,
            CellStyle valueStyle, CellStyle labelStyle) {
        Row r = sheet.createRow(row);
        Cell lbl = r.createCell(SIM_COL_LABEL_START);
        lbl.setCellValue("    " + label);
        lbl.setCellStyle(labelStyle);
        mergeSimRegion(sheet, row, SIM_COL_LABEL_START, SIM_COL_LABEL_END);
        fillSimRowCells(r, SIM_COL_LABEL_START, SIM_COL_LABEL_END, labelStyle);
        Cell val = r.createCell(SIM_COL_VALOR);
        val.setCellFormula(cellRef);
        val.setCellStyle(valueStyle);
        return row + 1;
    }

    /** Linha de destaque (fundo amarelo) — label mesclado B:E, valor col F. */
    private int addSimDestaqueRow(Sheet sheet, int row, String label, BigDecimal valor,
            CellStyle valueStyle, CellStyle labelStyle) {
        Row r = sheet.createRow(row);
        Cell lbl = r.createCell(SIM_COL_LABEL_START);
        lbl.setCellValue("    " + label);
        lbl.setCellStyle(labelStyle);
        mergeSimRegion(sheet, row, SIM_COL_LABEL_START, SIM_COL_LABEL_END);
        fillSimRowCells(r, SIM_COL_LABEL_START, SIM_COL_LABEL_END, labelStyle);
        Cell val = r.createCell(SIM_COL_VALOR);
        val.setCellValue(valor != null ? valor.doubleValue() : 0.0);
        val.setCellStyle(valueStyle);
        return row + 1;
    }

    /** Linha de alíquota efetiva da declaração (valor já em percentual). */
    private int addSimAliquotaDeclaracaoRow(Sheet sheet, int row, String label, BigDecimal aliquotaPercent,
            CellStyle labelStyle, CellStyle valueStyle) {
        Row r = sheet.createRow(row);
        Cell lbl = r.createCell(SIM_COL_LABEL_START);
        lbl.setCellValue("    " + label);
        lbl.setCellStyle(labelStyle);
        mergeSimRegion(sheet, row, SIM_COL_LABEL_START, SIM_COL_LABEL_END);
        fillSimRowCells(r, SIM_COL_LABEL_START, SIM_COL_LABEL_END, labelStyle);
        Cell val = r.createCell(SIM_COL_VALOR);
        if (aliquotaPercent != null) {
            val.setCellValue(aliquotaPercent.setScale(2, RoundingMode.HALF_UP).toPlainString() + "%");
        } else {
            val.setCellValue("0,00%");
        }
        val.setCellStyle(valueStyle);
        return row + 1;
    }

    /** Linha de detalhe — label mesclado B:E, valor col F. */
    private int addSimDetalhe(Sheet sheet, int row, String label, BigDecimal valor,
            CellStyle labelStyle, CellStyle valueStyle) {
        Row r = sheet.createRow(row);
        Cell lbl = r.createCell(SIM_COL_LABEL_START);
        lbl.setCellValue("    " + label);
        lbl.setCellStyle(labelStyle);
        mergeSimRegion(sheet, row, SIM_COL_LABEL_START, SIM_COL_LABEL_END);
        fillSimRowCells(r, SIM_COL_LABEL_START, SIM_COL_LABEL_END, labelStyle);
        Cell val = r.createCell(SIM_COL_VALOR);
        val.setCellValue(valor != null ? valor.doubleValue() : 0.0);
        val.setCellStyle(valueStyle);
        return row + 1;
    }

    /** Linha de detalhe com fórmula referenciando outra célula. */
    private int addSimDetalheFormula(Sheet sheet, int row, String label, String cellRef,
            CellStyle labelStyle, CellStyle valueStyle) {
        Row r = sheet.createRow(row);
        Cell lbl = r.createCell(SIM_COL_LABEL_START);
        lbl.setCellValue("    " + label);
        lbl.setCellStyle(labelStyle);
        mergeSimRegion(sheet, row, SIM_COL_LABEL_START, SIM_COL_LABEL_END);
        fillSimRowCells(r, SIM_COL_LABEL_START, SIM_COL_LABEL_END, labelStyle);
        Cell val = r.createCell(SIM_COL_VALOR);
        val.setCellFormula(cellRef);
        val.setCellStyle(valueStyle);
        return row + 1;
    }

    /** Layout simulação IRPF — quadro restrito às colunas B:F. */
    private static final int SIM_COL_LABEL_START = 1; // B
    private static final int SIM_COL_LABEL_END = 4;   // E (rótulos mesclados B:E)
    private static final int SIM_COL_COMPLETO = 4;    // E (comparativo — coluna Completo)
    private static final int SIM_COL_BASE = 3;        // D (tabela faixas)
    private static final int SIM_COL_ALIQ = 4;        // E (tabela faixas)
    private static final int SIM_COL_VALOR = 5;       // F (valores)

    private record SimValueRef(int row) {
        String cell() {
            return CellReference.convertNumToColString(SIM_COL_VALOR) + (row + 1);
        }

        int nextRow() {
            return row + 1;
        }
    }

    private record ModeloCalculoRefs(int nextRow, SimValueRef impostoDevido,
            SimValueRef impostoRestituir, SimValueRef saldoPagar,
            SimValueRef baseCalculo, SimValueRef impostoPago) {
    }

    private static String cellRef(int row) {
        return CellReference.convertNumToColString(SIM_COL_VALOR) + (row + 1);
    }

    private void mergeSimRegion(Sheet sheet, int row, int colStart, int colEnd) {
        if (colEnd > colStart) {
            sheet.addMergedRegion(new CellRangeAddress(row, row, colStart, colEnd));
        }
    }

    private void fillSimRowCells(Row row, int colStart, int colEnd, CellStyle style) {
        for (int c = colStart; c <= colEnd; c++) {
            Cell cell = row.getCell(c);
            if (cell == null) {
                cell = row.createCell(c);
            }
            cell.setCellStyle(style);
        }
    }

    private SimValueRef addSimRowValor(Sheet sheet, int row, String numero, String label, BigDecimal valor,
            CellStyle labelStyle, CellStyle valueStyle, boolean negrito) {
        addSimRow(sheet, row, numero, label, valor, labelStyle, valueStyle, negrito);
        return new SimValueRef(row);
    }

    private SimValueRef addSimRowFormula(Sheet sheet, int row, String numero, String label, String formula,
            CellStyle labelStyle, CellStyle valueStyle, boolean negrito) {
        Row r = sheet.createRow(row);
        Cell lbl = r.createCell(SIM_COL_LABEL_START);
        lbl.setCellValue(numero + ". " + label);
        lbl.setCellStyle(labelStyle);
        mergeSimRegion(sheet, row, SIM_COL_LABEL_START, SIM_COL_LABEL_END);
        fillSimRowCells(r, SIM_COL_LABEL_START, SIM_COL_LABEL_END, labelStyle);
        Cell valCell = r.createCell(SIM_COL_VALOR);
        valCell.setCellFormula(formula);
        valCell.setCellStyle(valueStyle);
        return new SimValueRef(row);
    }

    /** Infere qtd. dependentes a partir do total declarado quando a lista extraída está incompleta. */
    private int inferirQtdDependentes(IrpfDeclaracaoData data, IrParametrosAnuais params) {
        int fromList = data.getDependentes() != null ? data.getDependentes().size() : 0;
        if (data.getDeducaoDependentes() == null || params == null
                || params.getDeducaoDependente() == null
                || params.getDeducaoDependente().compareTo(BigDecimal.ZERO) <= 0) {
            return fromList;
        }
        int fromTotal = data.getDeducaoDependentes()
                .divide(params.getDeducaoDependente(), 0, RoundingMode.HALF_UP)
                .intValue();
        return Math.max(fromList, fromTotal);
    }

    /** Linha com rótulo mesclado B:E e valor opcional col F. */
    private int addSimRow(Sheet sheet, int row, String numero, String label, BigDecimal valor,
            CellStyle labelStyle, CellStyle valueStyle, boolean negrito) {
        Row r = sheet.createRow(row);
        Cell lbl = r.createCell(SIM_COL_LABEL_START);
        lbl.setCellValue(numero + ". " + label);
        lbl.setCellStyle(labelStyle);
        mergeSimRegion(sheet, row, SIM_COL_LABEL_START, SIM_COL_LABEL_END);
        fillSimRowCells(r, SIM_COL_LABEL_START, SIM_COL_LABEL_END, labelStyle);
        if (valor != null) {
            Cell valCell = r.createCell(SIM_COL_VALOR);
            valCell.setCellValue(valor.doubleValue());
            valCell.setCellStyle(valueStyle);
        }
        return row + 1;
    }

    /** Cabeçalho de seção — linha inteira mesclada B:F. */
    private int addSimSectionHeader(Sheet sheet, int row, String numero, String label, CellStyle labelStyle) {
        Row r = sheet.createRow(row);
        Cell c = r.createCell(SIM_COL_LABEL_START);
        c.setCellValue(numero + ". " + label);
        c.setCellStyle(labelStyle);
        mergeSimRegion(sheet, row, SIM_COL_LABEL_START, SIM_COL_VALOR);
        fillSimRowCells(r, SIM_COL_LABEL_START, SIM_COL_VALOR, labelStyle);
        return row + 1;
    }

    /** Nota explicativa — mesclada B:E, fonte preta. */
    private int addSimNota(Sheet sheet, int row, String nota, CellStyle style) {
        Row r = sheet.createRow(row);
        Cell c = r.createCell(SIM_COL_LABEL_START);
        c.setCellValue(nota);
        c.setCellStyle(style);
        mergeSimRegion(sheet, row, SIM_COL_LABEL_START, SIM_COL_LABEL_END);
        fillSimRowCells(r, SIM_COL_LABEL_START, SIM_COL_LABEL_END, style);
        return row + 1;
    }

    /** Título de bloco sem numeração — mesclado B:F. */
    private int addSimTituloBloco(Sheet sheet, int row, String titulo, CellStyle labelStyle) {
        Row r = sheet.createRow(row);
        Cell c = r.createCell(SIM_COL_LABEL_START);
        c.setCellValue(titulo);
        c.setCellStyle(labelStyle);
        mergeSimRegion(sheet, row, SIM_COL_LABEL_START, SIM_COL_VALOR);
        fillSimRowCells(r, SIM_COL_LABEL_START, SIM_COL_VALOR, labelStyle);
        return row + 1;
    }

    /** Retorna BigDecimal.ZERO se nulo. */
    private BigDecimal nvl(BigDecimal v) {
        return v != null ? v : BigDecimal.ZERO;
    }

    private int parseAnoCalendario(String ano) {
        if (ano == null || ano.isBlank()) {
            return 0;
        }
        try {
            return Integer.parseInt(ano.trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /** Usa valor do simulador (pagamentos granulares) quando positivo; senão fallback do RESUMO/planilha. */
    private BigDecimal valorOuFallback(BigDecimal preferido, BigDecimal fallback) {
        if (preferido != null && preferido.compareTo(BigDecimal.ZERO) > 0) {
            return preferido;
        }
        return nvl(fallback);
    }

    private BigDecimal despesasInstrucaoParaExibicao(SimuladorIrpfRequest request, IrpfDeclaracaoData data) {
        BigDecimal granular = somarInstrucaoRequest(request);
        if (granular.compareTo(BigDecimal.ZERO) > 0) {
            return granular;
        }
        if (request.getDespesasInstrucaoDeclarada() != null
                && request.getDespesasInstrucaoDeclarada().compareTo(BigDecimal.ZERO) > 0) {
            return request.getDespesasInstrucaoDeclarada();
        }
        return nvl(data.getDespesasInstrucao());
    }

    private BigDecimal somarInstrucaoRequest(SimuladorIrpfRequest request) {
        BigDecimal total = nvl(request.getDespesasInstrucaoTitular());
        if (request.getDespesasInstrucaoDependentes() != null) {
            for (BigDecimal v : request.getDespesasInstrucaoDependentes()) {
                total = total.add(nvl(v));
            }
        }
        if (request.getDespesasInstrucaoAlimentandos() != null) {
            for (BigDecimal v : request.getDespesasInstrucaoAlimentandos()) {
                total = total.add(nvl(v));
            }
        }
        return total;
    }

    // =============================================
    // ESTILOS DA SIMULAÇÃO IRPF
    // =============================================

    /** Título principal (fundo azul escuro, fonte branca, negrito, centralizado). */
    private CellStyle createSimTitleStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        font.setFontName("Arial");
        font.setFontHeightInPoints((short) 11);
        font.setColor(IndexedColors.WHITE.getIndex());
        style.setFont(font);
        style.setFillForegroundColor(IndexedColors.DARK_BLUE.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setAlignment(HorizontalAlignment.CENTER);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        return style;
    }

    /** Subtítulo abaixo do banner (NOME/CPF, tipo de tributação) — fundo branco, fonte preta, negrito. */
    private CellStyle createSimBannerSubtitleStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        font.setFontName("Arial");
        font.setFontHeightInPoints((short) 10);
        font.setColor(IndexedColors.BLACK.getIndex());
        style.setFont(font);
        style.setFillPattern(FillPatternType.NO_FILL);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        style.setWrapText(true);
        return style;
    }

    /** Label de linha principal (fundo lilás, fonte preta, negrito). */
    private CellStyle createSimLabelStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        font.setFontName("Arial");
        font.setFontHeightInPoints((short) 10);
        font.setColor(IndexedColors.BLACK.getIndex());
        style.setFont(font);
        style.setFillForegroundColor(IndexedColors.LIGHT_CORNFLOWER_BLUE.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        return style;
    }

    /** Valor numérico (fundo lilás, fonte preta, alinhado à direita). */
    private CellStyle createSimValueStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setFontName("Arial");
        font.setFontHeightInPoints((short) 10);
        font.setColor(IndexedColors.BLACK.getIndex());
        style.setFont(font);
        style.setFillForegroundColor(IndexedColors.LIGHT_CORNFLOWER_BLUE.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setAlignment(HorizontalAlignment.RIGHT);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        DataFormat fmt = workbook.createDataFormat();
        style.setDataFormat(fmt.getFormat("#,##0.00"));
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        return style;
    }

    /** Sub-label / notas (fonte preta, sem fundo). */
    private CellStyle createSimSubLabelStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setFontName("Arial");
        font.setFontHeightInPoints((short) 9);
        font.setColor(IndexedColors.BLACK.getIndex());
        style.setFont(font);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        style.setWrapText(true);
        return style;
    }

    /** Total — valor (fundo amarelo, fonte preta, alinhado à direita). */
    private CellStyle createSimTotalStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        font.setFontName("Arial");
        font.setFontHeightInPoints((short) 10);
        font.setColor(IndexedColors.BLACK.getIndex());
        style.setFont(font);
        style.setFillForegroundColor(IndexedColors.YELLOW.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setAlignment(HorizontalAlignment.RIGHT);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        DataFormat fmt = workbook.createDataFormat();
        style.setDataFormat(fmt.getFormat("#,##0.00"));
        style.setBorderBottom(BorderStyle.DOUBLE);
        style.setBorderTop(BorderStyle.DOUBLE);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        return style;
    }

    /** Total — rótulo (fundo amarelo, fonte preta, alinhado à esquerda). */
    private CellStyle createSimTotalLabelStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        font.setFontName("Arial");
        font.setFontHeightInPoints((short) 10);
        font.setColor(IndexedColors.BLACK.getIndex());
        style.setFont(font);
        style.setFillForegroundColor(IndexedColors.YELLOW.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setAlignment(HorizontalAlignment.LEFT);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        style.setBorderBottom(BorderStyle.DOUBLE);
        style.setBorderTop(BorderStyle.DOUBLE);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        return style;
    }

    /** Alíquota efetiva (fonte preta, negrito). */
    private CellStyle createSimAliquotaStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        font.setFontName("Arial");
        font.setFontHeightInPoints((short) 12);
        font.setColor(IndexedColors.BLACK.getIndex());
        style.setFont(font);
        style.setAlignment(HorizontalAlignment.RIGHT);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        style.setBorderBottom(BorderStyle.MEDIUM);
        style.setBorderTop(BorderStyle.MEDIUM);
        style.setBorderLeft(BorderStyle.MEDIUM);
        style.setBorderRight(BorderStyle.MEDIUM);
        return style;
    }

    /** Destaque verde claro para previdência complementar da planilha (simulação 2). */
    private CellStyle createSimHighlightGreenStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setFontName("Arial");
        font.setFontHeightInPoints((short) 10);
        font.setColor(IndexedColors.BLACK.getIndex());
        style.setFont(font);
        style.setFillForegroundColor(IndexedColors.LIGHT_GREEN.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setAlignment(HorizontalAlignment.RIGHT);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        DataFormat fmt = workbook.createDataFormat();
        style.setDataFormat(fmt.getFormat("#,##0.00"));
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        return style;
    }
}
