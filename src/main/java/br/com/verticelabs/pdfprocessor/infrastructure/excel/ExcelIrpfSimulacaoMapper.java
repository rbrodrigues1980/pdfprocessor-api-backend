package br.com.verticelabs.pdfprocessor.infrastructure.excel;

import br.com.verticelabs.pdfprocessor.application.incometax.IrpfPrevidenciaOficialResolver;
import br.com.verticelabs.pdfprocessor.application.tributacao.IrPagamentosDeducaoAggregator;
import br.com.verticelabs.pdfprocessor.application.tributacao.dto.DeducoesPagamentosDTO;
import br.com.verticelabs.pdfprocessor.application.tributacao.dto.DoacoesBrutasDTO;
import br.com.verticelabs.pdfprocessor.application.tributacao.dto.SimuladorIrpfRequest;
import br.com.verticelabs.pdfprocessor.domain.model.IrpfDeclaracaoData;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;

/**
 * Converte dados extraídos da declaração IRPF em {@link SimuladorIrpfRequest}
 * para simulação via {@link br.com.verticelabs.pdfprocessor.application.tributacao.IrSimuladorMotorService}.
 * <p>
 * Prioriza {@code pagamentosEfetuados[]} / {@code doacoesEfetuadas[]} (granular por código);
 * totais do RESUMO são fallback quando listas detalhadas estiverem ausentes.
 * Na simulação CONTRACHEQUES_EXTRA, a previdência complementar da planilha prevalece sobre pagamentos/RESUMO.
 */
@Component
@RequiredArgsConstructor
public class ExcelIrpfSimulacaoMapper {

    private final IrPagamentosDeducaoAggregator pagamentosAggregator;

    public SimuladorIrpfRequest fromDeclaracao(IrpfDeclaracaoData data, BigDecimal previdenciaPrivada) {
        return fromDeclaracao(data, previdenciaPrivada, false);
    }

    public SimuladorIrpfRequest fromDeclaracao(
            IrpfDeclaracaoData data,
            BigDecimal previdenciaPrivada,
            boolean preferirPrevidenciaPlanilha) {

        int qtdDependentes = data.getDependentes() != null ? data.getDependentes().size() : 0;
        int qtdAlimentandos = data.getAlimentandos() != null ? data.getAlimentandos().size() : 0;
        Integer anoCalendario = parseAno(data.getAnoCalendario());

        SimuladorIrpfRequest.SimuladorIrpfRequestBuilder builder = SimuladorIrpfRequest.builder()
                .anoCalendario(anoCalendario)
                .tipoIncidencia("ANUAL")
                .rendimentosTributaveis(nvl(data.getRendimentosTributaveisTotal()))
                .previdenciaOficial(IrpfPrevidenciaOficialResolver.resolver(data))
                .qtdDependentes(qtdDependentes)
                .qtdAlimentandos(qtdAlimentandos)
                .deducaoDependentesDeclarada(nvl(data.getDeducaoDependentes()))
                .impostoDevidoRRA(nvl(data.getImpostoSobreRRA()))
                .impostoRetidoFonteTitular(nvl(data.getImpostoRetidoFonteTitular()))
                .impostoRetidoFonteDependentes(nvl(data.getImpostoRetidoDependentes()))
                .carneLeaoTitular(nvl(data.getCarneLeaoTitular()))
                .carneLeaoDependentes(nvl(data.getCarneLeaoDependentes()))
                .impostoComplementar(nvl(data.getImpostoComplementar()))
                .impostoPagoExterior(nvl(data.getImpostoPagoExterior()))
                .impostoRetidoFonteLei11033(nvl(data.getImpostoRetidoFonteLei11033()))
                .impostoRetidoRRA(nvl(data.getImpostoRetidoRRA()));

        aplicarPagamentos(data, previdenciaPrivada, preferirPrevidenciaPlanilha, builder);
        aplicarDoacoes(data, builder);

        return builder.build();
    }

