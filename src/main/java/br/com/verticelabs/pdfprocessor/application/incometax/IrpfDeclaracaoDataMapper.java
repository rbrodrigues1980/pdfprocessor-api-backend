package br.com.verticelabs.pdfprocessor.application.incometax;

import br.com.verticelabs.pdfprocessor.domain.model.IrpfDeclaracaoData;
import br.com.verticelabs.pdfprocessor.domain.model.IrpfDeclaracaoData.DoacaoEfetuadaIrpf;
import br.com.verticelabs.pdfprocessor.domain.model.IrpfDeclaracaoData.FontePagadoraIrpf;
import br.com.verticelabs.pdfprocessor.domain.model.IrpfDeclaracaoData.PagamentoEfetuadoIrpf;
import br.com.verticelabs.pdfprocessor.domain.model.IrpfDeclaracaoData.PessoaRelacionada;
import br.com.verticelabs.pdfprocessor.domain.service.IncomeTaxDeclarationService.IncomeTaxInfo;
import br.com.verticelabs.pdfprocessor.domain.service.IncomeTaxDeclarationService.IncomeTaxInfo.DependenteInfo;
import br.com.verticelabs.pdfprocessor.domain.service.IncomeTaxDeclarationService.IncomeTaxInfo.DoacaoEfetuada;
import br.com.verticelabs.pdfprocessor.domain.service.IncomeTaxDeclarationService.IncomeTaxInfo.FontePagadora;
import br.com.verticelabs.pdfprocessor.domain.service.IncomeTaxDeclarationService.IncomeTaxInfo.PagamentoEfetuado;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/**
 * Converte {@link IncomeTaxInfo} extraído pelo iText/Gemini para {@link IrpfDeclaracaoData}.
 */
@Component
public class IrpfDeclaracaoDataMapper {

