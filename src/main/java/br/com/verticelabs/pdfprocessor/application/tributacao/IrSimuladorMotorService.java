package br.com.verticelabs.pdfprocessor.application.tributacao;

import br.com.verticelabs.pdfprocessor.application.tributacao.dto.DoacoesBrutasDTO;
import br.com.verticelabs.pdfprocessor.application.tributacao.dto.DoacoesEfetivasDTO;
import br.com.verticelabs.pdfprocessor.application.tributacao.dto.ModeloTributacaoResultDTO;
import br.com.verticelabs.pdfprocessor.application.tributacao.dto.ResumoDeclaracaoDTO;
import br.com.verticelabs.pdfprocessor.application.tributacao.dto.ResultadoCalculoIrpfDTO;
import br.com.verticelabs.pdfprocessor.application.tributacao.dto.SimuladorIrpfRequest;
import br.com.verticelabs.pdfprocessor.application.tributacao.dto.SimuladorIrpfResponse;
import br.com.verticelabs.pdfprocessor.domain.model.IrParametrosAnuais;
import br.com.verticelabs.pdfprocessor.domain.model.IrTabelaTributacao;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

@Service
@RequiredArgsConstructor
public class IrSimuladorMotorService {

    private static final BigDecimal PGBL_LIMITE_PCT = new BigDecimal("0.12");
    private static final BigDecimal DESCONTO_SIMPLIFICADO_PCT = new BigDecimal("0.20");
    private static final int SCALE = 10;
    private static final RoundingMode RM = RoundingMode.HALF_UP;
    /** Truncamento — alíquota efetiva e imposto devido (SERPRO). */
    private static final RoundingMode RM_IMPOSTO = RoundingMode.DOWN;

    private final IrCalculoProgressivoService calculoProgressivoService;
    private final IrDoacoesDeducaoCalculator doacoesCalculator;

    public SimuladorIrpfResponse simular(
            SimuladorIrpfRequest request,
            List<IrTabelaTributacao> faixas,
            IrParametrosAnuais parametros) {

        BigDecimal rendimentosProgressivos = nvl(request.getRendimentosTributaveis());
        BigDecimal rendimentosRRA = nvl(request.getRendimentosRRA());

        DoacoesBrutasDTO doacoesBrutas = doacoesCalculator.fromRequestBrutas(
                request.getDeducaoIncentivo(),
                request.getDedPronon(),
                request.getDedPronas());

        BigDecimal impostoPagoTotal = calcularImpostoPagoTotal(request);
        BigDecimal impostoDevidoRRA = nvl(request.getImpostoDevidoRRA());

        DeducoesCompleto dedCompleto = calcularDeducoesCompleto(request, parametros, rendimentosProgressivos);

        ModeloTributacaoResultDTO completo = calcularModelo(
                "COMPLETO",
                rendimentosProgressivos,
                dedCompleto.total(),
                null,
                faixas,
                parametros,
                doacoesBrutas,
                impostoDevidoRRA,
                impostoPagoTotal,
                request,
                dedCompleto.inssDomesticoCredito());

        BigDecimal descontoSimplificado = calcularDescontoSimplificado(rendimentosProgressivos, parametros);

        ModeloTributacaoResultDTO simplificado = calcularModelo(
                "SIMPLIFICADO",
                rendimentosProgressivos,
                descontoSimplificado,
                descontoSimplificado,
                faixas,
                parametros,
                doacoesBrutas,
                impostoDevidoRRA,
                impostoPagoTotal,
                request,
                BigDecimal.ZERO);

        String recomendado = escolherModelo(completo.getSaldo(), simplificado.getSaldo());

        ResumoDeclaracaoDTO resumo = "COMPLETO".equals(recomendado)
                ? completo.getResumo()
                : simplificado.getResumo();

        return SimuladorIrpfResponse.builder()
                .anoCalendario(request.getAnoCalendario())
                .rendimentosRRA(rendimentosRRA.setScale(2, RM))
                .modeloRecomendado(recomendado)
                .modeloCompleto(completo)
                .modeloSimplificado(simplificado)
                .resumoDeclaracao(resumo)
                .build();
    }

