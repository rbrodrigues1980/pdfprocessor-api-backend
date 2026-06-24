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
 * Prev. complementar da Simulação 2 (contracheques + pagamentos cód. 36/37 externos na DIRPF).
 * <p>
 * CNPJs patronais já refletidos nos contracheques são ignorados:
 * {@code 00.436.923/0001-90} (FUNCEF) e {@code 00.360.305/0001-04} (CAIXA patronal).
 */
@Slf4j
public final class PrevComplPlanilhaHelper {

    private static final RoundingMode RM = RoundingMode.HALF_UP;

    /** FUNCEF — prev. complementar fechada patronal. */
    public static final String CNPJ_FUNCEF = "00436923000190";
    /** CAIXA ECONÔMICA FEDERAL — entidade patronal. */
    public static final String CNPJ_CAIXA_PATRONAL = "00360305000104";

    private static final Set<String> CNPJS_IGNORADOS = Set.of(CNPJ_FUNCEF, CNPJ_CAIXA_PATRONAL);

    private static final IrPagamentosDeducaoAggregator PAGAMENTOS_AGGREGATOR = new IrPagamentosDeducaoAggregator();

    private PrevComplPlanilhaHelper() {
    }

    public static BigDecimal calcularPrevComplSimulacao(
            ConsolidatedResponse consolidatedResponse,
            String ano,
            IrpfDeclaracaoData declaracao) {

        BigDecimal contracheques = ConsolidationAnoTotalsHelper.calcularTotalContracheques(consolidatedResponse, ano);
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
     * Soma pagamentos cód. 36/37 cujo CNPJ não é patronal (FUNCEF/CAIXA).
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
        return IrCodigoDeducao.C_36.getCodigo().equals(codigo)
                || IrCodigoDeducao.C_37.getCodigo().equals(codigo);
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
