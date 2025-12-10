package br.com.verticelabs.pdfprocessor.domain.service;

import br.com.verticelabs.pdfprocessor.domain.model.DocumentType;
import reactor.core.publisher.Mono;

public interface DocumentTypeDetectionService {
    Mono<DocumentType> detectType(String pdfText);
}