    /**
     * Limite de educação individual por CPF (titular, cada dependente, cada alimentando).
     * Não compartilha saldo entre pessoas.
     */
    BigDecimal calcularEducacaoEfetiva(SimuladorIrpfRequest request, BigDecimal limiteEducacao) {
        int dependentes = request.getQtdDependentes() != null ? request.getQtdDependentes() : 0;
        int alimentandos = request.getQtdAlimentandos() != null ? request.getQtdAlimentandos() : 0;

        BigDecimal efetiva = capEducacao(nvl(request.getDespesasInstrucaoTitular()), limiteEducacao);
        efetiva = efetiva.add(somarEducacaoPorPessoa(request.getDespesasInstrucaoDependentes(), dependentes, limiteEducacao));
        efetiva = efetiva.add(somarEducacaoPorPessoa(request.getDespesasInstrucaoAlimentandos(), alimentandos, limiteEducacao));
        return efetiva.setScale(2, RM);
    }

    /** INSS patronal empregador doméstico efetivo (crédito ou dedução conforme flag do request). */
    public BigDecimal calcularInssDomesticoCredito(
            SimuladorIrpfRequest request,
            IrParametrosAnuais parametros) {
        return IrDoacoesDeducaoCalculator.calcularInssDomesticoEfetivo(
                request.getPrevidenciaEmpregadoDomestico(),
                request.getAnoCalendario(),
                parametros);
    }

    DeducoesCompleto calcularDeducoesCompleto(
            SimuladorIrpfRequest request,
            IrParametrosAnuais parametros,
            BigDecimal rendimentos) {

        int dependentes = request.getQtdDependentes() != null ? request.getQtdDependentes() : 0;

        BigDecimal valorDependente = nvl(parametros.getDeducaoDependente());
        BigDecimal limiteEducacao = nvl(parametros.getLimiteInstrucao());

        BigDecimal educacaoEfetiva = request.getDespesasInstrucaoDeclarada() != null
                ? nvl(request.getDespesasInstrucaoDeclarada()).setScale(2, RM)
                : calcularEducacaoEfetiva(request, limiteEducacao);

        BigDecimal deducaoDependentes = request.getDeducaoDependentesDeclarada() != null
                ? nvl(request.getDeducaoDependentesDeclarada())
                : valorDependente.multiply(BigDecimal.valueOf(dependentes));

        BigDecimal pgblEfetivo = nvl(request.getPrevidenciaPrivada())
                .min(rendimentos.multiply(PGBL_LIMITE_PCT));

        BigDecimal inssDomesticoEfetivo = IrDoacoesDeducaoCalculator.calcularInssDomesticoEfetivo(
                request.getPrevidenciaEmpregadoDomestico(),
                request.getAnoCalendario(),
                parametros);

        BigDecimal total = nvl(request.getDespesasMedicas())
                .add(educacaoEfetiva)
                .add(deducaoDependentes)
                .add(nvl(request.getPrevidenciaOficial()))
                .add(pgblEfetivo)
                .add(nvl(request.getPensaoAlimenticia()));

        if (!Boolean.TRUE.equals(request.getInssDomesticoComoCreditoImposto())) {
            total = total.add(inssDomesticoEfetivo);
        }

        return new DeducoesCompleto(total.setScale(2, RM), inssDomesticoEfetivo.setScale(2, RM));
    }

    /** Expõe PGBL efetivo (teto 12% dos rendimentos) para exibição no Excel. */
    public BigDecimal calcularPgblEfetivo(BigDecimal previdenciaPrivada, BigDecimal rendimentos) {
        return nvl(previdenciaPrivada).min(rendimentos.multiply(PGBL_LIMITE_PCT)).setScale(2, RM);
    }

    BigDecimal calcularDescontoSimplificado(BigDecimal rendimentos, IrParametrosAnuais parametros) {
        BigDecimal teorico = rendimentos.multiply(DESCONTO_SIMPLIFICADO_PCT);
        BigDecimal limite = nvl(parametros.getLimiteDescontoSimplificado());
        return teorico.min(limite).setScale(2, RM);
    }

