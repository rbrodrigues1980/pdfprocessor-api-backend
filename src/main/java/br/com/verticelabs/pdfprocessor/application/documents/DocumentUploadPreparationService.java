package br.com.verticelabs.pdfprocessor.application.documents;

import br.com.verticelabs.pdfprocessor.domain.model.DetectedPage;
import br.com.verticelabs.pdfprocessor.domain.model.DocumentType;
import br.com.verticelabs.pdfprocessor.domain.model.PayrollDocument;
import br.com.verticelabs.pdfprocessor.domain.repository.PayrollDocumentRepository;
import br.com.verticelabs.pdfprocessor.domain.repository.PersonRepository;
import br.com.verticelabs.pdfprocessor.domain.service.DocumentTypeDetectionService;
import br.com.verticelabs.pdfprocessor.domain.service.GridFsService;
import br.com.verticelabs.pdfprocessor.domain.service.MonthYearDetectionService;
import br.com.verticelabs.pdfprocessor.domain.service.PdfService;
import br.com.verticelabs.pdfprocessor.infrastructure.pdf.DocumentTypeDetectionServiceImpl;
import br.com.verticelabs.pdfprocessor.infrastructure.pdf.SabespPayslipMetadataExtractor;
import br.com.verticelabs.pdfprocessor.infrastructure.pdf.SabesprevFichaMetadataExtractor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.io.ByteArrayInputStream;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Detecta tipo de documento e metadados de páginas após upload rápido (tipo {@link DocumentType#UNKNOWN}).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentUploadPreparationService {

    private final GridFsService gridFsService;
    private final PdfService pdfService;
    private final DocumentTypeDetectionService typeDetectionService;
    private final MonthYearDetectionService monthYearDetectionService;
    private final PayrollDocumentRepository documentRepository;
    private final PersonRepository personRepository;

    public Mono<PayrollDocument> prepareUnknownDocument(PayrollDocument document) {
        log.info("Preparando documento {} (detecção de tipo e metadados)", document.getId());
        return loadPdfFromGridFs(document.getOriginalFileId())
                .flatMap(fileBytes -> pdfService.extractText(new ByteArrayInputStream(fileBytes))
                        .flatMap(pdfText -> typeDetectionService.detectType(pdfText)
                                .flatMap(documentType -> applyPersonMetadataFromPdf(document, documentType, pdfText)
                                        .flatMap(updatedDoc -> detectPageMetadata(fileBytes, documentType)
                                                .flatMap(pageData -> {
                                                    updatedDoc.setTipo(documentType);
                                                    updatedDoc.setMesesDetectados(pageData.mesesDetectados);
                                                    updatedDoc.setDetectedPages(pageData.detectedPages);
                                                    updatedDoc.setAnoDetectado(pageData.anoDetectado);
                                                    return documentRepository.save(updatedDoc);
                                                })))));
    }

    private Mono<PayrollDocument> applyPersonMetadataFromPdf(
            PayrollDocument document, DocumentType documentType, String pdfText) {
        if (documentType != DocumentType.SABESP && documentType != DocumentType.SABESPREV_FICHA) {
            return Mono.just(document);
        }
        return personRepository.findByTenantIdAndCpf(document.getTenantId(), document.getCpf())
                .flatMap(person -> {
                    boolean changed = false;
                    if (documentType == DocumentType.SABESP) {
                        var metaOpt = SabespPayslipMetadataExtractor.extract(pdfText);
                        if (metaOpt.isPresent()) {
                            var meta = metaOpt.get();
                            if ((person.getNome() == null || person.getNome().isBlank()) && meta.nome() != null) {
                                person.setNome(meta.nome());
                                changed = true;
                            }
                            if (meta.matricula() != null) {
                                String matForm = person.getMatricula() != null
                                        ? person.getMatricula().replaceAll("\\D", "") : "";
                                if (matForm.isBlank()) {
                                    person.setMatricula(meta.matricula());
                                    changed = true;
                                }
                            }
                        }
                    } else {
                        var metaOpt = SabesprevFichaMetadataExtractor.extract(pdfText);
                        if (metaOpt.isPresent()) {
                            var meta = metaOpt.get();
                            if ((person.getNome() == null || person.getNome().isBlank()) && meta.nome() != null) {
                                person.setNome(meta.nome());
                                changed = true;
                            }
                            if (meta.matricula() != null) {
                                String matForm = person.getMatricula() != null
                                        ? person.getMatricula().replaceAll("\\D", "") : "";
                                if (matForm.isBlank()) {
                                    person.setMatricula(meta.matricula());
                                    changed = true;
                                }
                            }
                        }
                    }
                    if (!changed) {
                        return Mono.just(document);
                    }
                    person.setUpdatedAt(Instant.now());
                    return personRepository.save(person).thenReturn(document);
                })
                .defaultIfEmpty(document);
    }

    private Mono<byte[]> loadPdfFromGridFs(String fileId) {
        return gridFsService.retrieveFile(fileId)
                .flatMap(inputStream -> Mono.fromCallable(() -> {
                    try {
                        return inputStream.readAllBytes();
                    } finally {
                        inputStream.close();
                    }
                }));
    }

    private Mono<PageData> detectPageMetadata(byte[] fileBytes, DocumentType documentType) {
        return pdfService.getTotalPages(new ByteArrayInputStream(fileBytes))
                .flatMap(totalPages -> {
                    if (totalPages == 0) {
                        return Mono.just(new PageData(new ArrayList<>(), new ArrayList<>(), null));
                    }
                    return Flux.range(1, totalPages)
                            .flatMap(pageNumber -> pdfService.extractTextFromPage(
                                            new ByteArrayInputStream(fileBytes), pageNumber)
                                    .flatMap(pageText -> monthYearDetectionService.detectMonthYear(pageText)
                                            .flatMap(monthYearOpt -> typeDetectionService.detectType(pageText)
                                                    .map(pageType -> {
                                                        DocumentType resolvedType = resolvePageTypeForFuncefPortal(
                                                                documentType, pageType, pageText);
                                                        DetectedPage detectedPage = DetectedPage.builder()
                                                                .page(pageNumber)
                                                                .origem(resolvedType.name())
                                                                .build();
                                                        return new PageResult(
                                                                pageNumber, monthYearOpt, detectedPage, pageText);
                                                    }))))
                            .collectList()
                            .map(pageResults -> aggregatePageData(pageResults, documentType));
                });
    }

    private PageData aggregatePageData(List<PageResult> pageResults, DocumentType documentType) {
        pageResults.sort(java.util.Comparator.comparingInt(r -> r.pageNumber));

        Set<String> mesesSet = new HashSet<>();
        List<DetectedPage> detectedPages = new ArrayList<>();
        Integer anoDetectado = null;
        String lastMonthYear = null;

        for (PageResult result : pageResults) {
            Optional<String> monthYear = result.monthYear;
            if (monthYear.isEmpty()
                    && "FUNCEF".equals(result.detectedPage.getOrigem())
                    && lastMonthYear != null
                    && DocumentTypeDetectionServiceImpl.looksLikeFuncefPortalContinuation(result.pageText)) {
                monthYear = Optional.of(lastMonthYear);
            }

            detectedPages.add(result.detectedPage);

            if (monthYear.isPresent()) {
                String my = monthYear.get();
                mesesSet.add(my);
                lastMonthYear = my;
                try {
                    int ano = Integer.parseInt(my.substring(0, 4));
                    if (anoDetectado == null || ano > anoDetectado) {
                        anoDetectado = ano;
                    }
                } catch (NumberFormatException e) {
                    log.warn("Erro ao extrair ano de: {}", my);
                }
            }
        }

        List<String> mesesDetectados = mesesSet.stream().sorted().collect(Collectors.toList());

        if (documentType == DocumentType.SABESPREV_FICHA) {
            String anoFicha = null;
            for (PageResult result : pageResults) {
                var meta = SabesprevFichaMetadataExtractor.extract(result.pageText);
                if (meta.isPresent() && meta.get().ano() != null) {
                    anoFicha = meta.get().ano();
                    break;
                }
            }
            if (anoFicha != null) {
                anoDetectado = Integer.parseInt(anoFicha);
                mesesDetectados = new ArrayList<>();
                for (int m = 1; m <= 12; m++) {
                    mesesDetectados.add(anoFicha + "-" + String.format("%02d", m));
                }
            }
        }

        return new PageData(mesesDetectados, detectedPages, anoDetectado);
    }

    private static DocumentType resolvePageTypeForFuncefPortal(
            DocumentType documentType, DocumentType pageType, String pageText) {
        if (documentType == DocumentType.SABESP) {
            return DocumentType.SABESP;
        }
        if (documentType == DocumentType.SABESPREV_FICHA) {
            return DocumentType.SABESPREV_FICHA;
        }
        if (documentType == DocumentType.FUNCEF
                && pageType != DocumentType.FUNCEF
                && pageType != DocumentType.FUNCEF_DEMONSTRATIVO
                && DocumentTypeDetectionServiceImpl.looksLikeFuncefPortalContinuation(pageText)) {
            return DocumentType.FUNCEF;
        }
        return pageType;
    }

    private static final class PageData {
        final List<String> mesesDetectados;
        final List<DetectedPage> detectedPages;
        final Integer anoDetectado;

        PageData(List<String> mesesDetectados, List<DetectedPage> detectedPages, Integer anoDetectado) {
            this.mesesDetectados = mesesDetectados;
            this.detectedPages = detectedPages;
            this.anoDetectado = anoDetectado;
        }
    }

    private static final class PageResult {
        final int pageNumber;
        final Optional<String> monthYear;
        final DetectedPage detectedPage;
        final String pageText;

        PageResult(int pageNumber, Optional<String> monthYear, DetectedPage detectedPage, String pageText) {
            this.pageNumber = pageNumber;
            this.monthYear = monthYear;
            this.detectedPage = detectedPage;
            this.pageText = pageText;
        }
    }
}
