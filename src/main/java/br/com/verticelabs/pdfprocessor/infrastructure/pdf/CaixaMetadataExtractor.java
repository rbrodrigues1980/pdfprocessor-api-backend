package br.com.verticelabs.pdfprocessor.infrastructure.pdf;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Component
@RequiredArgsConstructor
public class CaixaMetadataExtractor {

    // Padrão para agência e conta: "AG 004 - CC 123456"
    private static final Pattern AGENCIA_CONTA_PATTERN = Pattern.compile(
            "AG\\s+(\\d+)\\s*-\\s*CC\\s+(\\d+)",
            Pattern.CASE_INSENSITIVE
    );

    // Padrão para GIREC: "GIREC 123" ou "GIREC: 123"
    private static final Pattern GIREC_PATTERN = Pattern.compile(
            "GIREC\\s*:?\\s*(\\d+)",
            Pattern.CASE_INSENSITIVE
    );

    // Padrão para Nome: Linha com nome completo (geralmente no topo)
    // Exemplo: "Nome: FLAVIO JOSE PEREIRA ALMEIDA"
    // Captura tudo após "Nome:" até encontrar "Data Admissão", "Matrícula" ou fim da linha
    // O nome geralmente está em uma linha própria ou antes de "Data Admissão"
    private static final Pattern NOME_PATTERN = Pattern.compile(
            "(?i)Nome\\s*:?\\s*([A-ZÁÉÍÓÚÀÈÌÒÙÂÊÎÔÛÃÕÇ][A-ZÁÉÍÓÚÀÈÌÒÙÂÊÎÔÛÃÕÇ\\s]{2,}?)(?=\\s*(?:Data\\s+Admiss[ãa]o|Matr[ií]cula|CPF|$|\\n))",
            Pattern.MULTILINE
    );

    // Padrão para Matrícula CAIXA: "043741-2" (com traço antes do último dígito)
    private static final Pattern MATRICULA_CAIXA_PATTERN = Pattern.compile(
            "Matr[ií]cula\\s*:?\\s*(\\d{6}-\\d)",
            Pattern.CASE_INSENSITIVE
    );

    /**
     * Extrai metadados específicos da CAIXA de uma página.
     * 
     * @param pageText Texto completo da página
     * @return Metadados extraídos
     */
    public CaixaMetadata extract(String pageText) {
        if (pageText == null || pageText.trim().isEmpty()) {
            return new CaixaMetadata(null, null, null, null);
        }

        String agenciaConta = extractAgenciaConta(pageText);
        String siglaGIREC = extractSiglaGIREC(pageText);
        String nome = extractNome(pageText);
        String matricula = extractMatricula(pageText);

        return new CaixaMetadata(agenciaConta, siglaGIREC, nome, matricula);
    }

    /**
     * Extrai agência e conta do rodapé.
     * Formato esperado: "AG 004 - CC 123456"
     */
    private String extractAgenciaConta(String pageText) {
        Matcher matcher = AGENCIA_CONTA_PATTERN.matcher(pageText);
        if (matcher.find()) {
            String agencia = matcher.group(1);
            String conta = matcher.group(2);
            return String.format("AG %s - CC %s", agencia, conta);
        }
        return null;
    }

    /**
     * Extrai sigla GIREC do cabeçalho.
     * Formato esperado: "GIREC 123" ou "GIREC: 123"
     */
    private String extractSiglaGIREC(String pageText) {
        Matcher matcher = GIREC_PATTERN.matcher(pageText);
        if (matcher.find()) {
            return "GIREC " + matcher.group(1);
        }
        return null;
    }

    /**
     * Extrai nome do titular.
     * Formato esperado: "Nome: FLAVIO JOSE PEREIRA ALMEIDA"
     * Baseado na imagem: o nome aparece após "Nome:" e antes de "Data Admissão" ou "Matrícula"
     */
    private String extractNome(String pageText) {
        Matcher matcher = NOME_PATTERN.matcher(pageText);
        if (matcher.find()) {
            String nome = matcher.group(1).trim();
            
            // Remover qualquer coisa após "Matrícula", "Matr" ou "Data Admissão"
            int matrIndex = nome.toLowerCase().indexOf("matr");
            if (matrIndex > 0) {
                nome = nome.substring(0, matrIndex).trim();
            }
            int dataAdmissaoIndex = nome.toLowerCase().indexOf("data");
            if (dataAdmissaoIndex > 0) {
                nome = nome.substring(0, dataAdmissaoIndex).trim();
            }
            
            // Limpar espaços extras
            nome = nome.replaceAll("\\s+", " ");
            
            // Remover caracteres não alfabéticos no final (exceto espaços)
            nome = nome.replaceAll("[^A-ZÁÉÍÓÚÀÈÌÒÙÂÊÎÔÛÃÕÇ\\s]+$", "");
            
            // Remover se for muito curto (provavelmente capturou algo errado)
            if (nome.length() < 5) {
                log.debug("Nome extraído muito curto (menos de 5 caracteres), ignorando: '{}'", nome);
                return null;
            }
            
            // Verificar se não capturou apenas "Matr" ou parte de "Matrícula"
            if (nome.equalsIgnoreCase("Matr") || nome.toLowerCase().startsWith("matr")) {
                log.debug("Nome extraído parece ser parte de 'Matrícula', ignorando: '{}'", nome);
                return null;
            }
            
            // Verificar se contém pelo menos uma letra maiúscula (nomes geralmente são em maiúsculas)
            if (!nome.matches(".*[A-ZÁÉÍÓÚÀÈÌÒÙÂÊÎÔÛÃÕÇ].*")) {
                log.debug("Nome extraído não contém letras maiúsculas, ignorando: '{}'", nome);
                return null;
            }
            
            // Verificar se parece um nome válido (pelo menos 2 palavras ou mais de 10 caracteres)
            String[] palavras = nome.split("\\s+");
            if (palavras.length < 2 && nome.length() < 10) {
                log.debug("Nome extraído não parece válido (menos de 2 palavras e menos de 10 caracteres), ignorando: '{}'", nome);
                return null;
            }
            
            log.debug("Nome extraído CAIXA: '{}'", nome);
            return nome;
        }
        log.debug("Padrão de nome não encontrado no texto");
        return null;
    }

    /**
     * Extrai matrícula CAIXA.
     * Formato esperado: "Matrícula: 043741-2" (com traço antes do último dígito)
     */
    private String extractMatricula(String pageText) {
        Matcher matcher = MATRICULA_CAIXA_PATTERN.matcher(pageText);
        if (matcher.find()) {
            String matricula = matcher.group(1).trim();
            log.debug("Matrícula extraída CAIXA: '{}'", matricula);
            return matricula;
        }
        log.debug("Matrícula CAIXA não encontrada no texto");
        return null;
    }

    /**
     * Classe para armazenar metadados extraídos da CAIXA.
     */
    public static class CaixaMetadata {
        private final String agenciaConta;
        private final String siglaGIREC;
        private final String nome;
        private final String matricula;

        public CaixaMetadata(String agenciaConta, String siglaGIREC, String nome, String matricula) {
            this.agenciaConta = agenciaConta;
            this.siglaGIREC = siglaGIREC;
            this.nome = nome;
            this.matricula = matricula;
        }

        public String getAgenciaConta() {
            return agenciaConta;
        }

        public String getSiglaGIREC() {
            return siglaGIREC;
        }

        public String getNome() {
            return nome;
        }

        public String getMatricula() {
            return matricula;
        }
    }
}

