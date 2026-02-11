package br.com.verticelabs.pdfprocessor.domain.service;

import reactor.core.publisher.Mono;

/**
 * Interface para serviço de extração de PDFs usando IA.
 * Usado como fallback quando PDFBox/iText não consegue extrair texto
 * (PDFs escaneados/baseados em imagem).
 */
public interface AiPdfExtractionService {

    /**
     * Verifica se o serviço de IA está habilitado e configurado.
     * 
     * @return true se o serviço está disponível
     */
    boolean isEnabled();

    /**
     * Extrai texto de um PDF escaneado usando IA.
     * 
     * @param pdfBytes   bytes do PDF
     * @param pageNumber número da página (1-indexed)
     * @return Mono contendo o texto extraído
     */
    Mono<String> extractTextFromScannedPage(byte[] pdfBytes, int pageNumber);

    /**
     * Extrai dados estruturados de um contracheque.
     * 
     * @param pdfBytes   bytes do PDF
     * @param pageNumber número da página (1-indexed)
     * @return Mono contendo JSON com dados estruturados do contracheque
     */
    Mono<String> extractPayrollData(byte[] pdfBytes, int pageNumber);

    /**
     * Extrai dados estruturados de uma declaração de IR (página resumo).
     * 
     * @param pdfBytes   bytes do PDF
     * @param pageNumber número da página do resumo
     * @return Mono contendo JSON com dados estruturados do IR
     */
    Mono<String> extractIncomeTaxData(byte[] pdfBytes, int pageNumber);

    /**
     * Valida dados extraídos de um contracheque.
     * 
     * @param extractedDataJson JSON com dados extraídos
     * @return Mono contendo JSON com resultado da validação
     */
    Mono<String> validatePayrollData(String extractedDataJson);
}
