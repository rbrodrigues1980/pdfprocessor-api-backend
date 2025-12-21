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
 * Parâmetros anuais adicionais do IRPF.
 * Tabela global (única para todos os tenants - dados da Receita Federal).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "ir_parametros_anuais")
@CompoundIndex(name = "idx_ano_tipo", def = "{'anoCalendario': 1, 'tipoIncidencia': 1}", unique = true)
public class IrParametrosAnuais {

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
     * Dedução anual/mensal por dependente (R$)
     */
    @Field("deducaoDependente")
    private BigDecimal deducaoDependente;

    /**
     * Limite anual de despesa com instrução (R$)
     */
    @Field("limiteInstrucao")
    private BigDecimal limiteInstrucao;

    /**
     * Limite de desconto simplificado (R$)
     */
    @Field("limiteDescontoSimplificado")
    private BigDecimal limiteDescontoSimplificado;

    /**
     * Rendimentos previdenciários isentos para maiores de 65 anos (R$)
     */
    @Field("isencao65Anos")
    private BigDecimal isencao65Anos;

    @Field("createdAt")
    private LocalDateTime createdAt;

    @Field("updatedAt")
    private LocalDateTime updatedAt;
}
