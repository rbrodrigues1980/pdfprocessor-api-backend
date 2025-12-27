package br.com.verticelabs.pdfprocessor.infrastructure.excel;

import br.com.verticelabs.pdfprocessor.application.excel.ExcelExportService;
import br.com.verticelabs.pdfprocessor.domain.exceptions.ExcelGenerationException;
import br.com.verticelabs.pdfprocessor.domain.model.DocumentType;
import br.com.verticelabs.pdfprocessor.domain.model.PayrollEntry;
import br.com.verticelabs.pdfprocessor.domain.model.Person;
import br.com.verticelabs.pdfprocessor.domain.repository.PayrollDocumentRepository;
import br.com.verticelabs.pdfprocessor.domain.repository.PayrollEntryRepository;
import br.com.verticelabs.pdfprocessor.interfaces.consolidation.dto.ConsolidatedResponse;
import br.com.verticelabs.pdfprocessor.interfaces.consolidation.dto.ConsolidationRow;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class ConsolidationExcelServiceImpl implements ExcelExportService {

    private final PayrollDocumentRepository documentRepository;
    private final PayrollEntryRepository entryRepository;
    private final br.com.verticelabs.pdfprocessor.application.tributacao.IrTributacaoService tributacaoService;

    @Override
    public Mono<byte[]> generateConsolidationExcel(Person person, ConsolidatedResponse consolidatedResponse,
            String filename) {
        // Primeiro, buscar entries de IR para a pessoa
        return buscarEntriesIncomeTax(person)
                .flatMap(incomeTaxEntries -> {
                    // Buscar tabelas de tributação para cada ano
                    java.util.Set<String> anos = consolidatedResponse.getAnos();
                    return buscarTabelasTributacao(anos)
                            .flatMap(tabelasTributacao -> {
                                return Mono.fromCallable(() -> {
                                    try (Workbook workbook = new XSSFWorkbook();
                                            ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                                        log.debug("Criando workbook Excel...");

                                        // Criar estilos
                                        CellStyle headerStyle = createHeaderStyle(workbook);
                                        CellStyle numberStyle = createNumberStyle(workbook);
                                        CellStyle defaultStyle = createDefaultStyle(workbook);
                                        CellStyle totalStyle = createTotalStyle(workbook);
                                        CellStyle infoStyle = createInfoStyle(workbook);

                                        // Ordenar anos em ordem crescente
                                        TreeSet<String> anosOrdenados = new TreeSet<>(consolidatedResponse.getAnos());
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

                                            // Adicionar quadro resumo de IR (se houver para este ano)
                                            currentRow = addIncomeTaxSummaryTable(sheet, ano, incomeTaxEntries,
                                                    consolidatedResponse, tabelasTributacao, currentRow, totalStyle,
                                                    numberStyle);

                                            // Ajustar largura das colunas
                                            autoSizeColumns(sheet,
                                                    consolidatedResponse.getRubricas().size() > 0
                                                            ? consolidatedResponse.getRubricas().get(0).getValores()
                                                                    .size()
                                                            : 0);

                                            // Congelar linha de cabeçalho (linha 2, índice 1)
                                            sheet.createFreezePane(0, 2); // Congela após linha 2 (cabeçalho)
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
                                }).subscribeOn(Schedulers.boundedElastic());
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

    /**
     * Calcula o imposto devido usando a tabela de tributação do banco.
     * Fórmula: Imposto = (Base × Alíquota) - Dedução
     */
    private BigDecimal calcularImpostoComTabelaBanco(BigDecimal baseCalculo, String ano,
            Map<String, java.util.List<br.com.verticelabs.pdfprocessor.domain.model.IrTabelaTributacao>> tabelasTributacao) {

        if (baseCalculo == null || baseCalculo.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO;
        }

        java.util.List<br.com.verticelabs.pdfprocessor.domain.model.IrTabelaTributacao> faixas = tabelasTributacao
                .get(ano);

        if (faixas == null || faixas.isEmpty()) {
            log.warn("Nenhuma tabela de tributação encontrada para ano: {}. Usando valores padrão (2016-2022).", ano);
            // Fallback para valores de 2016-2022
            return baseCalculo.multiply(new BigDecimal("0.275"))
                    .subtract(new BigDecimal("10432.32"))
                    .setScale(2, java.math.RoundingMode.HALF_UP)
                    .max(BigDecimal.ZERO);
        }

        // Encontrar a faixa correta
        for (br.com.verticelabs.pdfprocessor.domain.model.IrTabelaTributacao faixa : faixas) {
            BigDecimal limiteInferior = faixa.getLimiteInferior();
            BigDecimal limiteSuperior = faixa.getLimiteSuperior();

            boolean acimaDeLimiteInferior = baseCalculo.compareTo(limiteInferior) >= 0;
            boolean abaixoDeLimiteSuperior = limiteSuperior == null ||
                    baseCalculo.compareTo(limiteSuperior) <= 0;

            if (acimaDeLimiteInferior && abaixoDeLimiteSuperior) {
                BigDecimal aliquota = faixa.getAliquota();
                BigDecimal deducao = faixa.getDeducao();

                if (aliquota == null || aliquota.compareTo(BigDecimal.ZERO) == 0) {
                    return BigDecimal.ZERO; // Faixa isenta
                }

                BigDecimal imposto = baseCalculo.multiply(aliquota)
                        .subtract(deducao != null ? deducao : BigDecimal.ZERO)
                        .setScale(2, java.math.RoundingMode.HALF_UP);

                log.debug("Ano {}, Faixa {}: base={}, aliquota={}, deducao={}, imposto={}",
                        ano, faixa.getFaixa(), baseCalculo, aliquota, deducao, imposto);

                return imposto.max(BigDecimal.ZERO);
            }
        }

        // Se não encontrou faixa, usa a última
        br.com.verticelabs.pdfprocessor.domain.model.IrTabelaTributacao ultimaFaixa = faixas.get(faixas.size() - 1);
        BigDecimal aliquota = ultimaFaixa.getAliquota();
        BigDecimal deducao = ultimaFaixa.getDeducao();

        log.debug("Usando última faixa para ano {}: aliquota={}, deducao={}", ano, aliquota, deducao);

        return baseCalculo.multiply(aliquota != null ? aliquota : BigDecimal.ZERO)
                .subtract(deducao != null ? deducao : BigDecimal.ZERO)
                .setScale(2, java.math.RoundingMode.HALF_UP)
                .max(BigDecimal.ZERO);
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
        BigDecimal impostoDevidoEstudo = calcularImpostoComTabelaBanco(baseCalculoEstudo, ano, tabelasTributacao);

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
}
