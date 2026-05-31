package br.com.verticelabs.pdfprocessor.domain.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "rubricas")
public class Rubrica {
    @Id
    private String id;

    @Indexed(unique = true)
    private String codigo;

    private String descricao;

    private String categoria;

    @Builder.Default
    private Boolean ativo = true;
}
