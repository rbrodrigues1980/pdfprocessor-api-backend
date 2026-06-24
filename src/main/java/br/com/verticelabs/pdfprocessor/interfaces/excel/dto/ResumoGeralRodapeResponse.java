package br.com.verticelabs.pdfprocessor.interfaces.excel.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ResumoGeralRodapeResponse {
    private String responsavelLabel;
    private String responsavelNome;
    private String economista;
}
