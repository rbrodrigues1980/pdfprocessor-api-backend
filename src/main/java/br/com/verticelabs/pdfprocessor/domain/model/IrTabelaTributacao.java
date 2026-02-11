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
 * Faixa de tributação do IRPF.
 * Tabela global (única para todos os tenants - dados da Receita Federal).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "ir_tabela_tributacao")
@CompoundIndex(name = "idx_ano_tipo_faixa", def = "{'anoCalendario': 1, 'tipoIncidencia': 1, 'faixa': 1}", unique = true)
public class IrTabelaTributacao {

    @Id
    private String id;

    /**
     * Ano-calendário (ex: 2016, 2023, 2024)
     */
    @Field("anoCalendario")
    private Integer anoCalendario;

    /**
     * Tipo de incidência: ANUAL, MENSAL ou PLR
     */
    @Field("tipoIncidencia")
    private String tipoIncidencia;

    /**
     * Número da faixa (1 a 5)
     */
    private Integer faixa;

    /**
     * Limite inferior da faixa (R$)
     */
    @Field("limiteInferior")
    private BigDecimal limiteInferior;

    /**
     * Limite superior da faixa (R$). NULL = "Acima de"
     */
    @Field("limiteSuperior")
    private BigDecimal limiteSuperior;

    /**
     * Alíquota da faixa (0.275 = 27,5%)
     */
    private BigDecimal aliquota;

    /**
     * Valor de dedução da faixa (R$)
     */
    private BigDecimal deducao;

    /**
     * Descrição da faixa (opcional)
     */
    private String descricao;

    @Field("createdAt")
    private LocalDateTime createdAt;

    @Field("updatedAt")
    private LocalDateTime updatedAt;
}
