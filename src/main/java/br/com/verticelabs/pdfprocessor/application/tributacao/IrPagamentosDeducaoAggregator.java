package br.com.verticelabs.pdfprocessor.application.tributacao;

import br.com.verticelabs.pdfprocessor.application.tributacao.dto.DeducoesPagamentosDTO;
import br.com.verticelabs.pdfprocessor.application.tributacao.dto.DoacoesBrutasDTO;
import br.com.verticelabs.pdfprocessor.domain.model.IrpfDeclaracaoData;
import br.com.verticelabs.pdfprocessor.domain.model.IrpfDeclaracaoData.DoacaoEfetuadaIrpf;
import br.com.verticelabs.pdfprocessor.domain.model.IrpfDeclaracaoData.PagamentoEfetuadoIrpf;
import br.com.verticelabs.pdfprocessor.domain.model.IrpfDeclaracaoData.PessoaRelacionada;
import br.com.verticelabs.pdfprocessor.domain.tributacao.IrCodigoDeducao;
import br.com.verticelabs.pdfprocessor.domain.tributacao.IrTipoRegraDeducao;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Agrega {@code pagamentosEfetuados[]} e {@code doacoesEfetuadas[]} por código oficial SERPRO.
 */
@Slf4j
@Component
public class IrPagamentosDeducaoAggregator {

    public DeducoesPagamentosDTO aggregate(List<PagamentoEfetuadoIrpf> pagamentos, IrpfDeclaracaoData declaracao) {
        if (pagamentos == null || pagamentos.isEmpty()) {
            return DeducoesPagamentosDTO.builder().fonteGranular(false).build();
        }

        BigDecimal despesasMedicas = BigDecimal.ZERO;
        BigDecimal pensaoAlimenticia = BigDecimal.ZERO;
        BigDecimal previdenciaPrivada = BigDecimal.ZERO;
        BigDecimal previdenciaEmpregadoDomestico = BigDecimal.ZERO;
        BigDecimal instrucaoTitular = BigDecimal.ZERO;

        Map<Integer, BigDecimal> instrucaoDependentes = new HashMap<>();
        Map<Integer, BigDecimal> instrucaoAlimentandos = new HashMap<>();

        List<PessoaRelacionada> dependentes = declaracao != null && declaracao.getDependentes() != null
                ? declaracao.getDependentes() : List.of();
        List<PessoaRelacionada> alimentandos = declaracao != null && declaracao.getAlimentandos() != null
                ? declaracao.getAlimentandos() : List.of();
        String cpfTitular = declaracao != null ? normalizarCpf(declaracao.getCpfTitular()) : "";

        for (PagamentoEfetuadoIrpf pagamento : pagamentos) {
            Optional<IrCodigoDeducao> codigoOpt = IrCodigoDeducao.fromCodigo(pagamento.getCodigo());
            if (codigoOpt.isEmpty()) {
                log.warn("Código de pagamento desconhecido ignorado: {}", pagamento.getCodigo());
                continue;
            }

            IrCodigoDeducao codigo = codigoOpt.get();
            if (codigo.getTipoRegra() == IrTipoRegraDeducao.DEDUCAO_DIRETA_IMPOSTO) {
                continue;
            }

            BigDecimal valor = valorDedutivel(pagamento);

            switch (codigo.getTipoRegra()) {
                case SEM_LIMITE -> {
                    if (codigo.isPensao()) {
                        pensaoAlimenticia = pensaoAlimenticia.add(valor);
                    } else {
                        despesasMedicas = despesasMedicas.add(valor);
                    }
                }
                case LIMITADO_POR_CPF -> {
                    int bucket = resolverBucketInstrucao(pagamento, cpfTitular, dependentes, alimentandos);
                    if (bucket >= 0) {
                        instrucaoDependentes.merge(bucket, valor, BigDecimal::add);
                    } else if (bucket <= -100) {
                        int idxAlimentando = -bucket - 100;
                        instrucaoAlimentandos.merge(idxAlimentando, valor, BigDecimal::add);
                    } else {
                        instrucaoTitular = instrucaoTitular.add(valor);
                    }
                }
                case LIMITADO_RENDA_12PCT -> previdenciaPrivada = previdenciaPrivada.add(valor);
                case TEMPORAL_DOMESTICO -> previdenciaEmpregadoDomestico = previdenciaEmpregadoDomestico.add(valor);
                default -> { }
            }
        }

        return DeducoesPagamentosDTO.builder()
                .despesasMedicas(despesasMedicas)
                .despesasInstrucaoTitular(instrucaoTitular)
                .despesasInstrucaoDependentes(listaPorIndices(dependentes.size(), instrucaoDependentes))
                .despesasInstrucaoAlimentandos(listaPorIndices(alimentandos.size(), instrucaoAlimentandos))
                .pensaoAlimenticia(pensaoAlimenticia)
                .previdenciaPrivada(previdenciaPrivada)
                .previdenciaEmpregadoDomestico(previdenciaEmpregadoDomestico)
                .fonteGranular(true)
                .build();
    }

