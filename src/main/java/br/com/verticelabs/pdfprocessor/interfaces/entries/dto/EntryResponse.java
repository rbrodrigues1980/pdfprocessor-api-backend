package br.com.verticelabs.pdfprocessor.interfaces.entries.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EntryResponse {
    private String id;
    private String documentId;
    private String rubricaCodigo;
    private String rubricaDescricao;
    private String referencia; // YYYY-MM
    private BigDecimal valor;
    private String origem; // CAIXA, FUNCEF ou INCOME_TAX
    private Integer pagina;

    // Campos opcionais da rubrica (quando disponível)
    private String rubricaCategoria; // Categoria da rubrica (ex: "Administrativa", "Extraordinária")
    private Boolean rubricaAtivo; // Indica se a rubrica está ativa
}
