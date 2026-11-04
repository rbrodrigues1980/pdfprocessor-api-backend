package br.com.verticelabs.pdfprocessor.infrastructure.incometax;

import br.com.verticelabs.pdfprocessor.domain.service.IncomeTaxDeclarationService.IncomeTaxInfo;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

/**
 * Texto iText de declarações reais em {@code src/test/resources/irpf-fixtures} (seções IDENT, RESUMO e ALL).
 */
public final class IrpfFixtures {

    private static final String IDENT = "=== IDENT ===\n";
    private static final String RESUMO = "\n=== RESUMO ===\n";
    private static final String ALL = "\n=== ALL ===\n";

    private IrpfFixtures() {
    }

    public static IncomeTaxInfo parse(String nome) {
        String texto = carregar(nome);
        int r = texto.indexOf(RESUMO);
        int a = texto.indexOf(ALL);
        if (!texto.startsWith(IDENT) || r < 0 || a < r) {
            throw new IllegalStateException("Fixture inválida: " + nome);
        }
        return new ITextIncomeTaxServiceImpl().parseFromPageTexts(
                texto.substring(IDENT.length(), r),
                texto.substring(r + RESUMO.length(), a),
                texto.substring(a + ALL.length()));
    }

    private static String carregar(String nome) {
        String path = "/irpf-fixtures/" + nome + ".txt";
        try (InputStream in = IrpfFixtures.class.getResourceAsStream(path)) {
            if (in == null) {
                throw new IllegalStateException("Fixture não encontrada: " + path);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8).replace("\r\n", "\n");
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
