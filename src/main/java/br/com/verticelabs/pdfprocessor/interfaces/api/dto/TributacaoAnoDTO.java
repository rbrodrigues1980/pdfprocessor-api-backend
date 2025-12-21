package br.com.verticelabs.pdfprocessor.interfaces.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * DTO completo para tributação de um ano.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TributacaoAnoDTO {
    private Integer anoCalendario;
    private String tipoIncidencia;
    private List<FaixaTributacaoDTO> faixas;
    private ParametrosAnuaisDTO parametros;
}
