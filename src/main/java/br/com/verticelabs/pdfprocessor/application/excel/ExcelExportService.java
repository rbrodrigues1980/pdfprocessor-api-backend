package br.com.verticelabs.pdfprocessor.application.excel;

import br.com.verticelabs.pdfprocessor.domain.model.Person;
import br.com.verticelabs.pdfprocessor.interfaces.consolidation.dto.ConsolidatedResponse;
import reactor.core.publisher.Mono;

public interface ExcelExportService {
    Mono<byte[]> generateConsolidationExcel(Person person, ConsolidatedResponse consolidatedResponse, String filename);
}

