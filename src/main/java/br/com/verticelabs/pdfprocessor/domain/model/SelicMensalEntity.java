package br.com.verticelabs.pdfprocessor.domain.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Taxa SELIC Mensal (série SGS 4390 do BCB).
 * Esta é a taxa utilizada pela Receita Federal para correção monetária.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "selic_mensal")
@CompoundIndex(name = "idx_ano_mes", def = "{'ano': 1, 'mes': 1}", unique = true)
public class SelicMensalEntity {

    @Id
    private String id;

    @Field("ano")
    private Integer ano;

    @Field("mes")
    private Integer mes;

    @Field("taxa")
    private BigDecimal taxa;

    @Field("dataReferencia")
    private String dataReferencia; // "01/MM/YYYY" do BCB

    @Field("syncedAt")
    private LocalDateTime syncedAt;
}
