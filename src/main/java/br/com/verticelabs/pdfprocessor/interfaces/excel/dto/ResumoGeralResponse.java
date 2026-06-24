package br.com.verticelabs.pdfprocessor.interfaces.excel.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ResumoGeralResponse {
    private String nome;
    private String cpf;
    private String atualizacao;
    private LocalDateTime dataGeracao;
    private LocalDate dataPagamentoSelic;
    private Map<String, LocalDate> datasVencimento;
    private List<ResumoGeralLinhaResponse> linhas;
    private ResumoGeralTotaisResponse totais;
    private ResumoGeralHonorariosResponse honorarios;
    private ResumoGeralRodapeResponse rodape;
}
