package br.com.verticelabs.pdfprocessor.infrastructure.excel;

import br.com.verticelabs.pdfprocessor.application.incometax.IrpfPrevidenciaOficialResolver;
import br.com.verticelabs.pdfprocessor.application.tributacao.IrPagamentosDeducaoAggregator;
import br.com.verticelabs.pdfprocessor.application.tributacao.dto.DeducoesPagamentosDTO;
import br.com.verticelabs.pdfprocessor.application.tributacao.dto.DoacoesBrutasDTO;
import br.com.verticelabs.pdfprocessor.application.tributacao.dto.SimuladorIrpfRequest;
import br.com.verticelabs.pdfprocessor.domain.model.IrpfDeclaracaoData;
import br.com.verticelabs.pdfprocessor.domain.tributacao.IrCodigoDeducao;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

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

    private static final Pattern PREFIXO_FUNDO_DIRETA = Pattern.compile(
            "^(Municipal|Estadual|Distrital|Federal|Nacional)\\s+-\\s+", Pattern.CASE_INSENSITIVE);

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
                .previdenciaOficial(previdenciaOficialSimulacao(data))
                .qtdDependentes(qtdDependentes)
                .qtdAlimentandos(qtdAlimentandos)
                // Zero/null: motor calcula qtd × valorDependente (Ilka 2017 RESUMO capturava 0,00)
                .deducaoDependentesDeclarada(
                        isPositive(data.getDeducaoDependentes()) ? data.getDeducaoDependentes() : null)
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

        BigDecimal previdenciaPrivada = data.getAjusteSimulacaoPrevComplementar() != null
                ? data.getAjusteSimulacaoPrevComplementar()
                : null;
        if (previdenciaPrivada != null) {
            builder.previdenciaPrivadaSemTeto(true);
        }
        if (agg.isFonteGranular()) {
            builder
                    .despesasMedicas(nvl(agg.getDespesasMedicas()))
                    .despesasInstrucaoTitular(nvl(agg.getDespesasInstrucaoTitular()))
                    .despesasInstrucaoDependentes(agg.getDespesasInstrucaoDependentes())
                    .despesasInstrucaoAlimentandos(agg.getDespesasInstrucaoAlimentandos())
                    .pensaoAlimenticia(nvl(agg.getPensaoAlimenticia()).add(nvl(agg.getPensaoEscritura())))
                    .previdenciaEmpregadoDomestico(nvl(agg.getPrevidenciaEmpregadoDomestico()))
                    .previdenciaPrivada(previdenciaPrivada != null
                            ? previdenciaPrivada
                            : resolverPrevidenciaPrivada(
                                    previdenciaPrivadaParam, agg.getPrevidenciaPrivada(), preferirPrevidenciaPlanilha));
        } else {
            builder
                    .despesasMedicas(nvl(data.getDespesasMedicas()))
                    .despesasInstrucaoDeclarada(nvl(data.getDespesasInstrucao()))
                    .pensaoAlimenticia(nvl(data.getPensaoAlimenticiaJudicial())
                            .add(nvl(data.getPensaoAlimenticiaEscrituraPublica())))
                    .previdenciaEmpregadoDomestico(nvl(data.getContribuicaoPatronalPrevidenciaSocial()))
                    .previdenciaPrivada(previdenciaPrivada != null
                            ? previdenciaPrivada
                            : resolverPrevidenciaPrivada(
                                    previdenciaPrivadaParam,
                                    data.getContribuicaoPrevidenciaPrivada(),
                                    preferirPrevidenciaPlanilha));
        }
    }

    /** Ajuste manual substitui a prev. oficial; o RRA manual soma na base, em linha própria na planilha. */
    private BigDecimal previdenciaOficialSimulacao(IrpfDeclaracaoData data) {
        BigDecimal oficial = data.getAjusteSimulacaoPrevOficialPublica() != null
                ? data.getAjusteSimulacaoPrevOficialPublica()
                : IrpfPrevidenciaOficialResolver.resolver(data);
        if (data.getAjusteSimulacaoPrevOficialRra() != null) {
            oficial = oficial.add(data.getAjusteSimulacaoPrevOficialRra());
        }
        return oficial;
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
        List<IrpfDeclaracaoData.DoacaoEfetuadaIrpf> todas = data.getDoacoesEfetuadas() != null
                ? data.getDoacoesEfetuadas()
                : List.of();
        List<IrpfDeclaracaoData.DoacaoEfetuadaIrpf> duranteAno = new ArrayList<>();
        BigDecimal diretoEca = BigDecimal.ZERO;
        BigDecimal diretoIdoso = BigDecimal.ZERO;
        for (IrpfDeclaracaoData.DoacaoEfetuadaIrpf doacao : todas) {
            IrCodigoDeducao codigo = codigoDiretaNaDeclaracao(doacao);
            if (codigo == IrCodigoDeducao.C_40) {
                diretoEca = diretoEca.add(nvl(doacao.getValorDoado()));
            } else if (codigo == IrCodigoDeducao.C_43) {
                diretoIdoso = diretoIdoso.add(nvl(doacao.getValorDoado()));
            } else {
                duranteAno.add(doacao);
            }
        }
        BigDecimal diretas = diretoEca.add(diretoIdoso);

        DoacoesBrutasDTO doacoes = pagamentosAggregator.aggregateDoacoes(duranteAno);
        boolean temDoacoesGranulares = !todas.isEmpty();
        BigDecimal incentivoGranular = temDoacoesGranulares
                ? nvl(doacoes.getDeducaoIncentivoBruta()).add(diretas)
                : BigDecimal.ZERO;
        BigDecimal incentivoResumo = nvl(data.getDeducaoIncentivo());
        // RESUMO igual a Imposto devido I / total não é incentivo (layout embaralhado).
        if (incentivoResumo.compareTo(BigDecimal.ZERO) > 0
                && (incentivoResumo.compareTo(nvl(data.getImpostoDevidoI())) == 0
                        || incentivoResumo.compareTo(nvl(data.getTotalImpostoDevido())) == 0)) {
            incentivoResumo = BigDecimal.ZERO;
        }
        // Alice AC 2016: Gemini/enrich copiou INSS doméstico em dedução de incentivo.
        BigDecimal domestico = nvl(data.getContribuicaoPatronalPrevidenciaSocial());
        if (incentivoResumo.compareTo(BigDecimal.ZERO) > 0
                && domestico.compareTo(BigDecimal.ZERO) > 0
                && incentivoResumo.compareTo(domestico) == 0) {
            incentivoResumo = BigDecimal.ZERO;
        }
        // Individuais (ECA 40 + Idoso 43) conferidos com o RESUMO: o maior prevalece
        // quando a extração granular perdeu uma seção (Helena 2023: 271,74 vs 543,48).
        // Cód. 99 não entra no granular — RESUMO ECA/Idoso permanece (Ilka 2017).
        // Diretas na declaração têm teto próprio (3%) no motor; o excedente do RESUMO fica no "durante o ano".
        builder.deducaoIncentivo(incentivoGranular.max(incentivoResumo).subtract(diretas).max(BigDecimal.ZERO))
                .incentivoDiretoEca(diretoEca)
                .incentivoDiretoIdoso(diretoIdoso);
        if (temDoacoesGranulares) {
            builder
                    .dedPronon(nvl(doacoes.getDedPrononBruta()))
                    .dedPronas(nvl(doacoes.getDedPronasBruta()));
        }
    }

    /**
     * ECA (40) / Pessoa Idosa (43) informados diretamente na declaração: o parser grava
     * "TIPO DE FUNDO - FUNDO" (ex.: "Municipal - MG - SAO DOMINGOS DO PRATA").
     *
     * @return C_40/C_43 quando direta; null caso contrário
     */
    static IrCodigoDeducao codigoDiretaNaDeclaracao(IrpfDeclaracaoData.DoacaoEfetuadaIrpf doacao) {
        if (doacao == null || doacao.getNomeBeneficiario() == null
                || !PREFIXO_FUNDO_DIRETA.matcher(doacao.getNomeBeneficiario().trim()).find()) {
            return null;
        }
        IrCodigoDeducao codigo = IrCodigoDeducao.fromCodigo(doacao.getCodigo()).orElse(null);
        return codigo == IrCodigoDeducao.C_40 || codigo == IrCodigoDeducao.C_43 ? codigo : null;
    }

    private boolean isPositive(BigDecimal v) {
        return v != null && v.compareTo(BigDecimal.ZERO) > 0;
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
