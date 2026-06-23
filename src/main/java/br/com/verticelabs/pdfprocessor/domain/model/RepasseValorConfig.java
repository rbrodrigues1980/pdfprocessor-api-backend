package br.com.verticelabs.pdfprocessor.domain.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.math.BigDecimal;
import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "repasse_valor_configs")
public class RepasseValorConfig {

    @Id
    private String id;

    private BigDecimal valorUnitario;

    private int anoBase;

    /** Data de corte inclusive — vale para validações a partir deste instante. */
    @Indexed
    private Instant vigenciaDe;

    private Instant createdAt;

    private String createdBy;
}
