package br.com.verticelabs.pdfprocessor.infrastructure.excel;

import br.com.verticelabs.pdfprocessor.application.tributacao.IrPagamentosDeducaoAggregator;
import br.com.verticelabs.pdfprocessor.domain.model.IrpfDeclaracaoData;
import br.com.verticelabs.pdfprocessor.domain.model.IrpfDeclaracaoData.PagamentoEfetuadoIrpf;
import br.com.verticelabs.pdfprocessor.domain.tributacao.IrCodigoDeducao;
import br.com.verticelabs.pdfprocessor.interfaces.consolidation.dto.ConsolidatedResponse;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Set;

/**
 * Prev. complementar da Simulação 2 (planilha + pagamentos cód. 36/37/38 externos na DIRPF).
 * <p>
 * Base = contracheques/ficha extraídos; não soma de novo a linha Fapi patronal da declaração.
 * CNPJs patronais já refletidos na planilha são ignorados nos extras:
 * {@code 00.436.923/0001-90} (FUNCEF), {@code 00.360.305/0001-04} (CAIXA) e
 * {@code 65.471.914/0001-86} (FUND. SABESP DE SEGURIDADE SOCIAL).
 * <p>
 * Ficha SABESPREV: usa o TOTAL líquido do rodapé (CONTRIBUIÇÃO − DEVOLUÇÃO da lista fechada),
 * não a soma bruta de todas as rubricas da matriz.
 */
@Slf4j
public final class PrevComplPlanilhaHelper {

    private static final RoundingMode RM = RoundingMode.HALF_UP;

    /** FUNCEF — prev. complementar fechada patronal. */
    public static final String CNPJ_FUNCEF = "00436923000190";
    /** CAIXA ECONÔMICA FEDERAL — entidade patronal. */
    public static final String CNPJ_CAIXA_PATRONAL = "00360305000104";
    /** FUND. SABESP DE SEGURIDADE SOCIAL (SABESPREV) — previdência patronal SABESP. */
    public static final String CNPJ_SABESPREV = "65471914000186";

    private static final Set<String> CNPJS_IGNORADOS = Set.of(
            CNPJ_FUNCEF, CNPJ_CAIXA_PATRONAL, CNPJ_SABESPREV);

    private static final IrPagamentosDeducaoAggregator PAGAMENTOS_AGGREGATOR = new IrPagamentosDeducaoAggregator();

    private PrevComplPlanilhaHelper() {
    }

    public static BigDecimal calcularPrevComplSimulacao(
            ConsolidatedResponse consolidatedResponse,
            String ano,
            IrpfDeclaracaoData declaracao) {

        BigDecimal contracheques = calcularBaseContracheques(consolidatedResponse, ano);
        BigDecimal extras = somarPagamentosPrevidenciaExternos(declaracao);
        BigDecimal total = contracheques.add(extras).setScale(2, RM);

        if (extras.compareTo(BigDecimal.ZERO) > 0) {
            log.info(
                    "Prev compl simulação ano {}: contracheques {} + extras declaração {} = {}",
                    ano, contracheques, extras, total);
        }

        return total;
    }

    /**
     * Base de prev. complementar dos contracheques/ficha no ano.
     * SABESPREV (ficha): TOTAL líquido do rodapé; demais origens: soma das rubricas.
     */
    static BigDecimal calcularBaseContracheques(ConsolidatedResponse consolidatedResponse, String ano) {
        if (consolidatedResponse == null) {
            return BigDecimal.ZERO.setScale(2, RM);
        }
        if (isAnoFichaSabesprev(consolidatedResponse, ano)) {
            BigDecimal liquido = SabesprevFichaTotaisHelper.calcular(
                            consolidatedResponse.getRubricas(), ano)
                    .totalLiquido()
                    .totalAno();
            log.debug("Prev compl SABESPREV ano {}: TOTAL líquido rodapé {}", ano, liquido);
            return liquido;
        }
        return ConsolidationAnoTotalsHelper.calcularTotalContracheques(consolidatedResponse, ano)
                .setScale(2, RM);
    }

    static boolean isAnoFichaSabesprev(ConsolidatedResponse consolidatedResponse, String ano) {
        if ("SABESPREV".equalsIgnoreCase(consolidatedResponse.getOrigem())) {
            return true;
        }
        return SabesprevFichaTotaisHelper.isAnoFichaFinanceira(
                consolidatedResponse.getRubricas(), ano);
    }

    /**
     * Soma pagamentos cód. 36/37/38 cujo CNPJ não é patronal (FUNCEF/CAIXA/SABESPREV).
     */
    public static BigDecimal somarPagamentosPrevidenciaExternos(IrpfDeclaracaoData data) {
        if (data == null || data.getPagamentosEfetuados() == null || data.getPagamentosEfetuados().isEmpty()) {
            return BigDecimal.ZERO.setScale(2, RM);
        }

        BigDecimal total = BigDecimal.ZERO;
        for (PagamentoEfetuadoIrpf pagamento : data.getPagamentosEfetuados()) {
            if (!isCodigoPrevidenciaComplementar(pagamento.getCodigo())) {
                continue;
            }
            if (isCnpjIgnorado(pagamento.getCpfCnpj())) {
                log.debug(
                        "Pagamento cód. {} CNPJ {} ignorado (patronal)",
                        pagamento.getCodigo(),
                        pagamento.getCpfCnpj());
                continue;
            }
            BigDecimal valor = PAGAMENTOS_AGGREGATOR.valorDedutivel(pagamento);
            if (valor.compareTo(BigDecimal.ZERO) > 0) {
                total = total.add(valor);
            }
        }

        return total.setScale(2, RM);
    }

    static boolean isCodigoPrevidenciaComplementar(String codigo) {
        if (codigo == null || codigo.isBlank()) {
            return false;
        }
        String digits = codigo.replaceAll("\\D", "");
        if (digits.isEmpty()) {
            return false;
        }
        try {
            String normalizado = String.format("%02d", Integer.parseInt(digits));
            return "36".equals(normalizado) || "37".equals(normalizado) || "38".equals(normalizado);
        } catch (NumberFormatException e) {
            return false;
        }
    }

    static boolean isCnpjIgnorado(String cpfCnpj) {
        String digits = normalizarDocumento(cpfCnpj);
        return digits.length() == 14 && CNPJS_IGNORADOS.contains(digits);
    }

    static String normalizarDocumento(String documento) {
        if (documento == null) {
            return "";
        }
        return documento.replaceAll("\\D", "");
    }
}