    public DoacoesBrutasDTO aggregateDoacoes(List<DoacaoEfetuadaIrpf> doacoes) {
        if (doacoes == null || doacoes.isEmpty()) {
            return DoacoesBrutasDTO.builder().build();
        }

        BigDecimal incentivo = BigDecimal.ZERO;
        BigDecimal pronon = BigDecimal.ZERO;
        BigDecimal pronas = BigDecimal.ZERO;

        for (DoacaoEfetuadaIrpf doacao : doacoes) {
            Optional<IrCodigoDeducao> codigoOpt = IrCodigoDeducao.fromCodigo(doacao.getCodigo());
            if (codigoOpt.isEmpty()) {
                log.warn("Código de doação desconhecido ignorado: {}", doacao.getCodigo());
                continue;
            }

            IrCodigoDeducao codigo = codigoOpt.get();
            BigDecimal valor = nvl(doacao.getValorDoado());

            if (codigo.isIncentivoGlobal()) {
                incentivo = incentivo.add(valor);
            } else if (codigo == IrCodigoDeducao.C_44) {
                pronon = pronon.add(valor);
            } else if (codigo == IrCodigoDeducao.C_45) {
                pronas = pronas.add(valor);
            }
        }

        return DoacoesBrutasDTO.builder()
                .deducaoIncentivoBruta(incentivo)
                .dedPrononBruta(pronon)
                .dedPronasBruta(pronas)
                .build();
    }

    public BigDecimal valorDedutivel(PagamentoEfetuadoIrpf pagamento) {
        BigDecimal pago = nvl(pagamento.getValorPago());
        BigDecimal naoDedutivel = nvl(pagamento.getParcNaoDedutivel());
        return pago.subtract(naoDedutivel).max(BigDecimal.ZERO);
    }

    /**
     * @return -1 titular; >=0 índice dependente; -102+ índice alimentando codificado como -(100+idx)
     */
    int resolverBucketInstrucao(
            PagamentoEfetuadoIrpf pagamento,
            String cpfTitular,
            List<PessoaRelacionada> dependentes,
            List<PessoaRelacionada> alimentandos) {

        String cpfBeneficiario = normalizarCpf(pagamento.getCpfCnpj());
        if (cpfBeneficiario.length() != 11) {
            log.debug("CPF de beneficiário de instrução ausente/inválido ({}); alocando ao titular",
                    pagamento.getCpfCnpj());
            return -1;
        }

        if (!cpfTitular.isEmpty() && cpfBeneficiario.equals(cpfTitular)) {
            return -1;
        }

        for (int i = 0; i < dependentes.size(); i++) {
            if (cpfBeneficiario.equals(normalizarCpf(dependentes.get(i).getCpf()))) {
                return i;
            }
        }

        for (int i = 0; i < alimentandos.size(); i++) {
            if (cpfBeneficiario.equals(normalizarCpf(alimentandos.get(i).getCpf()))) {
                return -(100 + i);
            }
        }

        log.debug("CPF {} de instrução não identificado; alocando ao titular", cpfBeneficiario);
        return -1;
    }

    private List<BigDecimal> listaPorIndices(int qtd, Map<Integer, BigDecimal> valores) {
        List<BigDecimal> lista = new ArrayList<>();
        for (int i = 0; i < qtd; i++) {
            lista.add(valores.getOrDefault(i, BigDecimal.ZERO));
        }
        return lista;
    }

    static String normalizarCpf(String cpf) {
        if (cpf == null) {
            return "";
        }
        return cpf.replaceAll("\\D", "");
    }

    private static BigDecimal nvl(BigDecimal v) {
        return v != null ? v : BigDecimal.ZERO;
    }
}
