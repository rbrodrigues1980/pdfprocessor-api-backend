package br.com.verticelabs.pdfprocessor.infrastructure.excel;

import br.com.verticelabs.pdfprocessor.application.incometax.IrpfPrevidenciaOficialResolver;
import br.com.verticelabs.pdfprocessor.application.tributacao.IrPagamentosDeducaoAggregator;
import br.com.verticelabs.pdfprocessor.application.tributacao.IrSimuladorMotorService;
import br.com.verticelabs.pdfprocessor.application.tributacao.dto.DeducoesPagamentosDTO;
import br.com.verticelabs.pdfprocessor.application.tributacao.dto.SimuladorIrpfRequest;
import br.com.verticelabs.pdfprocessor.domain.model.IrParametrosAnuais;
import br.com.verticelabs.pdfprocessor.domain.model.IrpfDeclaracaoData;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Monta valores da seção DEDUÇÕES (labels RESUMO IRPF) para simulação Completa com planilha.
 */
@Component
@RequiredArgsConstructor
public class ExcelIrpfDeducoesResumoHelper {

    private static final BigDecimal PGBL_LIMITE_PCT = new BigDecimal("0.12");
    private static final RoundingMode RM = RoundingMode.HALF_UP;

    private final IrSimuladorMotorService motorService;
    private final IrPagamentosDeducaoAggregator pagamentosAggregator;

    /**
     * Monta deduções espelhando a declaração entregue (valores do PDF, sem motor/planilha).
     */
    public ExcelIrpfDeducoesResumoDTO montarConformeDeclaracao(IrpfDeclaracaoData data) {
        BigDecimal rendimentos = nvl(data.getRendimentosTributaveisTotal());
        BigDecimal limite12 = rendimentos.multiply(PGBL_LIMITE_PCT).setScale(2, RM);

        BigDecimal prevOficial = IrpfPrevidenciaOficialResolver.resolver(data);
        BigDecimal prevOficialRra = nvl(data.getContribuicaoPrevidenciaOficialRra());
        BigDecimal prevCompl = nvl(data.getContribuicaoPrevidenciaPrivada());
        BigDecimal dependentes = nvl(data.getDeducaoDependentes());
        BigDecimal instrucao = nvl(data.getDespesasInstrucao());
        BigDecimal medicas = nvl(data.getDespesasMedicas());
        BigDecimal pensaoJudicial = nvl(data.getPensaoAlimenticiaJudicial());
        BigDecimal pensaoEscritura = nvl(data.getPensaoAlimenticiaEscrituraPublica());
        BigDecimal pensaoJudicialRra = nvl(data.getPensaoAlimenticiaJudicialRra());
        BigDecimal livroCaixa = nvl(data.getLivroCaixa());

        BigDecimal total = prevOficial
                .add(prevOficialRra)
                .add(prevCompl)
                .add(dependentes)
                .add(instrucao)
                .add(medicas)
                .add(pensaoJudicial)
                .add(pensaoEscritura)
                .add(pensaoJudicialRra)
                .add(livroCaixa)
                .setScale(2, RM);

        return ExcelIrpfDeducoesResumoDTO.builder()
                .prevOficialPublica(prevOficial)
                .prevOficialRra(prevOficialRra)
                .prevComplementarPrivadaEfetiva(prevCompl)
                .prevComplementarPlanilhaBruta(prevCompl)
                .dependentes(dependentes)
                .despesasInstrucao(instrucao)
                .despesasMedicas(medicas)
                .pensaoJudicial(pensaoJudicial)
                .pensaoEscritura(pensaoEscritura)
                .pensaoJudicialRra(pensaoJudicialRra)
                .livroCaixa(livroCaixa)
                .totalDeducoes(total)
                .inssDomesticoCredito(BigDecimal.ZERO)
                .limite12PctRendimentos(limite12)
                .build();
    }

