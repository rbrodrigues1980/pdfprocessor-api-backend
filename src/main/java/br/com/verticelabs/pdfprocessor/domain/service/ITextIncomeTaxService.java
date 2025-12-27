package br.com.verticelabs.pdfprocessor.domain.service;

import reactor.core.publisher.Mono;

import java.io.InputStream;

/**
 * Serviço para extrair informações de declaração de imposto de renda usando
 * iText 8.
 * Oferece extração mais precisa comparado ao PDFBox, especialmente para PDFs
 * com
 * layouts complexos (duas colunas) e tabelas.
 */
public interface ITextIncomeTaxService {

    /**
     * Extrai informações completas da declaração de IR.
     * 
     * @param inputStream Stream do PDF
     * @return Mono com objeto IncomeTaxInfo contendo todas as informações extraídas
     */
    Mono<IncomeTaxDeclarationService.IncomeTaxInfo> extractIncomeTaxInfo(InputStream inputStream);

    /**
     * Extrai o texto bruto de todas as páginas do PDF.
     * Útil para debug e análise do conteúdo.
     * 
     * @param inputStream Stream do PDF
     * @return Mono com texto bruto extraído
     */
    Mono<String> extractRawText(InputStream inputStream);

    /**
     * Extrai o texto bruto de uma página específica do PDF.
     * 
     * @param inputStream Stream do PDF
     * @param pageNumber  Número da página (1-indexed)
     * @return Mono com texto bruto da página
     */
    Mono<String> extractRawTextFromPage(InputStream inputStream, int pageNumber);

    /**
     * Encontra a página RESUMO no PDF.
     * 
     * @param inputStream Stream do PDF
     * @return Mono com número da página RESUMO
     */
    Mono<Integer> findResumoPage(InputStream inputStream);
}
