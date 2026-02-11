package br.com.verticelabs.pdfprocessor.domain.service;

import reactor.core.publisher.Mono;

import java.util.Optional;

public interface MonthYearDetectionService {
    /**
     * Detecta mês e ano de pagamento no texto de uma página.
     * Procura por padrões como "JANEIRO / 2016" ou "Mês/Ano de Pagamento: JANEIRO / 2016"
     * 
     * @param pageText Texto extraído da página
     * @return Optional com o mês/ano no formato "YYYY-MM" (ex: "2016-01") ou empty se não encontrado
     */
    Mono<Optional<String>> detectMonthYear(String pageText);
}

