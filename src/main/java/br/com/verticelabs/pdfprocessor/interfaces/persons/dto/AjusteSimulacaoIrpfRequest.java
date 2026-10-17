package br.com.verticelabs.pdfprocessor.interfaces.persons.dto;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class AjusteSimulacaoIrpfRequest {
    private String anoCalendario;
    private String tipo;
    /** Nulo no DELETE. No PUT, nulo apaga o lançamento. */
    private BigDecimal valor;
}
