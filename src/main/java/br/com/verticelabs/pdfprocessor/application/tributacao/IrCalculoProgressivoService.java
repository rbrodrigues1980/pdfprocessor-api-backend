package br.com.verticelabs.pdfprocessor.application.tributacao;

import br.com.verticelabs.pdfprocessor.application.tributacao.dto.ResultadoCalculoIrpfDTO;
import br.com.verticelabs.pdfprocessor.application.tributacao.dto.ResultadoFaixaCalculoDTO;
import br.com.verticelabs.pdfprocessor.domain.model.IrParametrosAnuais;
import br.com.verticelabs.pdfprocessor.domain.model.IrTabelaTributacao;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Cálculo progressivo anual do IRPF — alinhado à declaração entregue (Receita Federal).
 * <p>
 * Demonstrativo: soma faixa a faixa com limites acumulados por {@code limiteSuperior}.
 * Imposto devido final: fórmula (Base × Alíquota − Parcela) com truncamento em 2 casas
 * (ROUND_DOWN), alinhado ao programa SERPRO/Receita Federal — menos redução anual e deduções especiais.
 */
@Slf4j
@Service
public class IrCalculoProgressivoService {

    private static final int SCALE_CALC = 10;
    private static final RoundingMode RM = RoundingMode.HALF_UP;
    /** Truncamento em 2 casas — comportamento do imposto devido na declaração SERPRO. */
    private static final RoundingMode RM_IMPOSTO = RoundingMode.DOWN;

    /**
     * @param rendimentosTributaveis rendimentos sujeitos à progressiva; exclui RRA (tributação exclusiva)
     */
    public ResultadoCalculoIrpfDTO calcular(
            List<IrTabelaTributacao> faixas,
            BigDecimal rendimentosTributaveis,
            BigDecimal totalDeducoes,
            BigDecimal deducoesEspeciais,
            IrParametrosAnuais parametros) {

        BigDecimal rendimentos = nvl(rendimentosTributaveis);
        BigDecimal deducoes = nvl(totalDeducoes);
        BigDecimal especiais = nvl(deducoesEspeciais);

        BigDecimal baseCalculo = rendimentos.subtract(deducoes).max(BigDecimal.ZERO);

        List<IrTabelaTributacao> ordenadas = faixas.stream()
                .sorted(Comparator.comparingInt(f -> f.getFaixa() != null ? f.getFaixa() : 0))
                .toList();

        List<ResultadoFaixaCalculoDTO> resultadoFaixas = new ArrayList<>();
        BigDecimal limiteSuperiorAnterior = BigDecimal.ZERO;
        BigDecimal impostoAcumulado = BigDecimal.ZERO;

        for (IrTabelaTributacao faixa : ordenadas) {
            if (baseCalculo.compareTo(limiteSuperiorAnterior) <= 0) {
                break;
            }

            BigDecimal tetoFaixa = faixa.getLimiteSuperior() != null
                    ? baseCalculo.min(faixa.getLimiteSuperior())
                    : baseCalculo;

            BigDecimal baseNaFaixa = tetoFaixa.subtract(limiteSuperiorAnterior).max(BigDecimal.ZERO);
            BigDecimal aliquota = nvl(faixa.getAliquota());
            BigDecimal impostoFaixa = baseNaFaixa.multiply(aliquota);

            resultadoFaixas.add(ResultadoFaixaCalculoDTO.builder()
                    .faixa(faixa.getFaixa())
                    .descricao(faixa.getDescricao())
                    .baseNaFaixa(baseNaFaixa.setScale(2, RM))
                    .aliquota(aliquota)
                    .impostoNaFaixa(impostoFaixa.setScale(4, RM))
                    .build());

            impostoAcumulado = impostoAcumulado.add(impostoFaixa);

            if (faixa.getLimiteSuperior() != null) {
                limiteSuperiorAnterior = faixa.getLimiteSuperior();
            }
        }

        BigDecimal impostoProgressivo = calcularImpostoFormula(baseCalculo, ordenadas);

        // Verificação cruzada: soma faixa a faixa vs fórmula (tolerância R$ 0,02)
        BigDecimal impostoSomaFaixas = impostoAcumulado.setScale(2, RM);
        if (impostoProgressivo.subtract(impostoSomaFaixas).abs().compareTo(new BigDecimal("0.02")) > 0) {
            log.warn("Divergência entre fórmula ({}) e soma progressiva ({}) para base {}",
                    impostoProgressivo, impostoSomaFaixas, baseCalculo);
        }

        BigDecimal reducaoAnual = calcularReducaoAnual(rendimentos, impostoProgressivo, parametros);
        BigDecimal impostoAposReducao = impostoProgressivo.subtract(reducaoAnual).max(BigDecimal.ZERO);

        BigDecimal impostoDevido = impostoAposReducao.subtract(especiais).max(BigDecimal.ZERO).setScale(2, RM_IMPOSTO);

        BigDecimal aliquotaEfetiva = BigDecimal.ZERO;
        if (rendimentos.compareTo(BigDecimal.ZERO) > 0) {
            aliquotaEfetiva = impostoDevido
                    .divide(rendimentos, SCALE_CALC, RM)
                    .multiply(new BigDecimal("100"))
                    .setScale(2, RM_IMPOSTO);
        }

        return ResultadoCalculoIrpfDTO.builder()
                .rendimentosTributaveis(rendimentos.setScale(2, RM))
                .totalDeducoes(deducoes.setScale(2, RM))
                .baseCalculo(baseCalculo.setScale(2, RM))
                .faixas(resultadoFaixas)
                .impostoProgressivo(impostoProgressivo)
                .reducaoAnual(reducaoAnual.setScale(2, RM))
                .deducoesEspeciais(especiais.setScale(2, RM))
                .impostoDevido(impostoDevido)
                .aliquotaEfetiva(aliquotaEfetiva)
                .build();
    }

