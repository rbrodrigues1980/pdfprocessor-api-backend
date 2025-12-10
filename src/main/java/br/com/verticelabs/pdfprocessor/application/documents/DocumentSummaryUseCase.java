package br.com.verticelabs.pdfprocessor.application.documents;

import br.com.verticelabs.pdfprocessor.domain.exceptions.DocumentNotFoundException;
import br.com.verticelabs.pdfprocessor.domain.model.PayrollEntry;
import br.com.verticelabs.pdfprocessor.domain.repository.PayrollDocumentRepository;
import br.com.verticelabs.pdfprocessor.domain.repository.PayrollEntryRepository;
import br.com.verticelabs.pdfprocessor.interfaces.documents.dto.DocumentRubricaSummary;
import br.com.verticelabs.pdfprocessor.interfaces.documents.dto.DocumentSummaryResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentSummaryUseCase {

    private final PayrollDocumentRepository documentRepository;
    private final PayrollEntryRepository entryRepository;

    /**
     * Calcula o resumo de rubricas e estatísticas de um documento
     */
    public Mono<DocumentSummaryResponse> getSummary(String documentId) {
        log.info("=== DocumentSummaryUseCase.getSummary() INICIADO ===");
        log.info("ID do documento: {}", documentId);

        return documentRepository.findById(documentId)
                .switchIfEmpty(Mono.defer(() -> {
                    log.warn("Documento não encontrado: {}", documentId);
                    return Mono.error(new DocumentNotFoundException("Documento não encontrado: " + documentId));
                }))
                .flatMap(document -> {
                    log.info("✓ Documento encontrado: {} - Status: {}", document.getId(), document.getStatus());
                    return entryRepository.findByDocumentoId(documentId)
                            .collectList()
                            .map(entries -> {
                                log.info("✓ {} entries encontradas para documento: {}", entries.size(), documentId);
                                return buildSummary(documentId, entries);
                            });
                })
                .doOnSuccess(summary -> {
                    log.info("✓ Resumo gerado com sucesso para documento: {}", documentId);
                    log.info("Total de entries: {}", summary.getEntriesCount());
                    log.info("Total de rubricas distintas: {}", summary.getRubricasResumo().size());
                })
                .doOnError(error -> {
                    log.error("Erro ao gerar resumo do documento: {}", documentId, error);
                });
    }

    private DocumentSummaryResponse buildSummary(String documentId, List<PayrollEntry> entries) {
        // Contar total de entries
        Long entriesCount = (long) entries.size();

        // Agrupar por código de rubrica e calcular totais
        Map<String, List<PayrollEntry>> entriesByRubrica = entries.stream()
                .collect(Collectors.groupingBy(PayrollEntry::getRubricaCodigo));

        List<DocumentRubricaSummary> rubricasResumo = entriesByRubrica.entrySet().stream()
                .map(entry -> {
                    String codigo = entry.getKey();
                    List<PayrollEntry> entriesDaRubrica = entry.getValue();

                    Long quantidade = (long) entriesDaRubrica.size();
                    Double total = entriesDaRubrica.stream()
                            .mapToDouble(e -> e.getValor() != null ? e.getValor() : 0.0)
                            .sum();

                    return DocumentRubricaSummary.builder()
                            .codigo(codigo)
                            .quantidade(quantidade)
                            .total(total)
                            .build();
                })
                .sorted((a, b) -> a.getCodigo().compareTo(b.getCodigo())) // Ordenar por código
                .collect(Collectors.toList());

        log.debug("Resumo gerado: {} entries, {} rubricas distintas", entriesCount, rubricasResumo.size());

        return DocumentSummaryResponse.builder()
                .documentId(documentId)
                .entriesCount(entriesCount)
                .rubricasResumo(rubricasResumo)
                .build();
    }
}

