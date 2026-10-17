package br.com.verticelabs.pdfprocessor.interfaces.persons.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
public class AjusteSimulacaoIrpfResponse {
    private List<DeclaracaoDisponivel> declaracoes;
    private List<Ajuste> ajustes;

    @Data
    @Builder
    public static class DeclaracaoDisponivel {
        private String anoCalendario;
        private String exercicio;
    }

    @Data
    @Builder
    public static class Ajuste {
        private String anoCalendario;
        private String exercicio;
        private String tipo;
        private String area;
        private String rotuloArea;
        private String rotulo;
        private BigDecimal valor;
    }
}
