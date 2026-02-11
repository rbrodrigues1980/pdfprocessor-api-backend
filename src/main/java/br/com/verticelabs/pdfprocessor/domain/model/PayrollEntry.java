package br.com.verticelabs.pdfprocessor.domain.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "payroll_entries")
public class PayrollEntry {
    @Id
    private String id;

    @Indexed
    private String tenantId; // Tenant ao qual a entry pertence

    @Indexed
    private String documentoId; // Referência ao payroll_documents

    @Indexed
    private String rubricaCodigo; // Código da rubrica (ex: "4482")

    private String rubricaDescricao; // Descrição extraída

    private String referencia; // Mês/ano da rubrica no formato "2017-08" (pode ser "2017-13" para abono anual)

    private String mesPagamento; // Ano Pagamento / Mês do documento no formato "2017-05" (mês do contracheque)

    private BigDecimal valor; // Valor numérico

    private String origem; // CAIXA ou FUNCEF

    private Integer pagina; // Página onde foi extraída (1-indexed)
}
