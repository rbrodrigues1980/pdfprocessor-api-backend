package br.com.verticelabs.pdfprocessor.application.empresas;

import br.com.verticelabs.pdfprocessor.domain.model.Empresa;
import br.com.verticelabs.pdfprocessor.domain.model.EmpresaPercentual;
import br.com.verticelabs.pdfprocessor.infrastructure.excel.ExcelResumoGeralHelper;
import br.com.verticelabs.pdfprocessor.infrastructure.excel.ExcelResumoGeralLinhaDTO;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EmpresaPercentualHelperTest {

    private final EmpresaPercentualHelper helper = new EmpresaPercentualHelper();

    @Test
    void isPercentualVigente_respeitaIntervaloEAtivo() {
        EmpresaPercentual p = EmpresaPercentual.builder()
                .id("p1")
                .descricao("Contrato")
                .percentual(new BigDecimal("12"))
                .vigenciaInicio(LocalDate.of(2024, 1, 1))
                .vigenciaFim(LocalDate.of(2025, 12, 31))
                .ativo(true)
                .build();

        assertTrue(helper.isPercentualVigente(p, LocalDate.of(2024, 6, 1)));
        assertFalse(helper.isPercentualVigente(p, LocalDate.of(2026, 1, 1)));
        EmpresaPercentual inativo = EmpresaPercentual.builder()
                .id(p.getId())
                .descricao(p.getDescricao())
                .percentual(p.getPercentual())
                .vigenciaInicio(p.getVigenciaInicio())
                .vigenciaFim(p.getVigenciaFim())
                .ativo(false)
                .build();
        assertFalse(helper.isPercentualVigente(inativo, LocalDate.of(2024, 6, 1)));
    }

    @Test
    void findPercentual_retornaItemPorId() {
        Empresa empresa = Empresa.builder()
                .percentuais(List.of(EmpresaPercentual.builder().id("abc").percentual(new BigDecimal("15")).build()))
                .build();

        assertTrue(helper.findPercentual(empresa, "abc").isPresent());
        assertEquals(new BigDecimal("15"), helper.findPercentual(empresa, "abc").get().getPercentual());
    }
}
