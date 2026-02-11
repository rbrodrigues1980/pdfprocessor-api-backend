package br.com.verticelabs.pdfprocessor.interfaces.rest.dto;

/**
 * Record para configuração de IA.
 * 
 * @param enabled Habilita ou desabilita o uso de IA para PDFs escaneados
 * @param model   Modelo de IA a ser usado (gemini-1.5-flash-002,
 *                gemini-1.5-pro)
 */
public record AiConfigRequest(
        Boolean enabled,
        String model) {
}
