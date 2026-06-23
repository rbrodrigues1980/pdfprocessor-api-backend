package br.com.verticelabs.pdfprocessor.application.incometax;

import br.com.verticelabs.pdfprocessor.domain.model.IrpfDeclaracaoData;
import br.com.verticelabs.pdfprocessor.domain.model.IrpfDeclaracaoData.FontePagadoraIrpf;

import java.math.BigDecimal;
import java.util.List;

/**
 * Resolve previdência oficial para dedução IRPF a partir das fontes pagadoras PJ
 * (soma de {@code contrPrevOficial}) com fallback ao total do RESUMO.
 */
public final class IrpfPrevidenciaOficialResolver {

    private IrpfPrevidenciaOficialResolver() {
    }

    public static BigDecimal resolver(List<FontePagadoraIrpf> fontes, BigDecimal resumoPrevOficial) {
        return resolver(fontes, resumoPrevOficial, null);
    }

    public static BigDecimal resolver(
            List<FontePagadoraIrpf> fontes,
            BigDecimal resumoPrevOficial,
            BigDecimal resumoPrevRra) {

        BigDecimal somaFontes = somarContrPrevOficialFontes(fontes);
        if (somaFontes.compareTo(BigDecimal.ZERO) > 0) {
            return somaFontes.add(nvl(resumoPrevRra));
        }
        BigDecimal resumo = nvl(resumoPrevOficial).add(nvl(resumoPrevRra));
        return resumo.compareTo(BigDecimal.ZERO) > 0 ? resumo : BigDecimal.ZERO;
    }

    public static BigDecimal resolver(IrpfDeclaracaoData data) {
        if (data == null) {
            return BigDecimal.ZERO;
        }
        BigDecimal resumoOficial = data.getContribuicaoPrevidenciaOficialResumo();
        if (resumoOficial == null) {
            resumoOficial = data.getContribuicaoPrevidenciaSocial();
        }
        return resolver(data.getRendimentosFontesTitular(), resumoOficial, null);
    }

    private static BigDecimal somarContrPrevOficialFontes(List<FontePagadoraIrpf> fontes) {
        if (fontes == null || fontes.isEmpty()) {
            return BigDecimal.ZERO;
        }
        BigDecimal total = BigDecimal.ZERO;
        for (FontePagadoraIrpf fonte : fontes) {
            if (fonte != null && fonte.getContrPrevOficial() != null) {
                total = total.add(fonte.getContrPrevOficial());
            }
        }
        return total;
    }

    private static BigDecimal nvl(BigDecimal v) {
        return v != null ? v : BigDecimal.ZERO;
    }
}
