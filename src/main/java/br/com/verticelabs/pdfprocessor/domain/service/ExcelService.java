package br.com.verticelabs.pdfprocessor.domain.service;

import br.com.verticelabs.pdfprocessor.domain.model.Rubrica;
import reactor.core.publisher.Mono;

import java.util.List;

public interface ExcelService {
    Mono<byte[]> generateExcel(List<Rubrica> rubricas);
}
