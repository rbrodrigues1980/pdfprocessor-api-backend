package br.com.verticelabs.pdfprocessor.domain.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

/**
 * Configurações do sistema armazenadas no banco de dados.
 * Permite configuração dinâmica sem necessidade de restart.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "system_config")
public class SystemConfig {

    @Id
    private String id;

    /**
     * Chave única da configuração.
     * Ex: "ai.enabled", "ai.model", etc.
     */
    private String key;

    /**
     * Valor da configuração.
     */
    private String value;

    /**
     * Descrição da configuração.
     */
    private String description;

    /**
     * Tenant ID (null = configuração global).
     */
    private String tenantId;

    /**
     * Data de criação.
     */
    private Instant createdAt;

    /**
     * Data da última atualização.
     */
    private Instant updatedAt;

    /**
     * Usuário que fez a última atualização.
     */
    private String updatedBy;

    // Constantes para chaves de configuração
    public static final String KEY_AI_ENABLED = "ai.enabled";
    public static final String KEY_AI_MODEL = "ai.model";
}
