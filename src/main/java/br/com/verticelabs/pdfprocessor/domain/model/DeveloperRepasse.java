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
@Document(collection = "developer_repasses")
public class DeveloperRepasse {

    @Id
    private String id;

    @Indexed(unique = true)
    private String personId;

    @Indexed
    private String tenantId;

    private String tenantNome;

    private String cpf;

    private String nomeCliente;

    /** Formato YYYY-MM */
    @Indexed
    private String mesReferencia;

    private Instant validadoEm;

    private BigDecimal valorUnitario;

    private RepasseStatus status;

    private Instant pagoEm;

    private String pagoPor;

    /** Nome de quem recebeu o pagamento (beneficiário do repasse). */
    private String pagoParaNome;

    private String pagoParaEmail;

    private String pagoParaCelular;

    /** Ex.: PIX, TED, transferência. */
    private String formaPagamento;

    /** ID/código do comprovante ou referência bancária. */
    private String referenciaPagamento;

    private String observacao;

    private Instant createdAt;

    private Instant updatedAt;
}
