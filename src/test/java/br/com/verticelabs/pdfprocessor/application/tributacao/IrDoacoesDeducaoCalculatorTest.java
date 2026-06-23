package br.com.verticelabs.pdfprocessor.application.tributacao;

import br.com.verticelabs.pdfprocessor.application.tributacao.dto.DoacoesBrutasDTO;
import br.com.verticelabs.pdfprocessor.application.tributacao.dto.DoacoesEfetivasDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

class IrDoacoesDeducaoCalculatorTest {

    private IrDoacoesDeducaoCalculator calculator;

    @BeforeEach
    void setUp() {
        calculator = new IrDoacoesDeducaoCalculator();
    }

    @Test
    void capIncentivo6PctGlobal() {
        DoacoesBrutasDTO brutas = DoacoesBrutasDTO.builder()
                .deducaoIncentivoBruta(new BigDecimal("10000.00"))
                .build();

        DoacoesEfetivasDTO efetivas = calculator.calcularEfetivas(new BigDecimal("10000.00"), brutas);

        assertEquals(new BigDecimal("600.00"), efetivas.getDeducaoIncentivoEfetiva());
    }

    @Test
    void capPronon1Pct() {
        DoacoesBrutasDTO brutas = DoacoesBrutasDTO.builder()
                .dedPrononBruta(new BigDecimal("500.00"))
                .build();

        DoacoesEfetivasDTO efetivas = calculator.calcularEfetivas(new BigDecimal("10000.00"), brutas);

        assertEquals(new BigDecimal("100.00"), efetivas.getDedPrononEfetiva());
    }

    @Test
    void capPronas1Pct() {
        DoacoesBrutasDTO brutas = DoacoesBrutasDTO.builder()
                .dedPronasBruta(new BigDecimal("500.00"))
                .build();

        DoacoesEfetivasDTO efetivas = calculator.calcularEfetivas(new BigDecimal("20000.00"), brutas);

        assertEquals(new BigDecimal("200.00"), efetivas.getDedPronasEfetiva());
    }

    @Test
    void abaixoLimite_mantemValor() {
        DoacoesBrutasDTO brutas = DoacoesBrutasDTO.builder()
                .deducaoIncentivoBruta(new BigDecimal("100.00"))
                .dedPrononBruta(new BigDecimal("50.00"))
                .dedPronasBruta(new BigDecimal("30.00"))
                .build();

        DoacoesEfetivasDTO efetivas = calculator.calcularEfetivas(new BigDecimal("10000.00"), brutas);

        assertEquals(new BigDecimal("100.00"), efetivas.getDeducaoIncentivoEfetiva());
        assertEquals(new BigDecimal("50.00"), efetivas.getDedPrononEfetiva());
        assertEquals(new BigDecimal("30.00"), efetivas.getDedPronasEfetiva());
        assertEquals(new BigDecimal("180.00"), efetivas.totalEfetivo());
    }

    @Test
    void inssDomestico_2016_aplicaTeto() {
        BigDecimal efetivo = IrDoacoesDeducaoCalculator.calcularInssDomesticoEfetivo(
                new BigDecimal("1500.00"), 2016, null);
        assertEquals(new BigDecimal("1092.00"), efetivo);
    }

    @Test
    void inssDomestico_2019_zero() {
        BigDecimal efetivo = IrDoacoesDeducaoCalculator.calcularInssDomesticoEfetivo(
                new BigDecimal("1500.00"), 2019, null);
        assertEquals(BigDecimal.ZERO.setScale(2), efetivo);
    }
}
