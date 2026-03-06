package br.com.verticelabs.pdfprocessor.application.documents;

import br.com.verticelabs.pdfprocessor.domain.exceptions.InvalidPdfException;
import br.com.verticelabs.pdfprocessor.domain.model.*;
import br.com.verticelabs.pdfprocessor.domain.repository.PayrollDocumentRepository;
import br.com.verticelabs.pdfprocessor.domain.repository.PayrollEntryRepository;


import br.com.verticelabs.pdfprocessor.domain.service.AiPdfExtractionService;
import br.com.verticelabs.pdfprocessor.domain.service.CrossValidationService;
import br.com.verticelabs.pdfprocessor.domain.service.ExtractionValidationService;
import br.com.verticelabs.pdfprocessor.domain.service.GridFsService;
import br.com.verticelabs.pdfprocessor.domain.service.ITextIncomeTaxService;
import br.com.verticelabs.pdfprocessor.domain.service.MonthYearDetectionService;
import br.com.verticelabs.pdfprocessor.domain.service.PdfService;
import br.com.verticelabs.pdfprocessor.infrastructure.ai.GeminiResponseParser;
import br.com.verticelabs.pdfprocessor.infrastructure.pdf.*;
import br.com.verticelabs.pdfprocessor.interfaces.documents.dto.ProcessDocumentResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.io.ByteArrayInputStream;
import java.time.Instant;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentProcessUseCase {

    private final PayrollDocumentRepository documentRepository;
    private final PayrollEntryRepository entryRepository;
    private final GridFsService gridFsService;
    private final PdfService pdfService;
    private final AiPdfExtractionService aiPdfExtractionService;
    private final ExtractionValidationService validationService;
    private final CrossValidationService crossValidationService;

    private final MonthYearDetectionService monthYearDetectionService;
    private final ITextIncomeTaxService iTextIncomeTaxService;
    private final PdfLineParser lineParser;
    private final PdfNormalizer normalizer;
    private final RubricaValidator rubricaValidator;

    // Limite mínimo de caracteres para considerar que o PDF tem texto suficiente
    // PDFs abaixo deste limite são considerados escaneados e usarão Gemini AI
    private static final int MIN_TEXT_LENGTH_FOR_PDF = 100;

    // Proporção mínima de caracteres alfanuméricos para considerar texto legível.
    // PDFs com fontes sem mapeamento Unicode geram texto com alta proporção de símbolos.
    private static final double MIN_ALPHANUMERIC_RATIO = 0.5;

    // Palavras-chave que indicam que o texto é de um contracheque legível
    private static final String[] PAYROLL_KEYWORDS = {
            "SALARIO", "DESCONTO", "VENCIMENTO", "LIQUIDO", "BRUTO",
            "INSS", "IRRF", "FGTS", "RUBRICA", "PROVENTOS", "NOME",
            "CPF", "MATRICULA", "COMPETENCIA", "REMUNERACAO", "FERIAS",
            "GRATIFICACAO", "ADICIONAL", "CONTRIBUICAO", "PENSAO",
            "FUNCEF", "CAIXA", "PAGAMENTO", "FOLHA"
    };

    /**
     * Processa um documento PDF previamente enviado.
     * O processamento é assíncrono e não bloqueante.
     */
    public Mono<ProcessDocumentResponse> processDocument(String documentId) {
        log.info("=== INÍCIO DO PROCESSAMENTO ===");
        log.info("DocumentId: {}", documentId);

        return documentRepository.findById(documentId)
                .switchIfEmpty(Mono.error(new IllegalArgumentException("Documento não encontrado: " + documentId)))
                .flatMap(document -> {
                    // Verificar se já foi processado
                    if (document.getStatus() != DocumentStatus.PENDING) {
                        log.warn("Documento {} já foi processado. Status atual: {}", documentId, document.getStatus());
                        return Mono.error(new IllegalStateException(
                                "Documento já foi processado. Status atual: " + document.getStatus()));
                    }

                    // Atualizar status para PROCESSING
                    document.setStatus(DocumentStatus.PROCESSING);
                    return documentRepository.save(document)
                            .flatMap(savedDoc -> {
                                log.info("Status atualizado para PROCESSING. Iniciando processamento...");
                                // Retornar resposta imediata
                                ProcessDocumentResponse immediateResponse = ProcessDocumentResponse.builder()
                                        .documentId(documentId)
                                        .status(DocumentStatus.PROCESSING)
                                        .message("Processamento iniciado.")
                                        .build();

                                // Processar em background
                                processDocumentAsync(savedDoc)
                                        .subscribe(
                                                result -> log.info("Processamento concluído com sucesso. Entries: {}",
                                                        result),
                                                error -> {
                                                    log.error("Erro no processamento assíncrono", error);
                                                    // Atualizar status para ERROR
                                                    documentRepository.findById(documentId)
                                                            .flatMap(doc -> {
                                                                doc.setStatus(DocumentStatus.ERROR);
                                                                doc.setErro(error.getMessage());
                                                                return documentRepository.save(doc);
                                                            })
                                                            .subscribe();
                                                });

                                return Mono.just(immediateResponse);
                            });
                });
    }

    /**
     * Processa o documento de forma assíncrona.
     */
    private Mono<Long> processDocumentAsync(PayrollDocument document) {
        log.info("Processando documento {} (tipo: {})", document.getId(), document.getTipo());

        final long startTime = System.currentTimeMillis();
        addInfoEvent(document, null, ProcessingEventType.PROCESSING_STARTED,
                "Processamento iniciado. Tipo: " + document.getTipo());

        // Documentos de declaração de IR têm informações específicas que precisam ser
        // extraídas
        if (document.getTipo() == DocumentType.INCOME_TAX) {
            log.info("Documento é declaração de IR. Extraindo informações específicas...");
            return processIncomeTaxDocument(document);
        }

        return loadPdfFromGridFs(document.getOriginalFileId())
                .flatMap(pdfBytes -> {
                    log.info("PDF carregado do GridFS. Tamanho: {} bytes", pdfBytes.length);
                    return processPages(document, pdfBytes);
                })
                .flatMap(entriesCount -> {
                    long elapsed = System.currentTimeMillis() - startTime;
                    log.info("Processamento concluído. Total de entries: {}", entriesCount);

                    addInfoEvent(document, null, ProcessingEventType.PROCESSING_COMPLETED,
                            String.format("Processamento concluído. %d entries salvas em %dms.", entriesCount, elapsed),
                            Map.of("totalEntries", entriesCount, "processingTimeMs", elapsed));

                    // Atualizar documento com status PROCESSED
                    document.setStatus(DocumentStatus.PROCESSED);
                    document.setDataProcessamento(Instant.now());
                    document.setTotalEntries(entriesCount);

                    return documentRepository.save(document)
                            .thenReturn(entriesCount);
                })
                .onErrorResume(error -> {
                    long elapsed = System.currentTimeMillis() - startTime;
                    log.error("Erro ao processar documento", error);

                    addErrorEvent(document, null, ProcessingEventType.PROCESSING_FAILED,
                            "Erro no processamento: " + error.getMessage(),
                            Map.of("errorMessage", error.getMessage(), "processingTimeMs", elapsed));

                    document.setStatus(DocumentStatus.ERROR);
                    document.setErro(error.getMessage());
                    return documentRepository.save(document)
                            .then(Mono.error(error));
                });
    }

    /**
     * Carrega o PDF do GridFS.
     */
    private Mono<byte[]> loadPdfFromGridFs(String fileId) {
        return gridFsService.retrieveFile(fileId)
                .flatMap(inputStream -> {
                    try {
                        byte[] bytes = inputStream.readAllBytes();
                        inputStream.close();
                        return Mono.just(bytes);
                    } catch (Exception e) {
                        return Mono.error(new InvalidPdfException("Erro ao ler PDF do GridFS: " + e.getMessage()));
                    }
                });
    }

    /**
     * Processa todas as páginas do documento e extrai as rubricas.
     */
    private Mono<Long> processPages(PayrollDocument document, byte[] pdfBytes) {
        return pdfService.getTotalPages(new ByteArrayInputStream(pdfBytes))
                .flatMap(totalPages -> {
                    log.info("PDF possui {} páginas. Processando cada página...", totalPages);

                    addInfoEvent(document, null, ProcessingEventType.PROCESSING_STARTED,
                            String.format("PDF com %d páginas. Iniciando extração.", totalPages),
                            Map.of("totalPages", totalPages));

                    if (totalPages == 0) {
                        return Mono.just(0L);
                    }

                    // Primeiro, verificar se alguma página precisa de extração de texto de imagem
                    // Se sim, processar sequencialmente para evitar conflito de memória do
                    // Tesseract
                    return checkIfNeedsImageTextExtraction(pdfBytes, totalPages)
                            .flatMap(needsImageTextExtraction -> {
                                log.info("PDF precisa de extração de texto de imagem: {}. Processamento será {}",
                                        needsImageTextExtraction, needsImageTextExtraction ? "sequencial" : "paralelo");

                                // Se precisa de extração de texto de imagem, processar sequencialmente
                                // (concatMap)
                                // Caso contrário, processar em paralelo (flatMap)
                                Flux<PageResult> pagesFlux;
                                if (needsImageTextExtraction) {
                                    pagesFlux = Flux.range(1, totalPages)
                                            .concatMap(pageNumber -> {
                                                log.debug("Processando página {}/{} (sequencial - extração de imagem)",
                                                        pageNumber, totalPages);
                                                return processPageWithMetadata(document, pdfBytes, pageNumber,
                                                        totalPages)
                                                        .flatMap(pageResult -> saveIntermediateProgress(document)
                                                                .thenReturn(pageResult));
                                            });
                                } else {
                                    pagesFlux = Flux.range(1, totalPages)
                                            .flatMap(pageNumber -> {
                                                log.debug("Processando página {}/{} (paralelo)", pageNumber,
                                                        totalPages);
                                                return processPageWithMetadata(document, pdfBytes, pageNumber,
                                                        totalPages)
                                                        .flatMap(pageResult -> saveIntermediateProgress(document)
                                                                .thenReturn(pageResult));
                                            });
                                }

                                return pagesFlux.collectList();
                            })
                            .flatMap(pageResults -> {
                                // Pass 1: Consolidar entries de todas as páginas
                                List<PayrollEntry> allEntries = new ArrayList<>();
                                List<Integer> failedPages = new ArrayList<>();

                                log.info(
                                        "════════════════════════════════════════════════════════════════════════════════");
                                log.info("📊 PASS 1 — CONSOLIDANDO ENTRIES DE TODAS AS PÁGINAS:");
                                log.info(
                                        "════════════════════════════════════════════════════════════════════════════════");

                                for (int i = 0; i < pageResults.size(); i++) {
                                    PageResult pageResult = pageResults.get(i);
                                    int pageNum = i + 1;
                                    int pageEntriesCount = pageResult.getEntries().size();
                                    log.info("Página {} contribuiu com {} entries", pageNum, pageEntriesCount);
                                    allEntries.addAll(pageResult.getEntries());

                                    if (pageEntriesCount == 0) {
                                        failedPages.add(pageNum);
                                    }
                                }

                                log.info("📊 Pass 1: {} entries extraídas. Páginas com 0 entries: {}",
                                        allEntries.size(), failedPages);

                                // Pass 2: Multi-page retry para páginas que falharam
                                if (!failedPages.isEmpty() && aiPdfExtractionService.isEnabled()) {
                                    log.info(
                                            "════════════════════════════════════════════════════════════════════════════════");
                                    log.info("🔄 PASS 2 — MULTI-PAGE RETRY para {} páginas com 0 entries: {}",
                                            failedPages.size(), failedPages);
                                    log.info(
                                            "════════════════════════════════════════════════════════════════════════════════");

                                    return executeMultiPageRetry(document, pdfBytes, failedPages, totalPages, allEntries)
                                            .flatMap(combinedEntries -> {
                                                log.info(
                                                        "════════════════════════════════════════════════════════════════════════════════");
                                                log.info("📊 TOTAL FINAL (Pass 1 + Pass 2): {} entries", combinedEntries.size());
                                                log.info(
                                                        "════════════════════════════════════════════════════════════════════════════════");

                                                addInfoEvent(document, null, ProcessingEventType.ENTRIES_EXTRACTED,
                                                        String.format("%d rubricas extraídas de %d páginas (incluindo multi-page retry).",
                                                                combinedEntries.size(), totalPages),
                                                        Map.of("totalExtracted", combinedEntries.size(),
                                                                "totalPages", totalPages,
                                                                "failedPagesRetried", failedPages.size()));

                                                return saveIntermediateProgress(document)
                                                        .then(processEntries(document, combinedEntries));
                                            });
                                }

                                log.info(
                                        "════════════════════════════════════════════════════════════════════════════════");
                                log.info("📊 TOTAL DE ENTRIES EXTRAÍDAS DE TODAS AS PÁGINAS: {}", allEntries.size());
                                log.info(
                                        "════════════════════════════════════════════════════════════════════════════════");

                                addInfoEvent(document, null, ProcessingEventType.ENTRIES_EXTRACTED,
                                        String.format("%d rubricas extraídas de %d páginas.", allEntries.size(), totalPages),
                                        Map.of("totalExtracted", allEntries.size(), "totalPages", totalPages));

                                return saveIntermediateProgress(document)
                                        .then(processEntries(document, allEntries));
                            });
                });
    }

    /**
     * Processa as entries extraídas (validação e persistência).
     */
    private Mono<Long> processEntries(PayrollDocument document, List<PayrollEntry> allEntries) {
        if (allEntries.isEmpty()) {
            log.warn("Nenhuma rubrica foi extraída do documento");
            addWarnEvent(document, null, ProcessingEventType.ENTRIES_EXTRACTED,
                    "Nenhuma rubrica foi extraída do documento.", Map.of("totalExtracted", 0));
            return Mono.just(0L);
        }

        log.info("=== VALIDAÇÃO DE RUBRICAS ===");
        log.info("Total de entries extraídas: {}", allEntries.size());

        // Mostrar códigos das rubricas extraídas
        if (!allEntries.isEmpty()) {
            String codigos = allEntries.stream()
                    .map(PayrollEntry::getRubricaCodigo)
                    .distinct()
                    .collect(java.util.stream.Collectors.joining(", "));
            log.info("Códigos de rubricas extraídas: {}", codigos);
        }

        // Validar cada entry antes de salvar, rastreando rubricas não encontradas
        List<String> rubricasNotFound = java.util.Collections.synchronizedList(new ArrayList<>());

        return Flux.fromIterable(allEntries)
                .flatMap(entry -> validateEntry(entry)
                        .switchIfEmpty(Mono.defer(() -> {
                            rubricasNotFound.add(entry.getRubricaCodigo() +
                                    (entry.getRubricaDescricao() != null ? " - " + entry.getRubricaDescricao() : ""));
                            return Mono.empty();
                        })))
                .collectList()
                .flatMap(validEntries -> {
                    int ignoredCount = allEntries.size() - validEntries.size();

                    log.info("=== RESULTADO DA VALIDAÇÃO ===");
                    log.info("Entries extraídas: {}", allEntries.size());
                    log.info("Entries válidas (rubrica encontrada no banco): {}", validEntries.size());
                    log.info("Entries ignoradas (rubrica não encontrada): {}", ignoredCount);

                    // Emitir eventos de rubricas não encontradas (agrupadas por código único)
                    if (!rubricasNotFound.isEmpty()) {
                        // Agrupar por código para não ter eventos duplicados
                        Map<String, Long> rubricaCounts = rubricasNotFound.stream()
                                .collect(java.util.stream.Collectors.groupingBy(r -> r, java.util.stream.Collectors.counting()));

                        for (Map.Entry<String, Long> rubricaEntry : rubricaCounts.entrySet()) {
                            addWarnEvent(document, null, ProcessingEventType.RUBRICA_NOT_FOUND,
                                    String.format("Rubrica não cadastrada: %s (%dx)",
                                            rubricaEntry.getKey(), rubricaEntry.getValue()),
                                    Map.of("rubrica", rubricaEntry.getKey(), "occurrences", rubricaEntry.getValue()));
                        }
                    }

                    if (validEntries.isEmpty()) {
                        log.warn("⚠️ Nenhuma entry válida após validação de rubricas. " +
                                "Verifique se as rubricas estão cadastradas na API 1 (Rubricas).");
                        addWarnEvent(document, null, ProcessingEventType.ENTRIES_SAVED,
                                String.format("Nenhuma entry salva. %d extraídas, %d ignoradas (rubricas não cadastradas).",
                                        allEntries.size(), ignoredCount),
                                Map.of("totalExtracted", allEntries.size(), "totalValid", 0,
                                        "totalIgnored", ignoredCount));
                        return Mono.just(0L);
                    }

                    // Mostrar códigos das rubricas válidas
                    String codigosValidos = validEntries.stream()
                            .map(PayrollEntry::getRubricaCodigo)
                            .distinct()
                            .collect(java.util.stream.Collectors.joining(", "));
                    log.info("Códigos de rubricas válidas: {}", codigosValidos);

                    log.info("════════════════════════════════════════════════════════════════════════════════");
                    log.info("💾 SALVANDO {} ENTRIES VÁLIDAS NO BANCO DE DADOS:", validEntries.size());
                    log.info("════════════════════════════════════════════════════════════════════════════════");

                    // Mostrar todas as entries que serão salvas
                    for (int idx = 0; idx < validEntries.size(); idx++) {
                        PayrollEntry entry = validEntries.get(idx);
                        log.info(
                                "Entry[{}]: código=[{}], descrição=[{}], valor=[{}], referência=[{}], origem=[{}], página=[{}]",
                                idx + 1, entry.getRubricaCodigo(), entry.getRubricaDescricao(), entry.getValor(),
                                entry.getReferencia(), entry.getOrigem(), entry.getPagina());
                    }

                    log.info("════════════════════════════════════════════════════════════════════════════════");

                    final int totalExtracted = allEntries.size();
                    final int totalIgnored = ignoredCount;

                    return entryRepository.saveAll(Flux.fromIterable(validEntries))
                            .count()
                            .doOnNext(count -> {
                                log.info(
                                        "════════════════════════════════════════════════════════════════════════════════");
                                log.info("✅ {} ENTRIES SALVAS COM SUCESSO NO MONGODB", count);
                                log.info(
                                        "════════════════════════════════════════════════════════════════════════════════");

                                addInfoEvent(document, null, ProcessingEventType.ENTRIES_SAVED,
                                        String.format("%d entries salvas. %d extraídas, %d ignoradas (rubricas não cadastradas).",
                                                count, totalExtracted, totalIgnored),
                                        Map.of("totalSaved", count, "totalExtracted", totalExtracted,
                                                "totalIgnored", totalIgnored));
                            });
                });
    }

    /**
     * Resultado do processamento de uma página (apenas entries).
     */
    private static class PageResult {
        private final List<PayrollEntry> entries;

        public PageResult(List<PayrollEntry> entries) {
            this.entries = entries;
        }

        public List<PayrollEntry> getEntries() {
            return entries;
        }
    }

    /**
     * Pass 2: Multi-page retry para páginas que não extraíram nenhuma rubrica.
     * Para cada página que falhou, tenta:
     * 1. Extração com prompt para página parcial (CONTRACHEQUE_EXTRACTION_PARTIAL)
     * 2. Se ainda falhar, extração multi-page com página anterior + falha (duas imagens)
     *
     * As entries do retry são adicionadas ao resultado do Pass 1, com deduplicação.
     */
    private Mono<List<PayrollEntry>> executeMultiPageRetry(PayrollDocument document, byte[] pdfBytes,
                                                            List<Integer> failedPages, int totalPages,
                                                            List<PayrollEntry> pass1Entries) {

        return Flux.fromIterable(failedPages)
                .concatMap(failedPage -> {
                    log.info("🔄 Multi-page retry para página {}...", failedPage);

                    addInfoEvent(document, failedPage, ProcessingEventType.MULTIPAGE_RETRY_STARTED,
                            String.format("Retry multi-page iniciado para página %d.", failedPage));

                    String origem = determinePageOrigin(document, failedPage);
                    long retryStart = System.currentTimeMillis();

                    // Estratégia 1: Tentar extração com prompt para página parcial
                    return aiPdfExtractionService.extractPayrollDataPartialPage(pdfBytes, failedPage)
                            .flatMap(jsonResponse -> {
                                if (jsonResponse == null || jsonResponse.trim().isEmpty()) {
                                    return Mono.just(new ArrayList<PayrollEntry>());
                                }

                                GeminiResponseParser.ParsedPayrollData parsedData =
                                        GeminiResponseParser.parsePayrollResponse(
                                                jsonResponse, document.getId(),
                                                document.getTenantId(), origem, failedPage);

                                if (parsedData != null && !parsedData.getEntries().isEmpty()) {
                                    long elapsed = System.currentTimeMillis() - retryStart;
                                    log.info("✅ Página parcial {}: {} rubricas extraídas em {}ms",
                                            failedPage, parsedData.getEntries().size(), elapsed);
                                    addInfoEvent(document, failedPage, ProcessingEventType.MULTIPAGE_RETRY_COMPLETED,
                                            String.format("Extração parcial OK: %d rubricas em %dms.",
                                                    parsedData.getEntries().size(), elapsed),
                                            Map.of("rubricasCount", parsedData.getEntries().size(),
                                                    "processingTimeMs", elapsed, "strategy", "partial_page"));
                                    return Mono.just(parsedData.getEntries());
                                }

                                // Estratégia 2: Multi-page com página anterior + página que falhou
                                return tryMultiPageWithAdjacentPages(document, pdfBytes, failedPage,
                                        totalPages, origem, retryStart);
                            })
                            .onErrorResume(error -> {
                                log.warn("⚠️ Retry parcial falhou para página {}: {}. Tentando multi-page...",
                                        failedPage, error.getMessage());
                                long retryStart2 = System.currentTimeMillis();
                                return tryMultiPageWithAdjacentPages(document, pdfBytes, failedPage,
                                        totalPages, origem, retryStart2);
                            });
                })
                .collectList()
                .map(retryResultsList -> {
                    // Combinar Pass 1 + todos os resultados do Pass 2
                    List<PayrollEntry> combinedEntries = new ArrayList<>(pass1Entries);
                    int pass2Count = 0;

                    for (List<PayrollEntry> retryEntries : retryResultsList) {
                        combinedEntries.addAll(retryEntries);
                        pass2Count += retryEntries.size();
                    }

                    log.info("📊 Pass 2 adicionou {} entries. Total combinado: {} (Pass 1: {} + Pass 2: {})",
                            pass2Count, combinedEntries.size(), pass1Entries.size(), pass2Count);

                    // Deduplicar entries
                    List<PayrollEntry> deduplicatedEntries = deduplicateEntries(combinedEntries);
                    if (deduplicatedEntries.size() < combinedEntries.size()) {
                        log.info("🔄 Deduplicação removeu {} entries duplicadas. Final: {}",
                                combinedEntries.size() - deduplicatedEntries.size(), deduplicatedEntries.size());
                    }

                    return deduplicatedEntries;
                });
    }

    /**
     * Tenta extração multi-page combinando a página que falhou com suas adjacentes.
     * Tenta primeiro com a página anterior (N-1 + N), depois com a próxima (N + N+1).
     */
    private Mono<List<PayrollEntry>> tryMultiPageWithAdjacentPages(PayrollDocument document, byte[] pdfBytes,
                                                                    int failedPage, int totalPages,
                                                                    String origem, long retryStart) {
        // Tentar com página anterior primeiro (mais provável — a continuação vem da anterior)
        if (failedPage > 1) {
            List<Integer> pages = List.of(failedPage - 1, failedPage);
            log.info("🔄 Tentando multi-page com páginas {} para recuperar dados da página {}...",
                    pages, failedPage);

            return aiPdfExtractionService.extractPayrollDataMultiPage(pdfBytes, pages)
                    .flatMap(jsonResponse -> parseMultiPageResponse(document, jsonResponse,
                            failedPage, pages, origem, retryStart))
                    .onErrorResume(error -> {
                        log.warn("⚠️ Multi-page com páginas {}-{} falhou: {}",
                                failedPage - 1, failedPage, error.getMessage());

                        // Tentar com a próxima página
                        if (failedPage < totalPages) {
                            return tryMultiPageWithNextPage(document, pdfBytes, failedPage,
                                    totalPages, origem, retryStart);
                        }

                        long elapsed = System.currentTimeMillis() - retryStart;
                        addWarnEvent(document, failedPage, ProcessingEventType.MULTIPAGE_RETRY_FAILED,
                                String.format("Multi-page retry falhou para página %d após %dms.", failedPage, elapsed),
                                Map.of("processingTimeMs", elapsed));
                        return Mono.just(new ArrayList<>());
                    });
        } else if (failedPage < totalPages) {
            return tryMultiPageWithNextPage(document, pdfBytes, failedPage,
                    totalPages, origem, retryStart);
        }

        long elapsed = System.currentTimeMillis() - retryStart;
        addWarnEvent(document, failedPage, ProcessingEventType.MULTIPAGE_RETRY_FAILED,
                String.format("Multi-page retry não aplicável para página %d (sem adjacentes).", failedPage),
                Map.of("processingTimeMs", elapsed));
        return Mono.just(new ArrayList<>());
    }

    /**
     * Tenta multi-page com a próxima página (failedPage + failedPage+1).
     */
    private Mono<List<PayrollEntry>> tryMultiPageWithNextPage(PayrollDocument document, byte[] pdfBytes,
                                                               int failedPage, int totalPages,
                                                               String origem, long retryStart) {
        if (failedPage >= totalPages) {
            return Mono.just(new ArrayList<>());
        }

        List<Integer> pages = List.of(failedPage, failedPage + 1);
        log.info("🔄 Tentando multi-page com páginas {} para recuperar dados da página {}...",
                pages, failedPage);

        return aiPdfExtractionService.extractPayrollDataMultiPage(pdfBytes, pages)
                .flatMap(jsonResponse -> parseMultiPageResponse(document, jsonResponse,
                        failedPage, pages, origem, retryStart))
                .onErrorResume(error -> {
                    long elapsed = System.currentTimeMillis() - retryStart;
                    log.warn("⚠️ Multi-page com páginas {}-{} também falhou: {}",
                            failedPage, failedPage + 1, error.getMessage());
                    addWarnEvent(document, failedPage, ProcessingEventType.MULTIPAGE_RETRY_FAILED,
                            String.format("Multi-page retry falhou para página %d após %dms: %s",
                                    failedPage, elapsed, error.getMessage()),
                            Map.of("processingTimeMs", elapsed, "errorMessage", error.getMessage()));
                    return Mono.just(new ArrayList<>());
                });
    }

    /**
     * Parseia resposta do multi-page extraction.
     * A resposta pode ser um JSON array (múltiplos contracheques) ou um JSON object (único).
     */
    private Mono<List<PayrollEntry>> parseMultiPageResponse(PayrollDocument document, String jsonResponse,
                                                             int failedPage, List<Integer> pages,
                                                             String origem, long retryStart) {
        long elapsed = System.currentTimeMillis() - retryStart;

        if (jsonResponse == null || jsonResponse.trim().isEmpty()) {
            log.warn("⚠️ Multi-page retornou vazio para páginas {}", pages);
            addWarnEvent(document, failedPage, ProcessingEventType.MULTIPAGE_RETRY_FAILED,
                    String.format("Multi-page retornou vazio para páginas %s (%dms).", pages, elapsed),
                    Map.of("processingTimeMs", elapsed, "pages", pages.toString()));
            return Mono.just(new ArrayList<>());
        }

        log.info("✅ Multi-page retornou {} chars de JSON para páginas {}", jsonResponse.length(), pages);

        List<PayrollEntry> multiPageEntries = new ArrayList<>();
        String trimmedJson = jsonResponse.trim();

        try {
            if (trimmedJson.startsWith("[")) {
                // Array de contracheques — parsear cada um
                com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
                com.fasterxml.jackson.databind.JsonNode arrayNode = mapper.readTree(trimmedJson);

                if (arrayNode.isArray()) {
                    for (com.fasterxml.jackson.databind.JsonNode itemNode : arrayNode) {
                        String itemJson = mapper.writeValueAsString(itemNode);
                        GeminiResponseParser.ParsedPayrollData parsedData =
                                GeminiResponseParser.parsePayrollResponse(
                                        itemJson, document.getId(),
                                        document.getTenantId(), origem, failedPage);
                        if (parsedData != null && !parsedData.getEntries().isEmpty()) {
                            multiPageEntries.addAll(parsedData.getEntries());
                        }
                    }
                }
            } else {
                // Objeto único — parsear normalmente
                GeminiResponseParser.ParsedPayrollData parsedData =
                        GeminiResponseParser.parsePayrollResponse(
                                trimmedJson, document.getId(),
                                document.getTenantId(), origem, failedPage);
                if (parsedData != null && !parsedData.getEntries().isEmpty()) {
                    multiPageEntries.addAll(parsedData.getEntries());
                }
            }
        } catch (Exception e) {
            log.error("❌ Erro ao parsear resposta multi-page para páginas {}: {}", pages, e.getMessage());
        }

        if (!multiPageEntries.isEmpty()) {
            log.info("📊 Multi-page páginas {}: {} rubricas recuperadas em {}ms",
                    pages, multiPageEntries.size(), elapsed);
            addInfoEvent(document, failedPage, ProcessingEventType.MULTIPAGE_RETRY_COMPLETED,
                    String.format("Multi-page recovery OK: %d rubricas de páginas %s em %dms.",
                            multiPageEntries.size(), pages, elapsed),
                    Map.of("rubricasCount", multiPageEntries.size(), "processingTimeMs", elapsed,
                            "pages", pages.toString(), "strategy", "multi_page"));
        } else {
            log.warn("⚠️ Multi-page não recuperou rubricas de páginas {}", pages);
            addWarnEvent(document, failedPage, ProcessingEventType.MULTIPAGE_RETRY_FAILED,
                    String.format("Multi-page não recuperou rubricas de páginas %s (%dms).", pages, elapsed),
                    Map.of("processingTimeMs", elapsed, "pages", pages.toString()));
        }

        return Mono.just(multiPageEntries);
    }

    /**
     * Deduplica entries por chave composta (rubricaCodigo + referencia + valor).
     * Se houver duplicatas, mantém a que veio de página mais alta (provável multi-page retry,
     * que tende a ter contexto mais completo).
     */
    private List<PayrollEntry> deduplicateEntries(List<PayrollEntry> entries) {
        Map<String, PayrollEntry> uniqueEntries = new java.util.LinkedHashMap<>();

        for (PayrollEntry entry : entries) {
            String key = entry.getRubricaCodigo() + "|" +
                    (entry.getReferencia() != null ? entry.getReferencia() : "null") + "|" +
                    (entry.getValor() != null ? entry.getValor().toPlainString() : "null");

            if (uniqueEntries.containsKey(key)) {
                // Se duplicata, manter a de página mais alta (provavelmente do multi-page retry)
                PayrollEntry existing = uniqueEntries.get(key);
                if (entry.getPagina() > existing.getPagina()) {
                    uniqueEntries.put(key, entry);
                }
            } else {
                uniqueEntries.put(key, entry);
            }
        }

        return new ArrayList<>(uniqueEntries.values());
    }

    /**
     * Processa uma página específica e retorna as entries extraídas + metadados.
     * Para páginas com texto ilegível (escaneadas ou com fontes sem mapeamento Unicode),
     * usa extração JSON estruturada do Gemini Vision (Fase 2).
     * Para páginas digitais com texto legível, usa o parser regex tradicional.
     */
    private Mono<PageResult> processPageWithMetadata(PayrollDocument document, byte[] pdfBytes,
            int pageNumber, int totalPages) {
        // Tentar extrair texto normalmente primeiro
        return pdfService.extractTextFromPage(new ByteArrayInputStream(pdfBytes), pageNumber)
                .flatMap(pageText -> {
                    // Se o texto extraído for ilegível (muito curto OU com fontes sem Unicode mapping),
                    // tentar usar Gemini AI com JSON estruturado
                    if (!isTextReadable(pageText)) {
                        int textLen = pageText != null ? pageText.trim().length() : 0;
                        log.info(
                                "🔍 Texto extraído insuficiente ou ilegível ({} caracteres) na página {}. Tentando Gemini AI (JSON estruturado)...",
                                textLen, pageNumber);

                        addWarnEvent(document, pageNumber, ProcessingEventType.TEXT_UNREADABLE,
                                String.format("Texto ilegível (%d chars). Fontes sem mapeamento Unicode ou PDF escaneado.", textLen),
                                Map.of("textLength", textLen));

                        // Verificar se Gemini está habilitado
                        if (aiPdfExtractionService.isEnabled()) {
                            String modelName = aiPdfExtractionService.getPrimaryModelName();
                            log.info("🤖 Usando Gemini AI [{}] para extração ESTRUTURADA (JSON) da página {}...", modelName, pageNumber);
                            long geminiStart = System.currentTimeMillis();

                            addInfoEvent(document, pageNumber, ProcessingEventType.GEMINI_EXTRACTION_STARTED,
                                    String.format("Extração via Gemini AI [%s] iniciada.", modelName),
                                    Map.of("model", modelName));

                            // Fase 2: Usar extractPayrollData (JSON) ao invés de extractTextFromScannedPage (texto cru)
                            return aiPdfExtractionService.extractPayrollData(pdfBytes, pageNumber)
                                    .flatMap(jsonResponse -> {
                                        long geminiElapsed = System.currentTimeMillis() - geminiStart;

                                        if (jsonResponse == null || jsonResponse.trim().isEmpty()) {
                                            log.warn("⚠️ Gemini não retornou dados da página {}", pageNumber);
                                            addWarnEvent(document, pageNumber, ProcessingEventType.GEMINI_EXTRACTION_COMPLETED,
                                                    String.format("Gemini não retornou dados (%dms).", geminiElapsed),
                                                    Map.of("processingTimeMs", geminiElapsed, "rubricasCount", 0));
                                            return Mono.just(new PageResult(new ArrayList<>()));
                                        }

                                        log.info("✅ Gemini retornou {} chars de JSON estruturado da página {}",
                                                jsonResponse.length(), pageNumber);

                                        // Parsear JSON do Gemini em entries
                                        String origem = determinePageOrigin(document, pageNumber);
                                        GeminiResponseParser.ParsedPayrollData parsedData =
                                                GeminiResponseParser.parsePayrollResponse(
                                                        jsonResponse,
                                                        document.getId(),
                                                        document.getTenantId(),
                                                        origem,
                                                        pageNumber);

                                        if (parsedData == null || parsedData.getEntries().isEmpty()) {
                                            log.warn("⚠️ Gemini JSON não contém rubricas válidas na página {}", pageNumber);
                                            addWarnEvent(document, pageNumber, ProcessingEventType.GEMINI_EXTRACTION_COMPLETED,
                                                    String.format("Gemini retornou JSON mas sem rubricas válidas (%dms).", geminiElapsed),
                                                    Map.of("processingTimeMs", geminiElapsed, "rubricasCount", 0,
                                                            "responseLength", jsonResponse.length()));
                                            return Mono.just(new PageResult(new ArrayList<>()));
                                        }

                                        int rubricasCount = parsedData.getEntries().size();

                                        // Dados reais extraídos pela IA — visíveis para auditoria
                                        Map<String, Object> extractedDetails = new HashMap<>();
                                        extractedDetails.put("model", modelName);
                                        extractedDetails.put("processingTimeMs", geminiElapsed);
                                        extractedDetails.put("rubricasCount", rubricasCount);
                                        extractedDetails.put("responseLength", jsonResponse.length());
                                        if (parsedData.getNome() != null) extractedDetails.put("nome", parsedData.getNome());
                                        if (parsedData.getCpf() != null) extractedDetails.put("cpf", parsedData.getCpf());
                                        if (parsedData.getMatricula() != null) extractedDetails.put("matricula", parsedData.getMatricula());
                                        if (parsedData.getCompetencia() != null) extractedDetails.put("competencia", parsedData.getCompetencia());
                                        if (parsedData.getSalarioBruto() != null) extractedDetails.put("salarioBruto", parsedData.getSalarioBruto());
                                        if (parsedData.getTotalDescontos() != null) extractedDetails.put("totalDescontos", parsedData.getTotalDescontos());
                                        if (parsedData.getSalarioLiquido() != null) extractedDetails.put("salarioLiquido", parsedData.getSalarioLiquido());

                                        // Salvar TODAS as rubricas extraídas para histórico/auditoria
                                        List<Map<String, Object>> rubricasList = parsedData.getEntries().stream()
                                                .map(entry -> {
                                                    Map<String, Object> r = new HashMap<>();
                                                    r.put("codigo", entry.getRubricaCodigo());
                                                    if (entry.getRubricaDescricao() != null) r.put("descricao", entry.getRubricaDescricao());
                                                    if (entry.getValor() != null) r.put("valor", entry.getValor());
                                                    if (entry.getReferencia() != null) r.put("referencia", entry.getReferencia());
                                                    return r;
                                                })
                                                .collect(java.util.stream.Collectors.toList());
                                        extractedDetails.put("rubricas", rubricasList);

                                        String extractedSummary = String.format(
                                                "Gemini [%s] extraiu %d rubricas em %dms. nome=%s, cpf=%s, competencia=%s, bruto=%s, descontos=%s, líquido=%s",
                                                modelName, rubricasCount, geminiElapsed,
                                                parsedData.getNome(), parsedData.getCpf(), parsedData.getCompetencia(),
                                                parsedData.getSalarioBruto(), parsedData.getTotalDescontos(), parsedData.getSalarioLiquido());

                                        addInfoEvent(document, pageNumber, ProcessingEventType.GEMINI_EXTRACTION_COMPLETED,
                                                extractedSummary, extractedDetails);

                                        // Validação Fase 2: verificar consistência dos dados extraídos
                                        ValidationResult validation = validationService.validatePayrollExtraction(
                                                parsedData.getEntries(),
                                                parsedData.getSalarioBruto(),
                                                parsedData.getTotalDescontos(),
                                                parsedData.getSalarioLiquido(),
                                                parsedData.getCpf(),
                                                parsedData.getCompetencia()
                                        );

                                        double score = validation.confidenceScore();
                                        String scoreStr = String.format("%.2f", score);

                                        log.info("📊 Página {} (Gemini JSON) - {} rubricas extraídas, score={}, recommendation={}",
                                                pageNumber, parsedData.getEntries().size(),
                                                scoreStr, validation.recommendation());

                                        if (!validation.issues().isEmpty()) {
                                            List<String> issueMessages = new ArrayList<>();
                                            List<Map<String, Object>> issueDetails = new ArrayList<>();
                                            for (ValidationIssue issue : validation.issues()) {
                                                log.warn("  ⚠️ Validação: [{}] {} — esperado={}, encontrado={}",
                                                        issue.type(), issue.message(),
                                                        issue.expected(), issue.found());
                                                issueMessages.add(issue.type() + ": " + issue.message());
                                                Map<String, Object> issueMap = new HashMap<>();
                                                issueMap.put("type", issue.type());
                                                issueMap.put("message", issue.message());
                                                if (issue.expected() != null) issueMap.put("expected", issue.expected());
                                                if (issue.found() != null) issueMap.put("found", issue.found());
                                                issueDetails.add(issueMap);
                                            }

                                            Map<String, Object> valDetails = new HashMap<>();
                                            valDetails.put("score", score);
                                            valDetails.put("recommendation", validation.recommendation());
                                            valDetails.put("issues", issueDetails);
                                            valDetails.put("rubricasCount", rubricasCount);
                                            if (parsedData.getSalarioBruto() != null) valDetails.put("salarioBruto", parsedData.getSalarioBruto());
                                            if (parsedData.getTotalDescontos() != null) valDetails.put("totalDescontos", parsedData.getTotalDescontos());
                                            if (parsedData.getSalarioLiquido() != null) valDetails.put("salarioLiquido", parsedData.getSalarioLiquido());

                                            if (validation.isValid()) {
                                                addInfoEvent(document, pageNumber, ProcessingEventType.VALIDATION_PASSED,
                                                        String.format("Validação aprovada (score %s). %d issues menores.",
                                                                scoreStr, issueMessages.size()),
                                                        valDetails);
                                            } else {
                                                addWarnEvent(document, pageNumber, ProcessingEventType.VALIDATION_FAILED,
                                                        String.format("Validação reprovada (score %s). %d issues: %s",
                                                                scoreStr, issueMessages.size(), String.join("; ", issueMessages)),
                                                        valDetails);
                                            }
                                        } else {
                                            addInfoEvent(document, pageNumber, ProcessingEventType.VALIDATION_PASSED,
                                                    String.format("Validação aprovada (score %s). Sem issues.", scoreStr),
                                                    Map.of("score", score, "recommendation", validation.recommendation(),
                                                            "rubricasCount", rubricasCount));
                                        }

                                        // Fase 3: Cross-Validation — se score < 0.85, executar 2ª extração
                                        if (!validation.isValid()) {
                                            log.info("🔄 Score {} < 0.85 — Acionando CROSS-VALIDATION (Fase 3) na página {}...",
                                                    scoreStr, pageNumber);

                                            addInfoEvent(document, pageNumber, ProcessingEventType.CROSS_VALIDATION_STARTED,
                                                    String.format("Cross-validation iniciada (score %s < 0.85).", scoreStr));

                                            return crossValidationService.crossValidate(
                                                    pdfBytes, pageNumber,
                                                    parsedData.getEntries(),
                                                    jsonResponse,
                                                    document.getId(),
                                                    document.getTenantId(),
                                                    origem
                                            ).flatMap(crossResult -> {
                                                log.info("📊 Cross-validation página {}: score={}, matched={}/{}, revisão={}",
                                                        pageNumber,
                                                        String.format("%.2f", crossResult.confidenceScore()),
                                                        crossResult.matchedFields(),
                                                        crossResult.totalFields(),
                                                        crossResult.requiresManualReview());

                                                addInfoEvent(document, pageNumber, ProcessingEventType.CROSS_VALIDATION_COMPLETED,
                                                        String.format("Cross-validation concluída. %d/%d campos coincidem (%.0f%%). Revisão manual: %s.",
                                                                crossResult.matchedFields(), crossResult.totalFields(),
                                                                crossResult.confidenceScore() * 100,
                                                                crossResult.requiresManualReview() ? "sim" : "não"),
                                                        Map.of("score", crossResult.confidenceScore(),
                                                                "matchedFields", crossResult.matchedFields(),
                                                                "totalFields", crossResult.totalFields(),
                                                                "requiresManualReview", crossResult.requiresManualReview(),
                                                                "consolidatedEntries", crossResult.consolidatedEntries().size()));

                                                // Fase 4: Se cross-validation indica revisão manual, escalar para Gemini Pro
                                                if (crossResult.requiresManualReview()) {
                                                    log.info("⬆️ ESCALAÇÃO PARA GEMINI PRO (Fase 4) — Página {} com campos críticos divergentes",
                                                            pageNumber);

                                                    String proModelName = aiPdfExtractionService.getFallbackModelName();
                                                    addWarnEvent(document, pageNumber, ProcessingEventType.ESCALATION_TO_PRO,
                                                            String.format("Escalação para Gemini Pro [%s]. Campos críticos divergentes na cross-validation.", proModelName),
                                                            Map.of("reason", "Campos críticos divergentes", "model", proModelName));

                                                    long proStart = System.currentTimeMillis();
                                                    return aiPdfExtractionService.extractPayrollDataWithFallback(pdfBytes, pageNumber)
                                                            .map(proJsonResponse -> {
                                                                long proElapsed = System.currentTimeMillis() - proStart;

                                                                if (proJsonResponse == null || proJsonResponse.trim().isEmpty()) {
                                                                    log.warn("⚠️ Gemini Pro não retornou dados da página {}. Usando resultado da cross-validation.",
                                                                            pageNumber);
                                                                    addWarnEvent(document, pageNumber, ProcessingEventType.ESCALATION_FAILED,
                                                                            String.format("Gemini Pro não retornou dados (%dms). Usando cross-validation.", proElapsed),
                                                                            Map.of("processingTimeMs", proElapsed));
                                                                    return new PageResult(crossResult.consolidatedEntries());
                                                                }

                                                                log.info("✅ Gemini Pro retornou {} chars de JSON da página {}",
                                                                        proJsonResponse.length(), pageNumber);

                                                                GeminiResponseParser.ParsedPayrollData proData =
                                                                        GeminiResponseParser.parsePayrollResponse(
                                                                                proJsonResponse,
                                                                                document.getId(),
                                                                                document.getTenantId(),
                                                                                origem,
                                                                                pageNumber);

                                                                if (proData != null && !proData.getEntries().isEmpty()) {
                                                                    log.info("📊 Gemini Pro página {}: {} rubricas extraídas (substituindo resultado da cross-validation)",
                                                                            pageNumber, proData.getEntries().size());

                                                                    Map<String, Object> proDetails = new HashMap<>();
                                                                    proDetails.put("processingTimeMs", proElapsed);
                                                                    proDetails.put("rubricasCount", proData.getEntries().size());
                                                                    proDetails.put("model", "gemini-pro");
                                                                    if (proData.getNome() != null) proDetails.put("nome", proData.getNome());
                                                                    if (proData.getCpf() != null) proDetails.put("cpf", proData.getCpf());
                                                                    if (proData.getCompetencia() != null) proDetails.put("competencia", proData.getCompetencia());
                                                                    if (proData.getSalarioBruto() != null) proDetails.put("salarioBruto", proData.getSalarioBruto());
                                                                    if (proData.getTotalDescontos() != null) proDetails.put("totalDescontos", proData.getTotalDescontos());
                                                                    if (proData.getSalarioLiquido() != null) proDetails.put("salarioLiquido", proData.getSalarioLiquido());

                                                                    // Salvar TODAS as rubricas extraídas pelo Pro para histórico/auditoria
                                                                    List<Map<String, Object>> proRubricasList = proData.getEntries().stream()
                                                                            .map(entry -> {
                                                                                Map<String, Object> r = new HashMap<>();
                                                                                r.put("codigo", entry.getRubricaCodigo());
                                                                                if (entry.getRubricaDescricao() != null) r.put("descricao", entry.getRubricaDescricao());
                                                                                if (entry.getValor() != null) r.put("valor", entry.getValor());
                                                                                if (entry.getReferencia() != null) r.put("referencia", entry.getReferencia());
                                                                                return r;
                                                                            })
                                                                            .collect(java.util.stream.Collectors.toList());
                                                                    proDetails.put("rubricas", proRubricasList);

                                                                    addInfoEvent(document, pageNumber, ProcessingEventType.ESCALATION_COMPLETED,
                                                                            String.format("Gemini Pro extraiu %d rubricas em %dms. nome=%s, competencia=%s, bruto=%s, descontos=%s, líquido=%s",
                                                                                    proData.getEntries().size(), proElapsed,
                                                                                    proData.getNome(), proData.getCompetencia(),
                                                                                    proData.getSalarioBruto(), proData.getTotalDescontos(), proData.getSalarioLiquido()),
                                                                            proDetails);
                                                                    return new PageResult(proData.getEntries());
                                                                }

                                                                log.warn("⚠️ Gemini Pro não extraiu rubricas da página {}. Usando cross-validation.",
                                                                        pageNumber);
                                                                addWarnEvent(document, pageNumber, ProcessingEventType.ESCALATION_FAILED,
                                                                        String.format("Gemini Pro retornou JSON mas sem rubricas (%dms). Usando cross-validation.", proElapsed),
                                                                        Map.of("processingTimeMs", proElapsed));
                                                                return new PageResult(crossResult.consolidatedEntries());
                                                            })
                                                            .onErrorResume(proError -> {
                                                                long proElapsed = System.currentTimeMillis() - proStart;
                                                                log.error("❌ Erro ao escalar para Gemini Pro na página {}: {}. Usando cross-validation.",
                                                                        pageNumber, proError.getMessage());
                                                                addErrorEvent(document, pageNumber, ProcessingEventType.ESCALATION_FAILED,
                                                                        String.format("Erro no Gemini Pro (%dms): %s. Usando cross-validation.",
                                                                                proElapsed, proError.getMessage()),
                                                                        Map.of("processingTimeMs", proElapsed,
                                                                                "errorMessage", proError.getMessage()));
                                                                return Mono.just(new PageResult(crossResult.consolidatedEntries()));
                                                            });
                                                }

                                                // Cross-validation OK — usar entries consolidadas
                                                return Mono.just(new PageResult(crossResult.consolidatedEntries()));
                                            });
                                        }

                                        // Score >= 0.85 — dados confiáveis, usar direto
                                        return Mono.just(new PageResult(parsedData.getEntries()));
                                    })
                                    .onErrorResume(error -> {
                                        long geminiElapsed = System.currentTimeMillis() - geminiStart;
                                        log.error("❌ Erro ao usar Gemini [{}] na página {}: {}. Tentando extração de texto...",
                                                modelName, pageNumber, error.getMessage());

                                        addErrorEvent(document, pageNumber, ProcessingEventType.GEMINI_EXTRACTION_FAILED,
                                                String.format("Erro no Gemini [%s] (%dms): %s. Tentando fallback.",
                                                        modelName, geminiElapsed, error.getMessage()),
                                                Map.of("model", modelName, "processingTimeMs", geminiElapsed,
                                                        "errorMessage", error.getMessage()));

                                        // Fallback: tentar extração de texto cru como antes
                                        return aiPdfExtractionService.extractTextFromScannedPage(pdfBytes, pageNumber)
                                                .map(extractedText -> {
                                                    if (extractedText != null && !extractedText.trim().isEmpty()) {
                                                        log.info("✅ Fallback: Gemini extraiu {} caracteres (texto) da página {}",
                                                                extractedText.length(), pageNumber);
                                                        return extractedText;
                                                    }
                                                    return "";
                                                })
                                                .flatMap(text -> processPageTextWithParser(document, text, pageNumber))
                                                .onErrorResume(err -> {
                                                    log.error("❌ Fallback também falhou na página {}: {}", pageNumber, err.getMessage());
                                                    return Mono.just(new PageResult(new ArrayList<>()));
                                                });
                                    });
                        } else {
                            log.warn("⚠️ Gemini AI desabilitado. Página {} será ignorada.", pageNumber);
                            return Mono.just((Object) "");
                        }
                    }
                    // Texto legível — parser regex
                    int readableLen = pageText != null ? pageText.trim().length() : 0;
                    addInfoEvent(document, pageNumber, ProcessingEventType.TEXT_EXTRACTED,
                            String.format("Texto legível extraído (%d chars). Usando parser regex.", readableLen),
                            Map.of("textLength", readableLen));
                    return Mono.just((Object) pageText);
                })
                .flatMap(result -> {
                    // Se já é PageResult (veio do Gemini JSON), retornar direto
                    if (result instanceof PageResult) {
                        return Mono.just((PageResult) result);
                    }

                    // Se é String (texto extraído), processar com parser regex
                    String pageText = (String) result;
                    return processPageTextWithParser(document, pageText, pageNumber);
                })
                .onErrorResume(error -> {
                    log.error("Erro ao processar página {}", pageNumber, error);
                    // Continuar processamento mesmo com erro em uma página
                    return Mono.just(new PageResult(new ArrayList<>()));
                });
    }

    /**
     * Processa texto extraído usando o parser regex tradicional.
     * Usado para PDFs digitais ou como fallback quando o Gemini JSON falha.
     */
    private Mono<PageResult> processPageTextWithParser(PayrollDocument document, String pageText, int pageNumber) {
        // Se texto vazio, retornar resultado vazio
        if (pageText == null || pageText.isEmpty()) {
            return Mono.just(new PageResult(new ArrayList<>()));
        }

        // Determinar origem da página
        String origem = determinePageOrigin(document, pageNumber);
        log.debug("Página {} - Origem: {}", pageNumber, origem);

        // Determinar tipo do documento para esta página
        DocumentType pageType = determinePageType(document, origem);

        // Extrair referência (mês/ano) da página
        return monthYearDetectionService.detectMonthYear(pageText)
                .map(monthYearOpt -> {
                    String referencia = monthYearOpt.orElse(null);
                    if (referencia == null) {
                        log.warn("Não foi possível detectar referência na página {}", pageNumber);
                        if (!document.getMesesDetectados().isEmpty()) {
                            referencia = document.getMesesDetectados().get(0);
                        }
                    }

                    log.info("════════════════════════════════════════════════════════════════════════════════");
                    log.info("🔍 EXTRAINDO RUBRICAS DA PÁGINA {} (tipo: {}, referência: {}) — Parser Regex",
                            pageNumber, pageType, referencia);
                    log.info("════════════════════════════════════════════════════════════════════════════════");

                    List<PdfLineParser.ParsedLine> parsedLines;
                    if (pageType == DocumentType.FUNCEF) {
                        parsedLines = lineParser.parseLinesFuncef(pageText, referencia);
                    } else {
                        parsedLines = lineParser.parseLines(pageText, pageType);
                    }

                    log.info("📊 Total de linhas parseadas: {}", parsedLines.size());

                    List<PayrollEntry> entries = new ArrayList<>();
                    for (int idx = 0; idx < parsedLines.size(); idx++) {
                        PdfLineParser.ParsedLine parsedLine = parsedLines.get(idx);
                        log.debug("Processando linha [{}]: código=[{}], valor=[{}]",
                                idx + 1, parsedLine.getCodigo(), parsedLine.getValorStr());

                        String finalReferencia = parsedLine.getReferencia() != null
                                ? parsedLine.getReferencia()
                                : referencia;

                        PayrollEntry entry = createEntryFromParsedLine(
                                document.getId(),
                                document.getTenantId(),
                                parsedLine,
                                finalReferencia,
                                origem,
                                pageNumber,
                                referencia);

                        if (entry != null) {
                            entries.add(entry);
                        }
                    }

                    log.info("📊 Página {} - {} rubricas extraídas (parser), {} entries criadas",
                            pageNumber, parsedLines.size(), entries.size());

                    return new PageResult(entries);
                });
    }

    /**
     * Determina a origem de uma página baseado nos detectedPages do documento.
     */
    private String determinePageOrigin(PayrollDocument document, int pageNumber) {
        return document.getDetectedPages().stream()
                .filter(dp -> dp.getPage().equals(pageNumber))
                .map(DetectedPage::getOrigem)
                .findFirst()
                .orElse(document.getTipo().name()); // Fallback para o tipo do documento
    }

    /**
     * Determina o tipo do documento para uma página específica.
     */
    private DocumentType determinePageType(PayrollDocument document, String origem) {
        if ("CAIXA".equals(origem)) {
            return DocumentType.CAIXA;
        } else if ("FUNCEF".equals(origem)) {
            return DocumentType.FUNCEF;
        } else {
            return document.getTipo();
        }
    }

    /**
     * Cria uma PayrollEntry a partir de uma linha parseada, validando a rubrica.
     */
    private PayrollEntry createEntryFromParsedLine(String documentoId,
            String tenantId,
            PdfLineParser.ParsedLine parsedLine,
            String referencia,
            String origem,
            int pagina,
            String mesPagamento) { // Ano Pagamento / Mês do documento
        // Validar código (obrigatório)
        if (parsedLine.getCodigo() == null || parsedLine.getCodigo().trim().isEmpty()) {
            log.warn("Código da rubrica é obrigatório. Entry não será criada.");
            return null;
        }

        // Normalizar valor (obrigatório)
        java.math.BigDecimal normalizedValue = normalizer.normalizeValue(parsedLine.getValorStr());
        if (normalizedValue == null) {
            log.warn("Não foi possível normalizar valor: {}. Entry não será criada.", parsedLine.getValorStr());
            return null;
        }

        // Normalizar referência (opcional - usa a do cabeçalho se não houver na linha)
        String normalizedReferencia = null;
        if (referencia != null && !referencia.trim().isEmpty()) {
            normalizedReferencia = normalizer.normalizeReference(referencia);
            if (normalizedReferencia == null) {
                log.warn("Não foi possível normalizar referência: {}. Entry será criada sem referência.", referencia);
            }
        }

        // Normalizar mês de pagamento (Ano Pagamento / Mês do documento)
        String normalizedMesPagamento = null;
        if (mesPagamento != null && !mesPagamento.trim().isEmpty()) {
            normalizedMesPagamento = normalizer.normalizeReference(mesPagamento);
            if (normalizedMesPagamento == null) {
                log.warn("Não foi possível normalizar mês de pagamento: {}. Entry será criada sem mês de pagamento.",
                        mesPagamento);
            }
        }

        // Descrição é opcional - pode ser null
        String descricao = parsedLine.getDescricao();
        if (descricao != null) {
            descricao = normalizer.normalizeDescription(descricao);
        }

        // Criar entry mesmo sem referência ou descrição (código e valor são
        // obrigatórios)
        return PayrollEntry.builder()
                .tenantId(tenantId)
                .documentoId(documentoId)
                .rubricaCodigo(parsedLine.getCodigo())
                .rubricaDescricao(descricao) // Pode ser null
                .referencia(normalizedReferencia) // Pode ser null - referência da rubrica
                .mesPagamento(normalizedMesPagamento) // Ano Pagamento / Mês do documento
                .valor(normalizedValue)
                .origem(origem)
                .pagina(pagina)
                .build();
    }

    /**
     * Verifica se o texto extraído do PDF é legível e utilizável para parsing via regex.
     * PDFs com fontes sem mapeamento Unicode (ex: contracheques antigos da CAIXA/APCEF)
     * geram texto com caracteres aleatórios que passam no check de tamanho mas são ilegíveis.
     *
     * Critérios:
     * 1. Texto não pode ser nulo ou muito curto (< MIN_TEXT_LENGTH_FOR_PDF)
     * 2. Proporção de caracteres alfanuméricos + espaço deve ser >= MIN_ALPHANUMERIC_RATIO
     * 3. Se a proporção for limítrofe, verifica a presença de palavras-chave de contracheque
     *
     * @param text texto extraído do PDF pelo PDFBox
     * @return true se o texto é legível e pode ser processado por regex
     */
    private boolean isTextReadable(String text) {
        if (text == null || text.trim().length() < MIN_TEXT_LENGTH_FOR_PDF) {
            return false;
        }

        String trimmed = text.trim();

        // Proporção de caracteres alfanuméricos + espaço
        long readableChars = trimmed.chars()
                .filter(c -> Character.isLetterOrDigit(c) || Character.isWhitespace(c)
                        || c == '.' || c == ',' || c == '/' || c == '-')
                .count();
        double ratio = (double) readableChars / trimmed.length();

        // Se a proporção é muito alta, o texto é claramente legível
        if (ratio >= 0.7) {
            return true;
        }

        // Se está abaixo do mínimo, o texto é claramente ilegível (fontes sem Unicode mapping)
        if (ratio < MIN_ALPHANUMERIC_RATIO) {
            log.info("📛 Texto com baixa proporção alfanumérica ({}% de {} chars). " +
                    "Provável PDF com fontes sem mapeamento Unicode.",
                    String.format("%.1f", ratio * 100), trimmed.length());
            return false;
        }

        // Zona intermediária (0.5 - 0.7): verificar presença de palavras-chave
        String upper = trimmed.toUpperCase();
        for (String keyword : PAYROLL_KEYWORDS) {
            if (upper.contains(keyword)) {
                return true;
            }
        }

        log.info("📛 Texto sem palavras-chave reconhecíveis (proporção alfanumérica: {}%). " +
                "Provável PDF com fontes sem mapeamento Unicode.",
                String.format("%.1f", ratio * 100));
        return false;
    }

    // ==================== PROCESSING LOG HELPERS ====================

    private void addEvent(PayrollDocument document, ProcessingEventType type,
                          ProcessingEventLevel level, Integer page, String message,
                          Map<String, Object> details) {
        ProcessingEvent event = ProcessingEvent.builder()
                .timestamp(Instant.now())
                .type(type)
                .level(level)
                .page(page)
                .message(message)
                .details(details)
                .build();
        synchronized (document.getProcessingLog()) {
            document.getProcessingLog().add(event);
        }
    }

    private void addInfoEvent(PayrollDocument document, Integer page,
                              ProcessingEventType type, String message) {
        addEvent(document, type, ProcessingEventLevel.INFO, page, message, null);
    }

    private void addInfoEvent(PayrollDocument document, Integer page,
                              ProcessingEventType type, String message,
                              Map<String, Object> details) {
        addEvent(document, type, ProcessingEventLevel.INFO, page, message, details);
    }

    private void addWarnEvent(PayrollDocument document, Integer page,
                              ProcessingEventType type, String message,
                              Map<String, Object> details) {
        addEvent(document, type, ProcessingEventLevel.WARN, page, message, details);
    }

    private void addErrorEvent(PayrollDocument document, Integer page,
                               ProcessingEventType type, String message,
                               Map<String, Object> details) {
        addEvent(document, type, ProcessingEventLevel.ERROR, page, message, details);
    }

    /**
     * Salva o documento intermediariamente para persistir o processingLog acumulado.
     * Permite que o frontend faça polling e veja o progresso em tempo real.
     */
    private Mono<Void> saveIntermediateProgress(PayrollDocument document) {
        return documentRepository.save(document)
                .doOnSuccess(saved -> log.info("💾 Progresso intermediário salvo — {} eventos no processingLog",
                        saved.getProcessingLog().size()))
                .doOnError(error -> log.warn("⚠️ Falha ao salvar progresso intermediário: {}", error.getMessage()))
                .onErrorResume(error -> Mono.just(document))
                .then();
    }

    // ==================== FIM PROCESSING LOG HELPERS ====================

    /**
     * Verifica se o PDF precisa de extração de texto de imagem verificando o texto
     * da primeira página.
     */
    private Mono<Boolean> checkIfNeedsImageTextExtraction(byte[] pdfBytes, int totalPages) {
        if (totalPages == 0) {
            return Mono.just(false);
        }

        // Verificar apenas a primeira página para determinar se precisa de extração de
        // texto de imagem
        return pdfService.extractTextFromPage(new ByteArrayInputStream(pdfBytes), 1)
                .map(pageText -> {
                    boolean needsImageTextExtraction = !isTextReadable(pageText);
                    log.info("Primeira página tem {} caracteres (legível: {}). Precisa de extração de texto de imagem: {}",
                            pageText != null ? pageText.trim().length() : 0,
                            !needsImageTextExtraction,
                            needsImageTextExtraction);
                    return needsImageTextExtraction;
                })
                .onErrorReturn(true); // Se houver erro, assumir que precisa de extração de texto de imagem
    }

    /**
     * Valida uma entry verificando se a rubrica existe no banco.
     * Retorna a entry se válida, ou null se a rubrica não for encontrada.
     */
    private Mono<PayrollEntry> validateEntry(PayrollEntry entry) {
        return rubricaValidator.validateRubrica(entry.getRubricaCodigo(), entry.getRubricaDescricao())
                .map(rubrica -> {
                    log.debug("Rubrica {} validada: {}", entry.getRubricaCodigo(), rubrica.getDescricao());
                    return entry;
                })
                .switchIfEmpty(Mono.defer(() -> {
                    log.warn("Rubrica {} não encontrada ou inativa. Entry ignorada.", entry.getRubricaCodigo());
                    return Mono.empty();
                }));
    }

    /**
     * Processa documentos de declaração de IR, extraindo informações específicas
     * (nome, CPF, exercício, ano-calendário, imposto devido) e salvando como
     * entries.
     */
    private Mono<Long> processIncomeTaxDocument(PayrollDocument document) {
        log.info("Processando declaração de IR: {}", document.getId());

        // Usar o tenantId do documento em vez do contexto reativo (que pode estar
        // perdido no processamento assíncrono)
        String tenantId = document.getTenantId();
        if (tenantId == null || tenantId.isEmpty()) {
            log.error("TenantId não encontrado no documento: {}", document.getId());
            return Mono.error(new IllegalStateException("TenantId não encontrado no documento"));
        }

        return loadPdfFromGridFs(document.getOriginalFileId())
                .flatMap(pdfBytes -> {
                    log.info("PDF carregado do GridFS. Extraindo informações da declaração de IR...");
                    return iTextIncomeTaxService.extractIncomeTaxInfo(
                            new ByteArrayInputStream(pdfBytes));
                })
                .flatMap(incomeTaxInfo -> {
                    log.info("Informações extraídas da declaração de IR:");
                    log.info("  Nome: {}, CPF: {}, Exercício: {}, Ano-Calendário: {}",
                            incomeTaxInfo.getNome(), incomeTaxInfo.getCpf(),
                            incomeTaxInfo.getExercicio(), incomeTaxInfo.getAnoCalendario());
                    log.info(
                            "  Base Cálculo: {}, Devido: {}, Dedução: {}, Devido I: {}, Contribuição: {}, Devido II: {}, RRA: {}, Total: {}, Saldo a Pagar: {}",
                            incomeTaxInfo.getBaseCalculoImposto(), incomeTaxInfo.getImpostoDevido(),
                            incomeTaxInfo.getDeducaoIncentivo(), incomeTaxInfo.getImpostoDevidoI(),
                            incomeTaxInfo.getContribuicaoPrevEmpregadorDomestico(), incomeTaxInfo.getImpostoDevidoII(),
                            incomeTaxInfo.getImpostoDevidoRRA(), incomeTaxInfo.getTotalImpostoDevido(),
                            incomeTaxInfo.getSaldoImpostoPagar());

                    // Criar entries para cada informação
                    List<PayrollEntry> entries = new ArrayList<>();
                    String referencia = incomeTaxInfo.getAnoCalendario() != null
                            ? incomeTaxInfo.getAnoCalendario() + "-00"
                            : null;

                    // Entry para Nome
                    if (incomeTaxInfo.getNome() != null && !incomeTaxInfo.getNome().trim().isEmpty()) {
                        entries.add(createEntry(tenantId, document.getId(), "IR_NOME", incomeTaxInfo.getNome(), null,
                                referencia));
                    }

                    // Entry para CPF
                    if (incomeTaxInfo.getCpf() != null && !incomeTaxInfo.getCpf().trim().isEmpty()) {
                        entries.add(createEntry(tenantId, document.getId(), "IR_CPF", incomeTaxInfo.getCpf(), null,
                                referencia));
                    }

                    // Entry para Exercício
                    if (incomeTaxInfo.getExercicio() != null && !incomeTaxInfo.getExercicio().trim().isEmpty()) {
                        entries.add(createEntry(tenantId, document.getId(), "IR_EXERCICIO",
                                incomeTaxInfo.getExercicio(), null, referencia));
                    }

                    // Entry para Ano-Calendário
                    if (incomeTaxInfo.getAnoCalendario() != null
                            && !incomeTaxInfo.getAnoCalendario().trim().isEmpty()) {
                        entries.add(createEntry(tenantId, document.getId(), "IR_ANO_CALENDARIO",
                                incomeTaxInfo.getAnoCalendario(), null, referencia));
                    }

                    // Entries para valores da seção IMPOSTO DEVIDO
                    if (incomeTaxInfo.getBaseCalculoImposto() != null) {
                        entries.add(createEntry(tenantId, document.getId(), "IR_BASE_CALCULO_IMPOSTO",
                                "Base de cálculo do imposto", incomeTaxInfo.getBaseCalculoImposto(), referencia));
                    }

                    if (incomeTaxInfo.getImpostoDevido() != null) {
                        entries.add(createEntry(tenantId, document.getId(), "IR_IMPOSTO_DEVIDO", "Imposto devido",
                                incomeTaxInfo.getImpostoDevido(), referencia));
                    }

                    if (incomeTaxInfo.getDeducaoIncentivo() != null) {
                        entries.add(createEntry(tenantId, document.getId(), "IR_DEDUCAO_INCENTIVO",
                                "Dedução de incentivo", incomeTaxInfo.getDeducaoIncentivo(), referencia));
                    }

                    if (incomeTaxInfo.getImpostoDevidoI() != null) {
                        entries.add(createEntry(tenantId, document.getId(), "IR_IMPOSTO_DEVIDO_I", "Imposto devido I",
                                incomeTaxInfo.getImpostoDevidoI(), referencia));
                    }

                    if (incomeTaxInfo.getContribuicaoPrevEmpregadorDomestico() != null) {
                        entries.add(createEntry(tenantId, document.getId(), "IR_CONTRIBUICAO_PREV_EMPREGADOR_DOMESTICO",
                                "Contribuição Prev. Empregador Doméstico",
                                incomeTaxInfo.getContribuicaoPrevEmpregadorDomestico(), referencia));
                    }

                    if (incomeTaxInfo.getImpostoDevidoII() != null) {
                        entries.add(createEntry(tenantId, document.getId(), "IR_IMPOSTO_DEVIDO_II", "Imposto devido II",
                                incomeTaxInfo.getImpostoDevidoII(), referencia));
                    }

                    if (incomeTaxInfo.getImpostoDevidoRRA() != null) {
                        entries.add(createEntry(tenantId, document.getId(), "IR_IMPOSTO_DEVIDO_RRA",
                                "Imposto devido RRA", incomeTaxInfo.getImpostoDevidoRRA(), referencia));
                    }

                    if (incomeTaxInfo.getTotalImpostoDevido() != null) {
                        entries.add(createEntry(tenantId, document.getId(), "IR_TOTAL_IMPOSTO_DEVIDO",
                                "Total do imposto devido", incomeTaxInfo.getTotalImpostoDevido(), referencia));
                    }

                    if (incomeTaxInfo.getSaldoImpostoPagar() != null) {
                        entries.add(createEntry(tenantId, document.getId(), "IR_SALDO_IMPOSTO_A_PAGAR",
                                "Saldo de imposto a pagar", incomeTaxInfo.getSaldoImpostoPagar(), referencia));
                    }

                    // --- NOVOS CAMPOS SALVOS ---

                    if (incomeTaxInfo.getRendimentosTributaveis() != null) {
                        entries.add(createEntry(tenantId, document.getId(), "IR_RENDIMENTOS_TRIBUTAVEIS",
                                "Rendimentos Tributáveis", incomeTaxInfo.getRendimentosTributaveis(), referencia));
                    }

                    if (incomeTaxInfo.getDeducoes() != null) {
                        entries.add(createEntry(tenantId, document.getId(), "IR_DEDUCOES",
                                "Deduções Totais", incomeTaxInfo.getDeducoes(), referencia));
                    }

                    if (incomeTaxInfo.getImpostoRetidoFonteTitular() != null) {
                        entries.add(createEntry(tenantId, document.getId(), "IR_IMPOSTO_RETIDO_FONTE",
                                "Imposto Retido na Fonte (Titular)", incomeTaxInfo.getImpostoRetidoFonteTitular(),
                                referencia));
                    }

                    if (incomeTaxInfo.getImpostoPagoTotal() != null) {
                        entries.add(createEntry(tenantId, document.getId(), "IR_IMPOSTO_PAGO_TOTAL",
                                "Total do Imposto Pago", incomeTaxInfo.getImpostoPagoTotal(), referencia));
                    }

                    if (incomeTaxInfo.getImpostoRestituir() != null) {
                        entries.add(createEntry(tenantId, document.getId(), "IR_IMPOSTO_RESTITUIR",
                                "Imposto a Restituir", incomeTaxInfo.getImpostoRestituir(), referencia));
                    }

                    // --- CAMPOS INDIVIDUAIS DE DEDUÇÕES ---

                    if (incomeTaxInfo.getDeducoesContribPrevOficial() != null) {
                        entries.add(createEntry(tenantId, document.getId(), "IR_DEDUCOES_CONTRIB_PREV_OFICIAL",
                                "Contribuição à previdência oficial e complementar pública (limite patrocinador)",
                                incomeTaxInfo.getDeducoesContribPrevOficial(), referencia));
                    }

                    if (incomeTaxInfo.getDeducoesContribPrevRRA() != null) {
                        entries.add(createEntry(tenantId, document.getId(), "IR_DEDUCOES_CONTRIB_PREV_RRA",
                                "Contribuição à previdência oficial (RRA)",
                                incomeTaxInfo.getDeducoesContribPrevRRA(), referencia));
                    }

                    if (incomeTaxInfo.getDeducoesContribPrevCompl() != null) {
                        entries.add(createEntry(tenantId, document.getId(), "IR_DEDUCOES_CONTRIB_PREV_COMPL",
                                "Contribuição à previdência complementar/privada/Fapi",
                                incomeTaxInfo.getDeducoesContribPrevCompl(), referencia));
                    }

                    if (incomeTaxInfo.getDeducoesDependentes() != null) {
                        entries.add(createEntry(tenantId, document.getId(), "IR_DEDUCOES_DEPENDENTES",
                                "Dependentes", incomeTaxInfo.getDeducoesDependentes(), referencia));
                    }

                    if (incomeTaxInfo.getDeducoesInstrucao() != null) {
                        entries.add(createEntry(tenantId, document.getId(), "IR_DEDUCOES_INSTRUCAO",
                                "Despesas com instrução", incomeTaxInfo.getDeducoesInstrucao(), referencia));
                    }

                    if (incomeTaxInfo.getDeducoesMedicas() != null) {
                        entries.add(createEntry(tenantId, document.getId(), "IR_DEDUCOES_MEDICAS",
                                "Despesas médicas", incomeTaxInfo.getDeducoesMedicas(), referencia));
                    }

                    if (incomeTaxInfo.getDeducoesPensaoJudicial() != null) {
                        entries.add(createEntry(tenantId, document.getId(), "IR_DEDUCOES_PENSAO_JUDICIAL",
                                "Pensão alimentícia judicial", incomeTaxInfo.getDeducoesPensaoJudicial(), referencia));
                    }

                    if (incomeTaxInfo.getDeducoesPensaoEscritura() != null) {
                        entries.add(createEntry(tenantId, document.getId(), "IR_DEDUCOES_PENSAO_ESCRITURA",
                                "Pensão alimentícia por escritura pública",
                                incomeTaxInfo.getDeducoesPensaoEscritura(), referencia));
                    }

                    if (incomeTaxInfo.getDeducoesPensaoRRA() != null) {
                        entries.add(createEntry(tenantId, document.getId(), "IR_DEDUCOES_PENSAO_RRA",
                                "Pensão alimentícia judicial (RRA)",
                                incomeTaxInfo.getDeducoesPensaoRRA(), referencia));
                    }

                    if (incomeTaxInfo.getDeducoesLivroCaixa() != null) {
                        entries.add(createEntry(tenantId, document.getId(), "IR_DEDUCOES_LIVRO_CAIXA",
                                "Livro caixa", incomeTaxInfo.getDeducoesLivroCaixa(), referencia));
                    }

                    // --- CAMPOS INDIVIDUAIS DE IMPOSTO PAGO ---

                    if (incomeTaxInfo.getImpostoRetidoFonteDependentes() != null) {
                        entries.add(createEntry(tenantId, document.getId(), "IR_IMPOSTO_RETIDO_FONTE_DEPENDENTES",
                                "Imp. retido na fonte dos dependentes",
                                incomeTaxInfo.getImpostoRetidoFonteDependentes(), referencia));
                    }

                    if (incomeTaxInfo.getCarneLeaoTitular() != null) {
                        entries.add(createEntry(tenantId, document.getId(), "IR_CARNE_LEAO_TITULAR",
                                "Carnê-Leão do titular", incomeTaxInfo.getCarneLeaoTitular(), referencia));
                    }

                    if (incomeTaxInfo.getCarneLeaoDependentes() != null) {
                        entries.add(createEntry(tenantId, document.getId(), "IR_CARNE_LEAO_DEPENDENTES",
                                "Carnê-Leão dos dependentes", incomeTaxInfo.getCarneLeaoDependentes(), referencia));
                    }

                    if (incomeTaxInfo.getImpostoComplementar() != null) {
                        entries.add(createEntry(tenantId, document.getId(), "IR_IMPOSTO_COMPLEMENTAR",
                                "Imposto complementar", incomeTaxInfo.getImpostoComplementar(), referencia));
                    }

                    if (incomeTaxInfo.getImpostoPagoExterior() != null) {
                        entries.add(createEntry(tenantId, document.getId(), "IR_IMPOSTO_PAGO_EXTERIOR",
                                "Imposto pago no exterior", incomeTaxInfo.getImpostoPagoExterior(), referencia));
                    }

                    if (incomeTaxInfo.getImpostoRetidoFonteLei11033() != null) {
                        entries.add(createEntry(tenantId, document.getId(), "IR_IMPOSTO_RETIDO_FONTE_LEI_11033",
                                "Imposto retido na fonte (Lei nº 11.033/2004)",
                                incomeTaxInfo.getImpostoRetidoFonteLei11033(), referencia));
                    }

                    if (incomeTaxInfo.getImpostoRetidoRRA() != null) {
                        entries.add(createEntry(tenantId, document.getId(), "IR_IMPOSTO_RETIDO_RRA",
                                "Imposto retido RRA", incomeTaxInfo.getImpostoRetidoRRA(), referencia));
                    }

                    // --- CAMPOS EXCLUSIVOS 2017+ (DESCONTO SIMPLIFICADO) ---

                    if (incomeTaxInfo.getDescontoSimplificado() != null) {
                        entries.add(createEntry(tenantId, document.getId(), "IR_DESCONTO_SIMPLIFICADO",
                                "Desconto Simplificado", incomeTaxInfo.getDescontoSimplificado(), referencia));
                    }

                    if (incomeTaxInfo.getAliquotaEfetiva() != null) {
                        entries.add(createEntry(tenantId, document.getId(), "IR_ALIQUOTA_EFETIVA",
                                "Alíquota efetiva (%)", incomeTaxInfo.getAliquotaEfetiva(), referencia));
                    }

                    log.info("Criadas {} entries para declaração de IR", entries.size());

                    // Salvar entries
                    if (entries.isEmpty()) {
                        log.warn("Nenhuma informação foi extraída da declaração de IR");
                        return Mono.just(0L);
                    }

                    return entryRepository.saveAll(Flux.fromIterable(entries))
                            .count()
                            .doOnNext(count -> {
                                log.info("✅ {} entries de declaração de IR salvas com sucesso", count);
                            })
                            .flatMap(count -> {
                                // Atualizar documento com status PROCESSED e informações extraídas
                                log.info("Processamento da declaração de IR concluído. Total de entries: {}", count);

                                // Atualizar documento com status PROCESSED
                                document.setStatus(DocumentStatus.PROCESSED);
                                document.setDataProcessamento(Instant.now());
                                document.setTotalEntries(count);

                                // Atualizar anoDetectado se estiver null e o ano-calendário foi extraído
                                if (document.getAnoDetectado() == null && incomeTaxInfo.getAnoCalendario() != null) {
                                    try {
                                        Integer anoCalendario = Integer.parseInt(incomeTaxInfo.getAnoCalendario());
                                        document.setAnoDetectado(anoCalendario);
                                        log.info("Ano detectado atualizado para: {}", anoCalendario);
                                    } catch (NumberFormatException e) {
                                        log.warn("Não foi possível converter ano-calendário para Integer: {}",
                                                incomeTaxInfo.getAnoCalendario());
                                    }
                                }

                                // Para documentos de IR, mesesDetectados não faz sentido (não há meses
                                // específicos)
                                // Deixamos vazio, mas garantimos que o ano está preenchido
                                if (document.getMesesDetectados().isEmpty() && document.getAnoDetectado() != null) {
                                    log.info("Ano-calendário: {} (mesesDetectados não aplicável para IR)",
                                            document.getAnoDetectado());
                                }

                                return documentRepository.save(document)
                                        .thenReturn(count);
                            });
                })
                .onErrorResume(error -> {
                    log.error("Erro ao processar declaração de IR", error);
                    document.setStatus(DocumentStatus.ERROR);
                    document.setErro(error.getMessage());
                    return documentRepository.save(document)
                            .then(Mono.error(error));
                });
    }

    /**
     * Cria uma PayrollEntry para declaração de IR.
     */
    private PayrollEntry createEntry(String tenantId, String documentoId, String rubricaCodigo,
            String rubricaDescricao, java.math.BigDecimal valor, String referencia) {
        return PayrollEntry.builder()
                .tenantId(tenantId)
                .documentoId(documentoId)
                .rubricaCodigo(rubricaCodigo)
                .rubricaDescricao(rubricaDescricao)
                .referencia(referencia)
                .valor(valor)
                .origem("INCOME_TAX")
                .pagina(1)
                .build();
    }
}
