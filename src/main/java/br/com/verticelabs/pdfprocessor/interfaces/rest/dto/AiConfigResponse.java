package br.com.verticelabs.pdfprocessor.interfaces.rest.dto;

import java.time.Instant;

/**
 * Record de resposta para configuração de IA.
 * 
 * @param enabled               Se a IA está habilitada
 * @param model                 Modelo de IA configurado
 * @param credentialsConfigured Se as credenciais do Google Cloud estão
 *                              configuradas
 * @param projectId             ID do projeto no Google Cloud
 * @param location              Região do Vertex AI
 * @param updatedAt             Data da última atualização
 * @param updatedBy             Usuário que fez a última atualização
 * @param statusMessage         Mensagem de status legível
 */
public record AiConfigResponse(
        Boolean enabled,
        String model,
        Boolean credentialsConfigured,
        String projectId,
        String location,
        Instant updatedAt,
        String updatedBy,
        String statusMessage) {
}
