package br.com.verticelabs.pdfprocessor.domain.exceptions;

/**
 * Lançada quando o CPF extraído da declaração de IR não corresponde
 * ao CPF da pessoa cadastrada no sistema.
 */
public class DeclaracaoCpfMismatchException extends RuntimeException {

    private final String cpfPessoa;
    private final String cpfDeclaracao;

    public DeclaracaoCpfMismatchException(String cpfPessoa, String cpfDeclaracao) {
        super(String.format(
                "A declaração não pertence a esta pessoa. " +
                "CPF cadastrado: %s — CPF na declaração: %s. " +
                "Verifique se o arquivo enviado é o correto.",
                cpfPessoa, cpfDeclaracao));
        this.cpfPessoa = cpfPessoa;
        this.cpfDeclaracao = cpfDeclaracao;
    }

    public String getCpfPessoa() {
        return cpfPessoa;
    }

    public String getCpfDeclaracao() {
        return cpfDeclaracao;
    }
}
