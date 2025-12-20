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
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ConsolidationExcelServiceImpl implements ExcelExportService {

    private final PayrollDocumentRepository documentRepository;
    private final PayrollEntryRepository entryRepository;

    @Override
    public Mono<byte[]> generateConsolidationExcel(Person person, ConsolidatedResponse consolidatedResponse,
            String filename) {
        // Primeiro, buscar entries de IR para a pessoa
        return buscarEntriesIncomeTax(person)
                .flatMap(incomeTaxEntries -> {
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
                                currentRow = addMatrixData(sheet, consolidatedResponse, ano, currentRow, numberStyle,
                                        defaultStyle);

                                // Linha de totais mensais
                                currentRow = addMonthlyTotals(sheet, consolidatedResponse, ano, currentRow, totalStyle,
                                        numberStyle);

                                // Adicionar seção de Imposto Devido (se houver para este ano)
                                currentRow = addIncomeTaxSection(sheet, ano, incomeTaxEntries, currentRow, totalStyle,
                                        numberStyle);

                                // Ajustar largura das colunas
                                autoSizeColumns(sheet,
                                        consolidatedResponse.getRubricas().size() > 0
                                                ? consolidatedResponse.getRubricas().get(0).getValores().size()
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
                            currentRow = addConsolidatedData(consolidatedSheet, consolidatedResponse, anosOrdenados,
                                    currentRow, numberStyle, defaultStyle);

                            // Linha de totais consolidados
                            currentRow = addConsolidatedTotals(consolidatedSheet, consolidatedResponse, anosOrdenados,
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
                            throw new ExcelGenerationException("Erro ao gerar arquivo Excel: " + e.getMessage(), e);
                        }
                    }).subscribeOn(Schedulers.boundedElastic());
                });
    }

    /**
     * Busca todas as entries de declaração de IR para a pessoa.
     */
    private Mono<Map<String, PayrollEntry>> buscarEntriesIncomeTax(Person person) {
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

                    // Criar mapa: ano-calendário -> entry de "Saldo de Imposto a Pagar"
                    // A referência está no formato "2016-00", então extraímos o ano
                    Map<String, PayrollEntry> map = new HashMap<>();

                    for (PayrollEntry entry : entries) {
                        // Buscar entry de "Saldo de Imposto a Pagar" (IR_SALDO_IMPOSTO_A_PAGAR)
                        if ("IR_SALDO_IMPOSTO_A_PAGAR".equals(entry.getRubricaCodigo())) {

                            // Extrair ano da referência (formato "2016-00")
                            if (entry.getReferencia() != null && entry.getReferencia().contains("-")) {
                                String ano = entry.getReferencia().split("-")[0];
                                log.debug("Entry de Saldo de Imposto a Pagar encontrada para ano: {} (valor: {})",
                                        ano, entry.getValor());

                                map.put(ano, entry);
                            }
                        }
                    }

                    log.info("Mapeamento de Saldo de Imposto a Pagar por ano: {}",
                            map.keySet().stream()
                                    .map(ano -> ano + "=" + map.get(ano).getValor())
                                    .collect(Collectors.joining(", ")));

                    return map;
                })
                .onErrorResume(e -> {
                    log.warn("Erro ao buscar entries de IR (continuando sem dados de IR): {}", e.getMessage());
                    return Mono.just(Collections.emptyMap());
                });
    }

    /**
     * Adiciona seção de Saldo de Imposto a Pagar após a linha TOTAL.
     * Formato:
     * - Linha vazia (espaçamento)
     * - Linha: "SALDO DE IMPOSTO A PAGAR" | valor
     * - Linha: "Imposto Pago (novo calculo)" | (vazio)
     * - Linha: "Valor a restituir" | (vazio)
     */
    private int addIncomeTaxSection(Sheet sheet, String ano, Map<String, PayrollEntry> incomeTaxEntries,
            int startRow, CellStyle totalStyle, CellStyle numberStyle) {
        PayrollEntry saldoImpostoPagarEntry = incomeTaxEntries.get(ano);

        if (saldoImpostoPagarEntry == null || saldoImpostoPagarEntry.getValor() == null) {
            log.debug("Nenhum Saldo de Imposto a Pagar encontrado para ano: {}", ano);
            return startRow;
        }

        log.info("Adicionando seção de Saldo de Imposto a Pagar para ano {}: R$ {}", ano,
                saldoImpostoPagarEntry.getValor());

        // Linha vazia (espaçamento entre TOTAL e Saldo de Imposto a Pagar)
        sheet.createRow(startRow);

        // Linha 1: "SALDO DE IMPOSTO A PAGAR" | valor
        Row row1 = sheet.createRow(startRow + 1);
        Cell label1 = row1.createCell(1); // Coluna B
        label1.setCellValue("SALDO DE IMPOSTO A PAGAR");
        label1.setCellStyle(totalStyle);

        Cell value1 = row1.createCell(2); // Coluna C
        // Usar doubleValue() para POI
        value1.setCellValue(saldoImpostoPagarEntry.getValor().doubleValue());
        value1.setCellStyle(totalStyle);

        // Linha 2: "Imposto Pago (novo calculo)" | (vazio)
        Row row2 = sheet.createRow(startRow + 2);
        Cell label2 = row2.createCell(1); // Coluna B
        label2.setCellValue("Imposto Pago (novo calculo)");
        label2.setCellStyle(totalStyle);

        Cell value2 = row2.createCell(2); // Coluna C (vazio)
        value2.setCellStyle(totalStyle);

        // Linha 3: "Valor a restituir" | (vazio)
        Row row3 = sheet.createRow(startRow + 3);
        Cell label3 = row3.createCell(1); // Coluna B
        label3.setCellValue("Valor a restituir");
        label3.setCellStyle(totalStyle);

        Cell value3 = row3.createCell(2); // Coluna C (vazio)
        value3.setCellStyle(totalStyle);

        return startRow + 4;
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
        sheet.setColumnWidth(1, 12000); // Rubrica

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
