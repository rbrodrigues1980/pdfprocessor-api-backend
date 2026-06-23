package br.com.verticelabs.pdfprocessor.domain.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.Date;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "logs")
public class SystemLogEntry {

    @Id
    private String id;
    private Date timestamp;
    private String level;
    private String logger;
    private String thread;
    private String message;
    private String exception;
    private Map<String, String> context;
}