    /**
     * Fórmula direta: (Base × Alíquota) − Dedução da faixa onde a base se enquadra.
     */
    public BigDecimal calcularImpostoFormula(BigDecimal baseCalculo, List<IrTabelaTributacao> faixas) {
        if (baseCalculo == null || baseCalculo.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO;
        }

        for (IrTabelaTributacao faixa : faixas) {
            BigDecimal limInf = nvl(faixa.getLimiteInferior());
            BigDecimal limSup = faixa.getLimiteSuperior();

            boolean acimaInf = baseCalculo.compareTo(limInf) >= 0;
            boolean abaixoSup = limSup == null || baseCalculo.compareTo(limSup) <= 0;

            if (acimaInf && abaixoSup) {
                BigDecimal aliquota = nvl(faixa.getAliquota());
                if (aliquota.compareTo(BigDecimal.ZERO) == 0) {
                    return BigDecimal.ZERO;
                }
                return baseCalculo.multiply(aliquota)
                        .subtract(nvl(faixa.getDeducao()))
                        .setScale(2, RM_IMPOSTO)
                        .max(BigDecimal.ZERO);
            }
        }

        IrTabelaTributacao ultima = faixas.get(faixas.size() - 1);
        return baseCalculo.multiply(nvl(ultima.getAliquota()))
                .subtract(nvl(ultima.getDeducao()))
                .setScale(2, RM_IMPOSTO)
                .max(BigDecimal.ZERO);
    }

    /**
     * Tabela de Redução Anual (Lei 15.270/2025) — a partir do ano-calendário 2026 (Ex. 2027).
     */
    public BigDecimal calcularReducaoAnual(
            BigDecimal rendimentosTributaveis,
            BigDecimal impostoProgressivo,
            IrParametrosAnuais parametros) {

        if (parametros == null || !Boolean.TRUE.equals(parametros.getReducaoAnualAtiva())) {
            return BigDecimal.ZERO;
        }

        BigDecimal rend = nvl(rendimentosTributaveis);
        BigDecimal limiteIsencao = nvl(parametros.getReducaoRendimentoLimiteIsencao());
        BigDecimal limiteSuperior = nvl(parametros.getReducaoRendimentoLimiteSuperior());

        if (limiteIsencao.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO;
        }

        if (rend.compareTo(limiteIsencao) <= 0) {
            // Zera o imposto devido (Lei 15.270/2025 — rendimentos até R$ 60.000)
            return impostoProgressivo.setScale(2, RM);
        }

        if (limiteSuperior.compareTo(BigDecimal.ZERO) > 0 && rend.compareTo(limiteSuperior) >= 0) {
            return BigDecimal.ZERO;
        }

        // Faixa decrescente: constante − (coeficiente × rendimentos)
        BigDecimal constante = nvl(parametros.getReducaoConstanteLinear());
        BigDecimal coeficiente = nvl(parametros.getReducaoCoeficienteLinear());
        BigDecimal reducao = constante.subtract(rend.multiply(coeficiente));

        return reducao.max(BigDecimal.ZERO).min(impostoProgressivo).setScale(2, RM);
    }

    private BigDecimal nvl(BigDecimal v) {
        return v != null ? v : BigDecimal.ZERO;
    }
}
