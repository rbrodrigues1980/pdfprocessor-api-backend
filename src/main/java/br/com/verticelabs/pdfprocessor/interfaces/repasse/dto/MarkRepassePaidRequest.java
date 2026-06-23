package br.com.verticelabs.pdfprocessor.interfaces.repasse.dto;

import lombok.Data;

import java.time.Instant;

@Data
public class MarkRepassePaidRequest {
    private Instant pagoEm;
    private String pagoParaNome;
    private String pagoParaEmail;
    private String pagoParaCelular;
    private String formaPagamento;
    private String referenciaPagamento;
    private String observacao;
}
