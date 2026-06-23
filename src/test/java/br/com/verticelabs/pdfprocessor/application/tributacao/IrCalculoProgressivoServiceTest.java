package br.com.verticelabs.pdfprocessor.application.tributacao;

import br.com.verticelabs.pdfprocessor.domain.model.IrParametrosAnuais;
import br.com.verticelabs.pdfprocessor.domain.model.IrTabelaTributacao;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IrCalculoProgressivoServiceTest {

    private IrCalculoProgressivoService service;
    private List<IrTabelaTributacao> faixas2020;

    @BeforeEach
    void setUp() {
        service = new IrCalculoProgressivoService();
        faixas2020 = criarFaixas2016a2022(2020);
    }

    @Test
    void elizabeth_exercicio2021_anoCalendario2020() {
        var resultado = service.calcular(
                faixas2020,
                new BigDecimal("86555.68"),
                new BigDecimal("4580.90"),
                BigDecimal.ZERO,
                null);

        assertEquals(new BigDecimal("81974.78"), resultado.getBaseCalculo());
        assertEquals(new BigDecimal("12110.74"), resultado.getImpostoDevido());
        assertEquals(new BigDecimal("13.99"), resultado.getAliquotaEfetiva());
    }

    @Test
    void isento_rendimentoAbaixoFaixa1() {
        var resultado = service.calcular(
                faixas2020,
                new BigDecimal("20000.00"),
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                null);

        assertEquals(BigDecimal.ZERO.setScale(2), resultado.getImpostoDevido());
    }

    @Test
    void faixa2_semDeducoes() {
        var resultado = service.calcular(
                faixas2020,
                new BigDecimal("30000.00"),
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                null);

        // (30000 × 7,5%) − 1713,58 = 2250 − 1713,58 = 536,42
        assertEquals(new BigDecimal("536.42"), resultado.getImpostoDevido());
    }

    @Test
    void formulaEquivalenteAoProgressivo() {
        BigDecimal base = new BigDecimal("81974.78");
        BigDecimal formula = service.calcularImpostoFormula(base, faixas2020);
        assertEquals(new BigDecimal("12110.74"), formula);
    }

    @Test
    void faixaIsenta_anoCalendario2023() {
        List<IrTabelaTributacao> faixas2023 = criarFaixas2023();
        var resultado = service.calcular(
                faixas2023,
                new BigDecimal("24511.92"),
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                null);

        assertEquals(BigDecimal.ZERO.setScale(2), resultado.getImpostoDevido());
    }

    @Test
    void reducaoAnual_rendimentosAte60000_zeraImposto() {
        IrParametrosAnuais params = IrParametrosAnuais.builder()
                .reducaoAnualAtiva(true)
                .reducaoRendimentoLimiteIsencao(new BigDecimal("60000.00"))
                .reducaoMaximaCompleta(new BigDecimal("2694.15"))
                .reducaoConstanteLinear(new BigDecimal("8429.73"))
                .reducaoCoeficienteLinear(new BigDecimal("0.095575"))
                .reducaoRendimentoLimiteSuperior(new BigDecimal("88200.00"))
                .build();

        List<IrTabelaTributacao> faixas2026 = criarFaixas2026();
        var resultado = service.calcular(
                faixas2026,
                new BigDecimal("50000.00"),
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                params);

        assertEquals(BigDecimal.ZERO.setScale(2), resultado.getImpostoDevido());
        assertTrue(resultado.getReducaoAnual().compareTo(BigDecimal.ZERO) > 0);
    }

    @Test
    void reducaoAnual_faixaDecrescente_70000() {
        IrParametrosAnuais params = IrParametrosAnuais.builder()
                .reducaoAnualAtiva(true)
                .reducaoRendimentoLimiteIsencao(new BigDecimal("60000.00"))
                .reducaoMaximaCompleta(new BigDecimal("2694.15"))
                .reducaoConstanteLinear(new BigDecimal("8429.73"))
                .reducaoCoeficienteLinear(new BigDecimal("0.095575"))
                .reducaoRendimentoLimiteSuperior(new BigDecimal("88200.00"))
                .build();

        List<IrTabelaTributacao> faixas2026 = criarFaixas2026();
        var resultado = service.calcular(
                faixas2026,
                new BigDecimal("70000.00"),
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                params);

        // Redução = 8429.73 - (0.095575 × 70000) = 1739.48
        assertEquals(new BigDecimal("1739.48"), resultado.getReducaoAnual());
    }

    @Test
    void reducaoAnual_semEfeito_antesDe2026() {
        var resultado = service.calcular(
                faixas2020,
                new BigDecimal("50000.00"),
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                null);

        assertEquals(BigDecimal.ZERO.setScale(2), resultado.getReducaoAnual());
    }

    private List<IrTabelaTributacao> criarFaixas2023() {
        List<IrTabelaTributacao> faixas = new ArrayList<>();
        faixas.add(faixa(2023, 1, "0", "24511.92", "0", "0", "Isento"));
        faixas.add(faixa(2023, 2, "24511.93", "33919.80", "0.075", "1838.39", "7,5%"));
        faixas.add(faixa(2023, 3, "33919.81", "45012.60", "0.15", "4382.38", "15%"));
        faixas.add(faixa(2023, 4, "45012.61", "55976.16", "0.225", "7758.32", "22,5%"));
        faixas.add(faixa(2023, 5, "55976.17", null, "0.275", "10557.13", "27,5%"));
        return faixas;
    }

    private List<IrTabelaTributacao> criarFaixas2026() {
        List<IrTabelaTributacao> faixas = new ArrayList<>();
        faixas.add(faixa(2026, 1, "0", "29145.60", "0", "0", "Isento"));
        faixas.add(faixa(2026, 2, "29145.61", "33919.80", "0.075", "2185.92", "7,5%"));
        faixas.add(faixa(2026, 3, "33919.81", "45012.60", "0.15", "4729.91", "15%"));
        faixas.add(faixa(2026, 4, "45012.61", "55976.16", "0.225", "8105.85", "22,5%"));
        faixas.add(faixa(2026, 5, "55976.17", null, "0.275", "10904.66", "27,5%"));
        return faixas;
    }

    private List<IrTabelaTributacao> criarFaixas2016a2022(int ano) {
        List<IrTabelaTributacao> faixas = new ArrayList<>();
        faixas.add(faixa(ano, 1, "0", "22847.76", "0", "0", "Isento"));
        faixas.add(faixa(ano, 2, "22847.77", "33919.80", "0.075", "1713.58", "7,5%"));
        faixas.add(faixa(ano, 3, "33919.81", "45012.60", "0.15", "4257.57", "15%"));
        faixas.add(faixa(ano, 4, "45012.61", "55976.16", "0.225", "7633.51", "22,5%"));
        faixas.add(faixa(ano, 5, "55976.17", null, "0.275", "10432.32", "27,5%"));
        return faixas;
    }

    private IrTabelaTributacao faixa(int ano, int n, String inf, String sup, String aliq, String ded, String desc) {
        return IrTabelaTributacao.builder()
                .anoCalendario(ano)
                .tipoIncidencia("ANUAL")
                .faixa(n)
                .limiteInferior(new BigDecimal(inf))
                .limiteSuperior(sup != null ? new BigDecimal(sup) : null)
                .aliquota(new BigDecimal(aliq))
                .deducao(new BigDecimal(ded))
                .descricao(desc)
                .build();
    }
}
