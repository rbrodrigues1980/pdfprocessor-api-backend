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
import java.util.List;

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
                    log.info("Processamento concluído. Total de entries: {}", entriesCount);

                    // Atualizar documento com status PROCESSED
                    document.setStatus(DocumentStatus.PROCESSED);
                    document.setDataProcessamento(Instant.now());
                    document.setTotalEntries(entriesCount);

                    return documentRepository.save(document)
                            .thenReturn(entriesCount);
                })
                .onErrorResume(error -> {
                    log.error("Erro ao processar documento", error);
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
                                                        totalPages);
                                            });
                                } else {
                                    pagesFlux = Flux.range(1, totalPages)
                                            .flatMap(pageNumber -> {
                                                log.debug("Processando página {}/{} (paralelo)", pageNumber,
                                                        totalPages);
                                                return processPageWithMetadata(document, pdfBytes, pageNumber,
                                                        totalPages);
                                            });
                                }

                                return pagesFlux.collectList();
                            })
                            .flatMap(pageResults -> {
                                // Extrair todas as entries
                                List<PayrollEntry> allEntries = new ArrayList<>();

                                log.info(
                                        "════════════════════════════════════════════════════════════════════════════════");
                                log.info("📊 CONSOLIDANDO ENTRIES DE TODAS AS PÁGINAS:");
                                log.info(
                                        "════════════════════════════════════════════════════════════════════════════════");

                                for (PageResult pageResult : pageResults) {
                                    int pageEntriesCount = pageResult.getEntries().size();
                                    log.info("Página contribuiu com {} entries", pageEntriesCount);
                                    allEntries.addAll(pageResult.getEntries());
                                }

                                log.info(
                                        "════════════════════════════════════════════════════════════════════════════════");
                                log.info("📊 TOTAL DE ENTRIES EXTRAÍDAS DE TODAS AS PÁGINAS: {}", allEntries.size());
                                log.info(
                                        "════════════════════════════════════════════════════════════════════════════════");

                                // Continuar com o processamento das entries
                                return processEntries(allEntries);
                            });
                });
    }

    /**
     * Processa as entries extraídas (validação e persistência).
     */
    private Mono<Long> processEntries(List<PayrollEntry> allEntries) {
        if (allEntries.isEmpty()) {
            log.warn("Nenhuma rubrica foi extraída do documento");
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

        // Validar cada entry antes de salvar
        return Flux.fromIterable(allEntries)
                .flatMap(this::validateEntry)
                .collectList()
                .flatMap(validEntries -> {
                    log.info("=== RESULTADO DA VALIDAÇÃO ===");
                    log.info("Entries extraídas: {}", allEntries.size());
                    log.info("Entries válidas (rubrica encontrada no banco): {}", validEntries.size());
                    log.info("Entries ignoradas (rubrica não encontrada): {}",
                            allEntries.size() - validEntries.size());

                    if (validEntries.isEmpty()) {
                        log.warn("⚠️ Nenhuma entry válida após validação de rubricas. " +
                                "Verifique se as rubricas estão cadastradas na API 1 (Rubricas).");
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

                    return entryRepository.saveAll(Flux.fromIterable(validEntries))
                            .count()
                            .doOnNext(count -> {
                                log.info(
                                        "════════════════════════════════════════════════════════════════════════════════");
                                log.info("✅ {} ENTRIES SALVAS COM SUCESSO NO MONGODB", count);
                                log.info(
                                        "════════════════════════════════════════════════════════════════════════════════");
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
     * Processa uma página específica e retorna as entries extraídas + metadados.
     * Para páginas escaneadas (texto < 100 chars), usa extração JSON estruturada do Gemini (Fase 2).
     * Para páginas digitais, usa o parser regex tradicional.
     */
    private Mono<PageResult> processPageWithMetadata(PayrollDocument document, byte[] pdfBytes,
            int pageNumber, int totalPages) {
        // Tentar extrair texto normalmente primeiro
        return pdfService.extractTextFromPage(new ByteArrayInputStream(pdfBytes), pageNumber)
                .flatMap(pageText -> {
                    // Se o texto extraído for muito pequeno, tentar usar Gemini AI com JSON estruturado
                    if (pageText == null || pageText.trim().length() < MIN_TEXT_LENGTH_FOR_PDF) {
                        log.info(
                                "🔍 Texto extraído muito pequeno ({} caracteres) na página {}. Tentando Gemini AI (JSON estruturado)...",
                                pageText != null ? pageText.length() : 0, pageNumber);

                        // Verificar se Gemini está habilitado
                        if (aiPdfExtractionService.isEnabled()) {
                            log.info("🤖 Usando Gemini AI para extração ESTRUTURADA (JSON) da página {}...", pageNumber);

                            // Fase 2: Usar extractPayrollData (JSON) ao invés de extractTextFromScannedPage (texto cru)
                            return aiPdfExtractionService.extractPayrollData(pdfBytes, pageNumber)
                                    .flatMap(jsonResponse -> {
                                        if (jsonResponse == null || jsonResponse.trim().isEmpty()) {
                                            log.warn("⚠️ Gemini não retornou dados da página {}", pageNumber);
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
                                            return Mono.just(new PageResult(new ArrayList<>()));
                                        }

                                        // Validação Fase 2: verificar consistência dos dados extraídos
                                        ValidationResult validation = validationService.validatePayrollExtraction(
                                                parsedData.getEntries(),
                                                parsedData.getSalarioBruto(),
                                                parsedData.getTotalDescontos(),
                                                parsedData.getSalarioLiquido(),
                                                parsedData.getCpf(),
                                                parsedData.getCompetencia()
                                        );

                                        log.info("📊 Página {} (Gemini JSON) - {} rubricas extraídas, score={}, recommendation={}",
                                                pageNumber, parsedData.getEntries().size(),
                                                String.format("%.2f", validation.confidenceScore()),
                                                validation.recommendation());

                                        if (!validation.issues().isEmpty()) {
                                            for (ValidationIssue issue : validation.issues()) {
                                                log.warn("  ⚠️ Validação: [{}] {} — esperado={}, encontrado={}",
                                                        issue.type(), issue.message(),
                                                        issue.expected(), issue.found());
                                            }
                                        }

                                        // Fase 3: Cross-Validation — se score < 0.85, executar 2ª extração
                                        if (!validation.isValid()) {
                                            log.info("🔄 Score {} < 0.85 — Acionando CROSS-VALIDATION (Fase 3) na página {}...",
                                                    String.format("%.2f", validation.confidenceScore()), pageNumber);

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

                                                // Fase 4: Se cross-validation indica revisão manual, escalar para Gemini Pro
                                                if (crossResult.requiresManualReview()) {
                                                    log.info("⬆️ ESCALAÇÃO PARA GEMINI PRO (Fase 4) — Página {} com campos críticos divergentes",
                                                            pageNumber);

                                                    return aiPdfExtractionService.extractPayrollDataWithFallback(pdfBytes, pageNumber)
                                                            .map(proJsonResponse -> {
                                                                if (proJsonResponse == null || proJsonResponse.trim().isEmpty()) {
                                                                    log.warn("⚠️ Gemini Pro não retornou dados da página {}. Usando resultado da cross-validation.",
                                                                            pageNumber);
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
                                                                    return new PageResult(proData.getEntries());
                                                                }

                                                                log.warn("⚠️ Gemini Pro não extraiu rubricas da página {}. Usando cross-validation.",
                                                                        pageNumber);
                                                                return new PageResult(crossResult.consolidatedEntries());
                                                            })
                                                            .onErrorResume(proError -> {
                                                                log.error("❌ Erro ao escalar para Gemini Pro na página {}: {}. Usando cross-validation.",
                                                                        pageNumber, proError.getMessage());
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
                                        log.error("❌ Erro ao usar Gemini JSON na página {}: {}. Tentando extração de texto...",
                                                pageNumber, error.getMessage());
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
                    boolean needsImageTextExtraction = pageText == null
                            || pageText.trim().length() < MIN_TEXT_LENGTH_FOR_PDF;
                    log.debug("Primeira página tem {} caracteres. Precisa de extração de texto de imagem: {}",
                            pageText != null ? pageText.length() : 0, needsImageTextExtraction);
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
