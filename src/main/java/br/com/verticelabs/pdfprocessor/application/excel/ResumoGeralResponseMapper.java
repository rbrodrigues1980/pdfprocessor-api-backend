package br.com.verticelabs.pdfprocessor.application.excel;

import br.com.verticelabs.pdfprocessor.domain.model.Person;
import br.com.verticelabs.pdfprocessor.infrastructure.excel.ExcelResumoGeralHelper;
import br.com.verticelabs.pdfprocessor.infrastructure.excel.ExcelResumoGeralLinhaDTO;
import br.com.verticelabs.pdfprocessor.interfaces.excel.dto.ResumoGeralHonorariosResponse;
import br.com.verticelabs.pdfprocessor.interfaces.excel.dto.ResumoGeralLinhaResponse;
import br.com.verticelabs.pdfprocessor.interfaces.excel.dto.ResumoGeralResponse;
import br.com.verticelabs.pdfprocessor.interfaces.excel.dto.ResumoGeralRodapeResponse;
import br.com.verticelabs.pdfprocessor.interfaces.excel.dto.ResumoGeralTotaisResponse;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

@Component
public class ResumoGeralResponseMapper {

    private static final String ATUALIZACAO = "SELIC RECEITA FEDERAL";

    public ResumoGeralResponse toResponse(Person person, ResumoGeralMontagemResult montagem) {
        return ResumoGeralResponse.builder()
                .nome(person.getNome())
                .cpf(person.getCpf())
                .atualizacao(ATUALIZACAO)
                .dataGeracao(montagem.dataGeracao())
                .dataPagamentoSelic(montagem.dataPagamentoSelic())
                .datasVencimento(ExcelResumoGeralHelper.DATAS_VENCIMENTO)
                .linhas(toLinhas(montagem.linhas()))
                .totais(toTotais(montagem.totais()))
                .honorarios(toHonorarios(montagem))
                .rodape(ResumoGeralRodapeResponse.builder()
                        .responsavelLabel(ExcelResumoGeralHelper.RODAPE_LABEL)
                        .responsavelNome(ExcelResumoGeralHelper.RODAPE_NOME)
                        .economista(ExcelResumoGeralHelper.RODAPE_ECONOMISTA)
                        .build())
                .build();
    }

    private List<ResumoGeralLinhaResponse> toLinhas(List<ExcelResumoGeralLinhaDTO> linhas) {
        return linhas.stream()
                .map(l -> ResumoGeralLinhaResponse.builder()
                        .anoCalendario(l.getAnoCalendario())
                        .valorDeclaracao(l.getValorDeclaracao())
                        .origemValorDeclaracao(l.getOrigemValorDeclaracao())
                        .valorSimulacao(l.getValorSimulacao())
                        .origemValorSimulacao(l.getOrigemValorSimulacao())
                        .principal(l.getPrincipal())
                        .selicAcumulada(l.getSelicAcumulada())
                        .valorCorrecao(l.getValorCorrecao())
                        .principalMaisCorrecao(l.getPrincipalMaisCorrecao())
                        .observacao(l.getObservacao())
                        .dataVencimento(l.getDataVencimento())
                        .build())
                .collect(Collectors.toList());
    }

    private ResumoGeralTotaisResponse toTotais(ExcelResumoGeralHelper.TotaisResumoGeral totais) {
        return ResumoGeralTotaisResponse.builder()
                .totalPrincipal(totais.totalPrincipal())
                .totalCorrecao(totais.totalCorrecao())
                .totalPrincipalMaisCorrecao(totais.totalPrincipalMaisCorrecao())
                .honorarios(totais.honorarios())
                .valorReceber(totais.valorReceber())
                .build();
    }

    private ResumoGeralHonorariosResponse toHonorarios(ResumoGeralMontagemResult montagem) {
        var config = montagem.honorariosConfig();
        return ResumoGeralHonorariosResponse.builder()
                .label(config.formatLabelHonorarios())
                .percentualExibicao(config.percentualExibicao())
                .empresaSigla(config.empresaSigla())
                .build();
    }
}
