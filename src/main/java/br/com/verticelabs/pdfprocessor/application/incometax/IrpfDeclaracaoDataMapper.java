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
                .baseCalculoImposto(info.getBaseCalculoImposto())
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
     * Resolve a dedução de dependentes: total da página 1 (iText) tem prioridade;
     * fallback para a linha "Dependentes" do RESUMO (Gemini/iText).
     * No fluxo Gemini escaneado, {@code totalDeducaoDependentes} é sempre null.
     */
    static BigDecimal resolverDeducaoDependentes(IncomeTaxInfo info) {
        if (info == null) {
            return null;
        }
        return firstNonNull(info.getTotalDeducaoDependentes(), info.getDeducoesDependentes());
    }

    private static BigDecimal firstNonNull(BigDecimal a, BigDecimal b) {
        return a != null ? a : b;
    }
}
