package br.com.verticelabs.pdfprocessor.domain.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;

/**
 * Taxa SELIC definida pelo COPOM (Comitê de Política Monetária).
 * Dados obtidos da API do Banco Central do Brasil.
 * 
 * Fonte: https://www.bcb.gov.br/api/servico/sitebcb/historicotaxasjuros
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "taxa_selic")
public class TaxaSelic {

    @Id
    private String id;

    /**
     * Número da reunião do COPOM (1 a 275+)
     */
    @Indexed(unique = true)
    @Field("numeroReuniaoCopom")
    private Integer numeroReuniaoCopom;

    /**
     * Data da reunião do COPOM
     */
    @Field("dataReuniaoCopom")
    private Instant dataReuniaoCopom;

    /**
     * Se foi reunião extraordinária
     */
    @Field("reuniaoExtraordinaria")
    private Boolean reuniaoExtraordinaria;

    /**
     * Viés da decisão (n/a, alta, baixa)
     */
    private String vies;

    /**
     * Se usa meta SELIC
     */
    @Field("usoMetaSelic")
    private Boolean usoMetaSelic;

    /**
     * Data de início da vigência da taxa
     */
    @Indexed
    @Field("dataInicioVigencia")
    private Instant dataInicioVigencia;

    /**
     * Data de fim da vigência (null = vigente atualmente)
     */
    @Field("dataFimVigencia")
    private Instant dataFimVigencia;

    /**
     * Meta SELIC (% a.a.)
     */
    @Field("metaSelic")
    private BigDecimal metaSelic;

    /**
     * Taxa TBAN (% a.m.) - pode ser null
     */
    @Field("taxaTban")
    private BigDecimal taxaTban;

    /**
     * Taxa SELIC efetiva no período de vigência (% período)
     */
    @Field("taxaSelicEfetivaVigencia")
    private BigDecimal taxaSelicEfetivaVigencia;

    /**
     * Taxa SELIC efetiva anualizada (% a.a.)
     */
    @Field("taxaSelicEfetivaAnualizada")
    private BigDecimal taxaSelicEfetivaAnualizada;

    /**
     * Se foi decisão monocrática do presidente
     */
    @Field("decisaoMonocraticaPres")
    private Boolean decisaoMonocraticaPres;

    /**
     * Data da última sincronização com API do BCB
     */
    @Field("syncedAt")
    private LocalDateTime syncedAt;
}