    BigDecimal calcularImpostoPagoTotal(SimuladorIrpfRequest request) {
        return nvl(request.getImpostoRetidoFonteTitular())
                .add(nvl(request.getImpostoRetidoFonteDependentes()))
                .add(nvl(request.getCarneLeaoTitular()))
                .add(nvl(request.getCarneLeaoDependentes()))
                .add(nvl(request.getImpostoComplementar()))
                .add(nvl(request.getImpostoPagoExterior()))
                .add(nvl(request.getImpostoRetidoFonteLei11033()))
                .add(nvl(request.getImpostoRetidoRRA()))
                .setScale(2, RM);
    }

    private ModeloTributacaoResultDTO calcularModelo(
            String tipo,
            BigDecimal rendimentosProgressivos,
            BigDecimal totalDeducoes,
            BigDecimal descontoSimplificado,
            List<IrTabelaTributacao> faixas,
            IrParametrosAnuais parametros,
            DoacoesBrutasDTO doacoesBrutas,
            BigDecimal impostoDevidoRRA,
            BigDecimal impostoPagoTotal,
            SimuladorIrpfRequest request,
            BigDecimal creditoInssDomestico) {

        // Passo 1: progressiva sem deduções especiais para obter impostoAposReducao
        ResultadoCalculoIrpfDTO calcSemEspeciais = calculoProgressivoService.calcular(
                faixas, rendimentosProgressivos, totalDeducoes, BigDecimal.ZERO, parametros);

        BigDecimal impostoDevidoInicial = calcSemEspeciais.getImpostoProgressivo();
        BigDecimal impostoAposReducao = impostoDevidoInicial
                .subtract(calcSemEspeciais.getReducaoAnual())
                .max(BigDecimal.ZERO);

        // Passo 2: aplicar travas 6%/1% sobre doações
        DoacoesEfetivasDTO doacoesEfetivas = doacoesCalculator.calcularEfetivas(impostoAposReducao, doacoesBrutas);
        BigDecimal deducoesEspeciais = doacoesEfetivas.totalEfetivo();

        BigDecimal impostoDevidoFinal = impostoAposReducao
                .subtract(deducoesEspeciais)
                .max(BigDecimal.ZERO)
                .setScale(2, RM_IMPOSTO);

        BigDecimal creditoInss = BigDecimal.ZERO;
        BigDecimal impostoDevidoII = null;
        if ("COMPLETO".equals(tipo) && Boolean.TRUE.equals(request.getInssDomesticoComoCreditoImposto())) {
            creditoInss = nvl(creditoInssDomestico);
            if (creditoInss.compareTo(BigDecimal.ZERO) > 0) {
                impostoDevidoII = impostoDevidoFinal.subtract(creditoInss).max(BigDecimal.ZERO).setScale(2, RM_IMPOSTO);
            }
        }

        BigDecimal impostoParaTotal = impostoDevidoII != null ? impostoDevidoII : impostoDevidoFinal;

        BigDecimal totalImpostoDevido = impostoParaTotal.add(impostoDevidoRRA).setScale(2, RM);

        BigDecimal aliquotaTotal = BigDecimal.ZERO;
        if (rendimentosProgressivos.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal impostoParaAliquota = "COMPLETO".equals(tipo) && Boolean.TRUE.equals(request.getInssDomesticoComoCreditoImposto())
                    ? impostoParaTotal
                    : totalImpostoDevido;
            aliquotaTotal = impostoParaAliquota
                    .divide(rendimentosProgressivos, SCALE, RM)
                    .multiply(new BigDecimal("100"))
                    .setScale(2, RM_IMPOSTO);
        }

        BigDecimal saldo = impostoPagoTotal.subtract(totalImpostoDevido).setScale(2, RM);
        BigDecimal restituir = BigDecimal.ZERO;
        BigDecimal aPagar = BigDecimal.ZERO;
        if (saldo.compareTo(BigDecimal.ZERO) > 0) {
            restituir = saldo;
        } else if (saldo.compareTo(BigDecimal.ZERO) < 0) {
            aPagar = saldo.abs();
        }

        ResumoDeclaracaoDTO resumo = montarResumo(
                calcSemEspeciais.getBaseCalculo(),
                impostoDevidoInicial,
                doacoesEfetivas.getDeducaoIncentivoEfetiva(),
                impostoDevidoFinal,
                impostoDevidoRRA,
                aliquotaTotal,
                totalImpostoDevido,
                impostoPagoTotal,
                restituir,
                aPagar,
                request);

        return ModeloTributacaoResultDTO.builder()
                .tipo(tipo)
                .rendimentosTributaveis(rendimentosProgressivos.setScale(2, RM))
                .totalDeducoes(totalDeducoes.setScale(2, RM))
                .descontoSimplificado(descontoSimplificado)
                .baseCalculo(calcSemEspeciais.getBaseCalculo())
                .faixas(calcSemEspeciais.getFaixas())
                .impostoDevidoInicial(impostoDevidoInicial)
                .reducaoAnual(calcSemEspeciais.getReducaoAnual())
                .impostoDevidoAposReducao(impostoAposReducao.setScale(2, RM))
                .deducoesEspeciais(deducoesEspeciais.setScale(2, RM))
                .impostoDevidoFinal(impostoDevidoFinal)
                .impostoDevidoII(impostoDevidoII)
                .creditoInssDomestico(creditoInss)
                .aliquotaEfetiva(aliquotaTotal)
                .impostoPagoTotal(impostoPagoTotal)
                .saldo(saldo)
                .impostoRestituir(restituir)
                .saldoImpostoPagar(aPagar)
                .resumo(resumo)
                .build();
    }

