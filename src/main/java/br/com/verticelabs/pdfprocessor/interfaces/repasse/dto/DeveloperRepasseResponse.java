package br.com.verticelabs.pdfprocessor.interfaces.repasse.dto;

import br.com.verticelabs.pdfprocessor.domain.model.RepasseStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DeveloperRepasseResponse {
    private String id;
    private String personId;
    private String tenantId;
    private String tenantNome;
    private String cpf;
    private String nomeCliente;
    private String mesReferencia;
    private Instant validadoEm;
    private BigDecimal valorUnitario;
    private RepasseStatus status;
    private Instant pagoEm;
    private String pagoPor;
    private String pagoParaNome;
    private String pagoParaEmail;
    private String pagoParaCelular;
    private String formaPagamento;
    private String referenciaPagamento;
    private String observacao;
    private Instant createdAt;
}