    public IrpfDeclaracaoData fromIncomeTaxInfo(IncomeTaxInfo info) {
        if (info == null) {
            return IrpfDeclaracaoData.builder().build();
        }

        List<PessoaRelacionada> dependentes = new ArrayList<>();
        if (info.getDependentes() != null) {
            for (DependenteInfo d : info.getDependentes()) {
                dependentes.add(PessoaRelacionada.builder()
                        .codigo(d.getCodigo())
                        .nome(d.getNome())
                        .dataNascimento(d.getDataNascimento())
                        .cpf(d.getCpf())
                        .build());
            }
        }

        List<PessoaRelacionada> alimentandos = new ArrayList<>();
        if (info.getAlimentandos() != null) {
            for (DependenteInfo a : info.getAlimentandos()) {
                alimentandos.add(PessoaRelacionada.builder()
                        .codigo(a.getCodigo())
                        .nome(a.getNome())
                        .dataNascimento(a.getDataNascimento())
                        .cpf(a.getCpf())
                        .build());
            }
        }

        List<FontePagadoraIrpf> fontes = new ArrayList<>();
        if (info.getFontesPagadoras() != null) {
            for (FontePagadora fp : info.getFontesPagadoras()) {
                fontes.add(FontePagadoraIrpf.builder()
                        .nome(fp.getNome())
                        .cnpjCpf(fp.getCnpjCpf())
                        .rendRecebidosPJ(fp.getRendimentoTributavel())
                        .contrPrevOficial(fp.getContribuicaoPrevOficial())
                        .impostoRetidoFonte(fp.getImpostoRetidoFonte())
                        .decimoTerceiro(fp.getDecimoTerceiro())
                        .irrfDecimoTerceiro(fp.getIrrfDecimoTerceiro())
                        .build());
            }
        }

        List<PagamentoEfetuadoIrpf> pagamentos = new ArrayList<>();
        if (info.getPagamentosEfetuados() != null) {
            for (PagamentoEfetuado p : info.getPagamentosEfetuados()) {
                pagamentos.add(PagamentoEfetuadoIrpf.builder()
                        .codigo(p.getCodigo())
                        .nomeBeneficiario(p.getNomeBeneficiario())
                        .cpfCnpj(p.getCpfCnpj())
                        .valorPago(p.getValorPago())
                        .parcNaoDedutivel(p.getParcNaoDedutivel())
                        .nitEmpregadoDomestico(p.getNitEmpregadoDomestico())
                        .build());
            }
        }

        List<DoacaoEfetuadaIrpf> doacoes = new ArrayList<>();
        if (info.getDoacoesEfetuadas() != null) {
            for (DoacaoEfetuada d : info.getDoacoesEfetuadas()) {
                doacoes.add(DoacaoEfetuadaIrpf.builder()
                        .codigo(d.getCodigo())
                        .nomeBeneficiario(d.getNomeBeneficiario())
                        .cpfCnpj(d.getCpfCnpj())
                        .valorDoado(d.getValorDoado())
                        .build());
            }
        }

        return IrpfDeclaracaoData.builder()
                .nomeTitular(info.getNome())
                .cpfTitular(info.getCpf())
                .exercicio(info.getExercicio())
                .anoCalendario(info.getAnoCalendario())
                .controle(info.getControle())
                .dataHoraEntrega(info.getDataEntrega())
                .tipoDeclaracao(info.getTipoDeclaracao())
                .tipoTributacao(info.getTipoTributacao())
                .dependentes(dependentes)
                .totalDeducaoDependentes(resolverDeducaoDependentes(info))
                .alimentandos(alimentandos)
                .rendimentosFontesTitular(fontes)
                .descontoSimplificado(info.getDescontoSimplificado())
                .contribuicaoPrevidenciaOficialResumo(info.getDeducoesContribPrevOficial())
                .contribuicaoPrevidenciaOficialRra(info.getDeducoesContribPrevRRA())
                .contribuicaoPrevidenciaSocial(IrpfPrevidenciaOficialResolver.resolver(
                        fontes, info.getDeducoesContribPrevOficial(), null))
                .contribuicaoPrevidenciaPrivada(info.getDeducoesContribPrevCompl())
                // Prioridade: total página 1 (iText) > linha Dependentes do RESUMO (Gemini/iText)
                .deducaoDependentes(resolverDeducaoDependentes(info))
                .despesasInstrucao(info.getDeducoesInstrucao())
                .despesasMedicas(info.getDeducoesMedicas())
                .pensaoAlimenticiaJudicial(info.getDeducoesPensaoJudicial())
                .pensaoAlimenticiaJudicialRra(info.getDeducoesPensaoRRA())
                .pensaoAlimenticiaEscrituraPublica(info.getDeducoesPensaoEscritura())
                .livroCaixa(info.getDeducoesLivroCaixa())
                .contribuicaoPatronalPrevidenciaSocial(info.getContribuicaoPrevEmpregadorDomestico())
                .rendimentosTributaveisTotal(info.getRendimentosTributaveis())
                .deducoesTotal(info.getDeducoes())
                .baseCalculoImposto(resolverBaseCalculo(info))
                .impostoDevido(info.getImpostoDevido())
                .deducaoIncentivo(info.getDeducaoIncentivo())
                .impostoDevidoI(info.getImpostoDevidoI())
                .impostoDevidoII(info.getImpostoDevidoII())
                .impostoSobreRRA(info.getImpostoDevidoRRA())
                .totalImpostoDevido(info.getTotalImpostoDevido())
                .impostoRetidoFonteTitular(info.getImpostoRetidoFonteTitular())
                .impostoRetidoDependentes(info.getImpostoRetidoFonteDependentes())
                .carneLeaoTitular(info.getCarneLeaoTitular())
                .carneLeaoDependentes(info.getCarneLeaoDependentes())
                .impostoComplementar(info.getImpostoComplementar())
                .impostoPagoExterior(info.getImpostoPagoExterior())
                .impostoRetidoFonteLei11033(info.getImpostoRetidoFonteLei11033())
                .impostoRetidoRRA(info.getImpostoRetidoRRA())
                .impostoPagoTotal(info.getImpostoPagoTotal())
                .impostoRestituir(info.getImpostoRestituir())
                .saldoImpostoPagar(info.getSaldoImpostoPagar())
                .aliquotaEfetiva(info.getAliquotaEfetiva())
                .rendimentosIsentos(info.getRendimentosIsentos())
                .rendimentosTributacaoExclusiva(info.getRendimentosTributacaoExclusiva())
                .pagamentosEfetuados(pagamentos)
                .doacoesEfetuadas(doacoes)
                .rendimentosTributaveisTitularPJ(info.getRendimentosTributaveisTitularPJ())
                .rendimentosTributaveisDependentesPJ(info.getRendimentosTributaveisDependentesPJ())
                .rendimentosTributaveisTitularPF(info.getRendimentosTributaveisTitularPF())
                .rendimentosTributaveisDependentesPF(info.getRendimentosTributaveisDependentesPF())
                .resultadoAtividadeRural(info.getResultadoAtividadeRural())
                .rendimentosAcumuladosTitular(info.getRendimentosAcumuladosTitular())
                .rendimentosAcumuladosDependentes(info.getRendimentosAcumuladosDependentes())
                .impostoPagoGanhosCapital(info.getImpostoPagoGanhosCapital())
                .impostoDevidoGanhosCapital(info.getImpostoDevidoGanhosCapital())
                .impostoDevidoGanhosCapitalMoedaEstrangeira(info.getImpostoDevidoGanhosCapitalMoedaEstrangeira())
                .impostoPagoGanhosCapitalMoedaEstrangeira(info.getImpostoPagoGanhosCapitalMoedaEstrangeira())
                .impostoPagoRendaVariavel(info.getImpostoPagoRendaVariavel())
                .impostoDevidoGanhosLiquidosRendaVariavel(info.getImpostoDevidoGanhosLiquidosRendaVariavel())
                .impostoAPagarGanhosCapitalMoedaEstrangeira(info.getImpostoAPagarGanhosCapitalMoedaEstrangeira())
                .rendimentosTributaveisExigSuspensa(info.getRendimentosTributaveisExigSuspensa())
                .depositosJudiciais(info.getDepositosJudiciais())
                .impostoDiferidoGanhosCapital(info.getImpostoDiferidoGanhosCapital())
                .doacoesPartidosPoliticos(info.getDoacoesPartidosPoliticos())
                .build();
    }

