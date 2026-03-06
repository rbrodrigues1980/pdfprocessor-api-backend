package br.com.verticelabs.pdfprocessor.domain.service;

import reactor.core.publisher.Mono;

import java.util.List;

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

    /**
     * Extrai dados estruturados de um contracheque usando o modelo fallback (Pro).
     * Chamado na Fase 4 quando Flash + Cross-Validation não são suficientes.
     *
     * @param pdfBytes   bytes do PDF
     * @param pageNumber número da página (1-indexed)
     * @return Mono contendo JSON com dados estruturados do contracheque
     */
    Mono<String> extractPayrollDataWithFallback(byte[] pdfBytes, int pageNumber);

    /**
     * Extrai dados de contracheque de MÚLTIPLAS páginas consecutivas.
     * Envia todas as imagens em uma única request para que o modelo
     * combine dados de um contracheque que se divide entre páginas.
     *
     * @param pdfBytes bytes do PDF
     * @param pages    lista de números de página (1-indexed) a processar juntas
     * @return Mono contendo JSON (array) com dados estruturados
     */
    Mono<String> extractPayrollDataMultiPage(byte[] pdfBytes, List<Integer> pages);

    /**
     * Extrai dados de uma página PARCIAL de contracheque (continuação).
     * Usa prompt otimizado para páginas que são a segunda metade de um contracheque,
     * podendo não ter cabeçalho (nome, CPF, competência).
     *
     * @param pdfBytes   bytes do PDF
     * @param pageNumber número da página (1-indexed)
     * @return Mono contendo JSON com dados estruturados
     */
    Mono<String> extractPayrollDataPartialPage(byte[] pdfBytes, int pageNumber);

    /**
     * Retorna o nome do modelo principal (ex: "gemini-2.5-flash").
     */
    default String getPrimaryModelName() {
        return "unknown";
    }

    /**
     * Retorna o nome do modelo fallback (ex: "gemini-2.5-pro").
     */
    default String getFallbackModelName() {
        return "unknown";
    }
}
