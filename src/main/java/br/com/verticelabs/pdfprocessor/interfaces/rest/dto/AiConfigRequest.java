package br.com.verticelabs.pdfprocessor.interfaces.rest.dto;

/**
 * Record para configuração de IA (Gemini 2.5).
 *
 * @param enabled       Habilita ou desabilita o uso de IA para PDFs escaneados
 * @param model         Modelo principal de IA (ex: gemini-2.5-flash)
 * @param fallbackModel Modelo fallback de IA (ex: gemini-2.5-pro)
 */
public record AiConfigRequest(
        Boolean enabled,
        String model,
        String fallbackModel) {
}