    /**
     * Base de cálculo do imposto no modelo Completo: rendimentos − total de deduções.
     * No Simplificado: rendimentos − desconto simplificado.
     * Não usa o valor solto extraído do RESUMO (layout de duas colunas pega o total de rendimentos
     * ou omite dependentes).
     */
    static BigDecimal resolverBaseCalculo(IncomeTaxInfo info) {
        if (info == null) {
            return null;
        }
        BigDecimal rendimentos = info.getRendimentosTributaveis();
        if (rendimentos == null) {
            return info.getBaseCalculoImposto();
        }
        if ("SIMPLIFICADO".equalsIgnoreCase(info.getTipoTributacao())
                && info.getDescontoSimplificado() != null) {
            return baseCalculoRendimentosMenosDeducoes(rendimentos, info.getDescontoSimplificado());
        }
        BigDecimal total = totalDeducoesParaBase(info);
        if (total != null && total.compareTo(BigDecimal.ZERO) >= 0
                && total.compareTo(rendimentos) < 0) {
            return baseCalculoRendimentosMenosDeducoes(rendimentos, total);
        }
        return info.getBaseCalculoImposto();
    }

    /**
     * Soma as linhas de dedução que a planilha espelha, usando o total de dependentes da página 1
     * quando o RESUMO veio zerado. Se o TOTAL extraído for maior (linha que o regex perdeu), usa o TOTAL.
     */
    static BigDecimal totalDeducoesParaBase(IncomeTaxInfo info) {
        BigDecimal somaLinhas = nvl(info.getDeducoesContribPrevOficial())
                .add(nvl(info.getDeducoesContribPrevRRA()))
                .add(nvl(info.getDeducoesContribPrevCompl()))
                .add(nvl(resolverDeducaoDependentes(info)))
                .add(nvl(info.getDeducoesInstrucao()))
                .add(nvl(info.getDeducoesMedicas()))
                .add(nvl(info.getDeducoesPensaoJudicial()))
                .add(nvl(info.getDeducoesPensaoEscritura()))
                .add(nvl(info.getDeducoesPensaoRRA()))
                .add(nvl(info.getDeducoesLivroCaixa()));
        BigDecimal extraido = info.getDeducoes();
        if (extraido != null
                && extraido.compareTo(somaLinhas) > 0
                && info.getRendimentosTributaveis() != null
                && extraido.compareTo(info.getRendimentosTributaveis()) < 0) {
            return extraido;
        }
        return somaLinhas;
    }

    public static BigDecimal baseCalculoRendimentosMenosDeducoes(
            BigDecimal rendimentos, BigDecimal totalDeducoes) {
        if (rendimentos == null) {
            return null;
        }
        BigDecimal total = nvl(totalDeducoes);
        return rendimentos.subtract(total).max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
    }

    private static BigDecimal nvl(BigDecimal value) {
        return value != null ? value : BigDecimal.ZERO;
    }

    /**
     * Resolve a dedução de dependentes: total da página 1 (iText/Gemini) tem prioridade;
     * fallback para a linha "Dependentes" do RESUMO. Zero não prevalece sobre valor positivo.
     */
    static BigDecimal resolverDeducaoDependentes(IncomeTaxInfo info) {
        if (info == null) {
            return null;
        }
        if (isPositive(info.getTotalDeducaoDependentes())) {
            return info.getTotalDeducaoDependentes();
        }
        if (isPositive(info.getDeducoesDependentes())) {
            return info.getDeducoesDependentes();
        }
        return firstNonNull(info.getTotalDeducaoDependentes(), info.getDeducoesDependentes());
    }

    private static boolean isPositive(BigDecimal value) {
        return value != null && value.compareTo(BigDecimal.ZERO) > 0;
    }

    private static BigDecimal firstNonNull(BigDecimal a, BigDecimal b) {
        return a != null ? a : b;
    }
}
