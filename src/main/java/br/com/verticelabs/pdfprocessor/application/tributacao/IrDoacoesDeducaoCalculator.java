package br.com.verticelabs.pdfprocessor.application.tributacao;

import br.com.verticelabs.pdfprocessor.application.tributacao.dto.DoacoesBrutasDTO;
import br.com.verticelabs.pdfprocessor.application.tributacao.dto.DoacoesEfetivasDTO;
import br.com.verticelabs.pdfprocessor.domain.model.IrParametrosAnuais;
import br.com.verticelabs.pdfprocessor.infrastructure.config.IrTributacaoParametrosUtil;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Aplica travas legais sobre doações (6% global incentivo, 1% PRONON/PRONAS).
 */
@Component
public class IrDoacoesDeducaoCalculator {

    private static final BigDecimal LIMITE_INCENTIVO_PCT = new BigDecimal("0.06");
    private static final BigDecimal LIMITE_PRONON_PCT = new BigDecimal("0.01");
    private static final BigDecimal LIMITE_PRONAS_PCT = new BigDecimal("0.01");
    private static final RoundingMode RM = RoundingMode.HALF_UP;

    public DoacoesEfetivasDTO calcularEfetivas(BigDecimal impostoAposReducao, DoacoesBrutasDTO brutas) {
        if (brutas == null) {
            return DoacoesEfetivasDTO.builder().build();
        }

        BigDecimal base = nvl(impostoAposReducao);
        BigDecimal tetoIncentivo = base.multiply(LIMITE_INCENTIVO_PCT).setScale(2, RM);
        BigDecimal tetoPronon = base.multiply(LIMITE_PRONON_PCT).setScale(2, RM);
        BigDecimal tetoPronas = base.multiply(LIMITE_PRONAS_PCT).setScale(2, RM);

        return DoacoesEfetivasDTO.builder()
                .deducaoIncentivoEfetiva(nvl(brutas.getDeducaoIncentivoBruta()).min(tetoIncentivo).setScale(2, RM))
                .dedPrononEfetiva(nvl(brutas.getDedPrononBruta()).min(tetoPronon).setScale(2, RM))
                .dedPronasEfetiva(nvl(brutas.getDedPronasBruta()).min(tetoPronas).setScale(2, RM))
                .build();
    }

    public DoacoesBrutasDTO fromRequestBrutas(
            BigDecimal deducaoIncentivo,
            BigDecimal dedPronon,
            BigDecimal dedPronas) {
        return DoacoesBrutasDTO.builder()
                .deducaoIncentivoBruta(nvl(deducaoIncentivo))
                .dedPrononBruta(nvl(dedPronon))
                .dedPronasBruta(nvl(dedPronas))
                .build();
    }

    /**
     * INSS patronal empregador doméstico — dedução histórica AC ≤ 2018 com teto anual.
     */
    public static BigDecimal calcularInssDomesticoEfetivo(
            BigDecimal declarado,
            Integer anoCalendario,
            IrParametrosAnuais params) {
        if (anoCalendario == null || anoCalendario > 2018) {
            return BigDecimal.ZERO;
        }
        BigDecimal limite = params != null && params.getLimiteInssDomestico() != null
                ? params.getLimiteInssDomestico()
                : IrTributacaoParametrosUtil.limiteInssDomestico(anoCalendario);
        return nvl(declarado).min(limite).setScale(2, RM);
    }

    private static BigDecimal nvl(BigDecimal v) {
        return v != null ? v : BigDecimal.ZERO;
    }
}
