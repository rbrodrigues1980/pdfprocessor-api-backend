package br.com.verticelabs.pdfprocessor.domain.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "audit_logs")
public class AuditLog {
    @Id
    private String id;
    
    @Indexed
    private String tenantId; // Tenant relacionado ao evento
    
    private String userId; // Usuário que executou a ação
    
    @Indexed
    private Instant timestamp; // Data/hora do evento
    
    @Indexed
    private String evento; // Tipo de evento (LOGIN, LOGIN_FAILED, REFRESH_TOKEN, etc.)
    
    @Builder.Default
    private Map<String, Object> detalhes = new HashMap<>(); // Detalhes adicionais (IP, user agent, etc.)
}