    private void aplicarPagamentos(
            IrpfDeclaracaoData data,
            BigDecimal previdenciaPrivadaParam,
            boolean preferirPrevidenciaPlanilha,
            SimuladorIrpfRequest.SimuladorIrpfRequestBuilder builder) {

        List<IrpfDeclaracaoData.PagamentoEfetuadoIrpf> pagamentos = data.getPagamentosEfetuados();
        DeducoesPagamentosDTO agg = pagamentosAggregator.aggregate(pagamentos, data);

        if (agg.isFonteGranular()) {
            builder
                    .despesasMedicas(nvl(agg.getDespesasMedicas()))
                    .despesasInstrucaoTitular(nvl(agg.getDespesasInstrucaoTitular()))
                    .despesasInstrucaoDependentes(agg.getDespesasInstrucaoDependentes())
                    .despesasInstrucaoAlimentandos(agg.getDespesasInstrucaoAlimentandos())
                    .pensaoAlimenticia(nvl(agg.getPensaoAlimenticia()))
                    .previdenciaEmpregadoDomestico(nvl(agg.getPrevidenciaEmpregadoDomestico()))
                    .previdenciaPrivada(resolverPrevidenciaPrivada(
                            previdenciaPrivadaParam, agg.getPrevidenciaPrivada(), preferirPrevidenciaPlanilha));
        } else {
            builder
                    .despesasMedicas(nvl(data.getDespesasMedicas()))
                    .despesasInstrucaoDeclarada(nvl(data.getDespesasInstrucao()))
                    .pensaoAlimenticia(nvl(data.getPensaoAlimenticiaJudicial())
                            .add(nvl(data.getPensaoAlimenticiaEscrituraPublica())))
                    .previdenciaEmpregadoDomestico(nvl(data.getContribuicaoPatronalPrevidenciaSocial()))
                    .previdenciaPrivada(resolverPrevidenciaPrivada(
                            previdenciaPrivadaParam, data.getContribuicaoPrevidenciaPrivada(), preferirPrevidenciaPlanilha));
        }
    }

    /**
     * Simulação 1 (declaração): pagamentos/RESUMO prevalecem sobre parâmetro da planilha.
     * Simulação 2 (contracheques): total da planilha prevalece quando informado.
     */
    private BigDecimal resolverPrevidenciaPrivada(
            BigDecimal previdenciaPlanilha,
            BigDecimal previdenciaDeclaracao,
            boolean preferirPrevidenciaPlanilha) {

        if (preferirPrevidenciaPlanilha && nvl(previdenciaPlanilha).compareTo(BigDecimal.ZERO) > 0) {
            return previdenciaPlanilha;
        }
        BigDecimal pgbl = nvl(previdenciaDeclaracao);
        if (pgbl.compareTo(BigDecimal.ZERO) > 0) {
            return pgbl;
        }
        return nvl(previdenciaPlanilha);
    }

    private void aplicarDoacoes(IrpfDeclaracaoData data, SimuladorIrpfRequest.SimuladorIrpfRequestBuilder builder) {
        DoacoesBrutasDTO doacoes = pagamentosAggregator.aggregateDoacoes(data.getDoacoesEfetuadas());
        boolean temDoacoesGranulares = data.getDoacoesEfetuadas() != null && !data.getDoacoesEfetuadas().isEmpty();

        if (temDoacoesGranulares) {
            builder
                    .deducaoIncentivo(nvl(doacoes.getDeducaoIncentivoBruta()))
                    .dedPronon(nvl(doacoes.getDedPrononBruta()))
                    .dedPronas(nvl(doacoes.getDedPronasBruta()));
        } else {
            builder.deducaoIncentivo(nvl(data.getDeducaoIncentivo()));
        }
    }

    private Integer parseAno(String anoCalendario) {
        if (anoCalendario == null || anoCalendario.isBlank()) {
            return null;
        }
        try {
            return Integer.parseInt(anoCalendario.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private BigDecimal nvl(BigDecimal v) {
        return v != null ? v : BigDecimal.ZERO;
    }
}