    public ExcelIrpfDeducoesResumoDTO montar(
            IrpfDeclaracaoData data,
            SimuladorIrpfRequest request,
            BigDecimal prevComplementarPlanilha,
            IrParametrosAnuais params) {

        BigDecimal rendimentos = nvl(data.getRendimentosTributaveisTotal());
        BigDecimal limite12 = rendimentos.multiply(PGBL_LIMITE_PCT).setScale(2, RM);

        BigDecimal prevOficial = IrpfPrevidenciaOficialResolver.resolver(data);
        BigDecimal prevComplBruta = nvl(request.getPrevidenciaPrivada());
        if (prevComplBruta.compareTo(BigDecimal.ZERO) <= 0) {
            prevComplBruta = nvl(prevComplementarPlanilha);
        }
        BigDecimal prevComplEfetiva = motorService.calcularPgblEfetivo(prevComplBruta, rendimentos);

        BigDecimal dependentes = resolverDependentes(request, data);
        BigDecimal instrucao = resolverInstrucao(request, data);
        BigDecimal medicas = valorOuFallback(request.getDespesasMedicas(), data.getDespesasMedicas());
        BigDecimal pensaoJudicial = resolverPensaoJudicial(request, data);
        BigDecimal pensaoEscritura = nvl(data.getPensaoAlimenticiaEscrituraPublica());
        BigDecimal inssCredito = motorService.calcularInssDomesticoCredito(request, params);

        BigDecimal total = prevOficial
                .add(prevComplEfetiva)
                .add(dependentes)
                .add(instrucao)
                .add(medicas)
                .add(pensaoJudicial)
                .add(pensaoEscritura)
                .setScale(2, RM);

        return ExcelIrpfDeducoesResumoDTO.builder()
                .prevOficialPublica(prevOficial)
                .prevOficialRra(BigDecimal.ZERO)
                .prevComplementarPrivadaEfetiva(prevComplEfetiva)
                .prevComplementarPlanilhaBruta(prevComplBruta)
                .dependentes(dependentes)
                .despesasInstrucao(instrucao)
                .despesasMedicas(medicas)
                .pensaoJudicial(pensaoJudicial)
                .pensaoEscritura(pensaoEscritura)
                .pensaoJudicialRra(BigDecimal.ZERO)
                .livroCaixa(BigDecimal.ZERO)
                .totalDeducoes(total)
                .inssDomesticoCredito(inssCredito)
                .limite12PctRendimentos(limite12)
                .build();
    }

    private BigDecimal resolverDependentes(SimuladorIrpfRequest request, IrpfDeclaracaoData data) {
        if (request.getDeducaoDependentesDeclarada() != null
                && request.getDeducaoDependentesDeclarada().compareTo(BigDecimal.ZERO) > 0) {
            return request.getDeducaoDependentesDeclarada();
        }
        return nvl(data.getDeducaoDependentes());
    }

    private BigDecimal resolverInstrucao(SimuladorIrpfRequest request, IrpfDeclaracaoData data) {
        if (request.getDespesasInstrucaoDeclarada() != null
                && request.getDespesasInstrucaoDeclarada().compareTo(BigDecimal.ZERO) > 0) {
            return request.getDespesasInstrucaoDeclarada();
        }
        BigDecimal total = nvl(request.getDespesasInstrucaoTitular());
        if (request.getDespesasInstrucaoDependentes() != null) {
            for (BigDecimal v : request.getDespesasInstrucaoDependentes()) {
                total = total.add(nvl(v));
            }
        }
        if (request.getDespesasInstrucaoAlimentandos() != null) {
            for (BigDecimal v : request.getDespesasInstrucaoAlimentandos()) {
                total = total.add(nvl(v));
            }
        }
        if (total.compareTo(BigDecimal.ZERO) > 0) {
            return total;
        }
        return nvl(data.getDespesasInstrucao());
    }

    private BigDecimal resolverPensaoJudicial(SimuladorIrpfRequest request, IrpfDeclaracaoData data) {
        DeducoesPagamentosDTO agg = pagamentosAggregator.aggregate(
                data.getPagamentosEfetuados(), data);
        if (agg.isFonteGranular() && nvl(agg.getPensaoAlimenticia()).compareTo(BigDecimal.ZERO) > 0) {
            return agg.getPensaoAlimenticia();
        }
        if (nvl(request.getPensaoAlimenticia()).compareTo(BigDecimal.ZERO) > 0) {
            return request.getPensaoAlimenticia();
        }
        return nvl(data.getPensaoAlimenticiaJudicial());
    }

    private BigDecimal valorOuFallback(BigDecimal preferido, BigDecimal fallback) {
        if (preferido != null && preferido.compareTo(BigDecimal.ZERO) > 0) {
            return preferido;
        }
        return nvl(fallback);
    }

    private BigDecimal nvl(BigDecimal v) {
        return v != null ? v : BigDecimal.ZERO;
    }
}
