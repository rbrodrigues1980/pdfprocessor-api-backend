package br.com.verticelabs.pdfprocessor.domain.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "payroll_documents")
public class PayrollDocument {
    @Id
    private String id;
    
    @Indexed
    private String tenantId; // Tenant ao qual o documento pertence
    
    @Indexed
    private String cpf; // CPF associado ao documento
    
    @Indexed
    private String fileHash; // Hash SHA-256 do arquivo para verificar duplicidade (único por tenant)
    
    private DocumentType tipo; // CAIXA, FUNCEF ou CAIXA_FUNCEF
    
    private Integer anoDetectado; // Ano detectado no PDF
    
    @Builder.Default
    private List<String> mesesDetectados = new ArrayList<>(); // Formato: ["2017-01", "2017-02"]
    
    private DocumentStatus status; // PENDING, PROCESSING, PROCESSED, ERROR
    
    @Builder.Default
    private List<DetectedPage> detectedPages = new ArrayList<>(); // Origem por página
    
    private String originalFileId; // ID do arquivo no GridFS (ObjectId como string)
    
    private Instant dataUpload; // Data do upload
    
    private Instant dataProcessamento; // Data do processamento
    
    private Long totalEntries; // Número total de entries extraídas
    
    private String erro; // Mensagem de erro se status = ERROR
    
    private String uploadedBy; // ID do usuário que fez o upload
}