    private BigDecimal somarEducacaoPorPessoa(List<BigDecimal> gastos, int qtd, BigDecimal limite) {
        BigDecimal soma = BigDecimal.ZERO;
        for (int i = 0; i < qtd; i++) {
            BigDecimal gasto = (gastos != null && i < gastos.size()) ? nvl(gastos.get(i)) : BigDecimal.ZERO;
            soma = soma.add(capEducacao(gasto, limite));
        }
        return soma;
    }

    private BigDecimal capEducacao(BigDecimal gasto, BigDecimal limite) {
        return gasto.min(limite);
    }

    private ResumoDeclaracaoDTO montarResumo(
            BigDecimal baseCalculo,
            BigDecimal impostoDevido,
            BigDecimal deducaoIncentivo,
            BigDecimal impostoDevidoI,
            BigDecimal impostoDevidoRRA,
            BigDecimal aliquotaEfetiva,
            BigDecimal totalImpostoDevido,
            BigDecimal impostoPagoTotal,
            BigDecimal restituir,
            BigDecimal aPagar,
            SimuladorIrpfRequest request) {

        return ResumoDeclaracaoDTO.builder()
                .baseCalculoImposto(baseCalculo)
                .impostoDevido(impostoDevido)
                .deducaoIncentivo(deducaoIncentivo)
                .impostoDevidoI(impostoDevidoI)
                .impostoDevidoRRA(impostoDevidoRRA)
                .aliquotaEfetiva(aliquotaEfetiva)
                .totalImpostoDevido(totalImpostoDevido)
                .impostoRetidoFonteTitular(nvl(request.getImpostoRetidoFonteTitular()))
                .impostoRetidoFonteDependentes(nvl(request.getImpostoRetidoFonteDependentes()))
                .carneLeaoTitular(nvl(request.getCarneLeaoTitular()))
                .carneLeaoDependentes(nvl(request.getCarneLeaoDependentes()))
                .impostoComplementar(nvl(request.getImpostoComplementar()))
                .impostoPagoExterior(nvl(request.getImpostoPagoExterior()))
                .impostoRetidoFonteLei11033(nvl(request.getImpostoRetidoFonteLei11033()))
                .impostoRetidoRRA(nvl(request.getImpostoRetidoRRA()))
                .totalImpostoPago(impostoPagoTotal)
                .impostoRestituir(restituir)
                .saldoImpostoPagar(aPagar)
                .build();
    }

    String escolherModelo(BigDecimal saldoCompleto, BigDecimal saldoSimplificado) {
        int cmp = saldoCompleto.compareTo(saldoSimplificado);
        if (cmp > 0) {
            return "COMPLETO";
        }
        if (cmp < 0) {
            return "SIMPLIFICADO";
        }
        return "COMPLETO";
    }

    private BigDecimal nvl(BigDecimal v) {
        return v != null ? v : BigDecimal.ZERO;
    }

    record DeducoesCompleto(BigDecimal total, BigDecimal inssDomesticoCredito) {}
}
