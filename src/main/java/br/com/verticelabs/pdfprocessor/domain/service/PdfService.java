package br.com.verticelabs.pdfprocessor.domain.service;

import reactor.core.publisher.Mono;

import java.io.InputStream;
import java.util.Map;

public interface PdfService {
    Mono<String> extractText(InputStream inputStream);

    Mono<Map<String, String>> extractMetadata(InputStream inputStream);
    
    Mono<String> extractTextFromPage(InputStream inputStream, int pageNumber);
    
    Mono<Integer> getTotalPages(InputStream inputStream);
}
