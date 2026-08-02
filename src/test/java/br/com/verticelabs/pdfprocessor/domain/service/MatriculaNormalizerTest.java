package br.com.verticelabs.pdfprocessor.domain.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MatriculaNormalizerTest {

    @Test
    void aceitaSeteDigitosCaixa() {
        assertEquals("0437412", MatriculaNormalizer.normalizeOptional("043741-2"));
        assertTrue(MatriculaNormalizer.isValid("0437412"));
    }

    @Test
    void aceitaOitoDigitosSabesp() {
        assertEquals("00008079", MatriculaNormalizer.normalizeOptional("00008079"));
        assertTrue(MatriculaNormalizer.isValid("00008079"));
    }

    @Test
    void vazioViraNull() {
        assertNull(MatriculaNormalizer.normalizeOptional(null));
        assertNull(MatriculaNormalizer.normalizeOptional("  "));
    }

    @Test
    void aceitaNoveDigitosSabesprev() {
        assertEquals("000080792", MatriculaNormalizer.normalizeOptional("000080792"));
        assertTrue(MatriculaNormalizer.isValid("000080792"));
    }

    @Test
    void rejeitaTamanhoInvalido() {
        assertThrows(IllegalArgumentException.class, () -> MatriculaNormalizer.normalizeOptional("123"));
        assertFalse(MatriculaNormalizer.isValid("1234567890"));
    }
}
