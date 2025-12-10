package br.com.verticelabs.pdfprocessor.infrastructure.pdf;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Component
@RequiredArgsConstructor
public class FuncefMetadataExtractor {

    // Padrão para valor líquido: "R$ 1.385,66" ou "Valor Líquido: R$ 1.385,66"
    private static final Pattern VALOR_LIQUIDO_PATTERN = Pattern.compile(
            "(?:Valor\\s+L[ií]quido\\s*:?\\s*)?R\\$\\s*([0-9\\.]+,\\d{2})",
            Pattern.CASE_INSENSITIVE
    );

    // Padrão para número de benefício: "Benefício: 123456789" ou "Nº Benefício: 123456789"
    private static final Pattern NUMERO_BENEFICIO_PATTERN = Pattern.compile(
            "(?:N[º°]?\\s*)?(?:Benef[ií]cio|Benef)\\s*:?\\s*(\\d+)",
            Pattern.CASE_INSENSITIVE
    );

    // Padrão para Nome: Linha com nome completo (geralmente no topo)
    // Exemplo: "Nome: FLAVIO JOSE PEREIRA ALMEIDA"
    // Captura tudo após "Nome:" até encontrar "Matrícula", "CPF" ou fim da linha
    // O nome geralmente está em uma linha própria ou antes de "Matrícula"
    private static final Pattern NOME_PATTERN = Pattern.compile(
            "(?i)Nome\\s*:?\\s*([A-ZÁÉÍÓÚÀÈÌÒÙÂÊÎÔÛÃÕÇ][A-ZÁÉÍÓÚÀÈÌÒÙÂÊÎÔÛÃÕÇ\\s]{2,}?)(?=\\s*(?:Matr[ií]cula|CPF|$|\\n))",
            Pattern.MULTILINE
    );

    // Padrão para Matrícula FUNCEF: "0437412" (sem traço)
    private static final Pattern MATRICULA_FUNCEF_PATTERN = Pattern.compile(
            "Matr[ií]cula\\s*:?\\s*(\\d{7})",
            Pattern.CASE_INSENSITIVE
    );

    /**
     * Extrai metadados específicos da FUNCEF de uma página.
     * 
     * @param pageText Texto completo da página
     * @return Metadados extraídos
     */
    public FuncefMetadata extract(String pageText) {
        if (pageText == null || pageText.trim().isEmpty()) {
            return new FuncefMetadata(null, null, null, null);
        }

        String valorLiquido = extractValorLiquido(pageText);
        String numeroBeneficio = extractNumeroBeneficio(pageText);
        String nome = extractNome(pageText);
        String matricula = extractMatricula(pageText);

        return new FuncefMetadata(valorLiquido, numeroBeneficio, nome, matricula);
    }

    /**
     * Extrai valor líquido do rodapé.
     * Formato esperado: "R$ 1.385,66" ou "Valor Líquido: R$ 1.385,66"
     */
    private String extractValorLiquido(String pageText) {
        Matcher matcher = VALOR_LIQUIDO_PATTERN.matcher(pageText);
        if (matcher.find()) {
            return "R$ " + matcher.group(1);
        }
        return null;
    }

    /**
     * Extrai número de benefício do cabeçalho.
     * Formato esperado: "Benefício: 123456789" ou "Nº Benefício: 123456789"
     */
    private String extractNumeroBeneficio(String pageText) {
        Matcher matcher = NUMERO_BENEFICIO_PATTERN.matcher(pageText);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return null;
    }

    /**
     * Extrai nome do titular.
     * Formato esperado: "Nome: FLAVIO JOSE PEREIRA ALMEIDA"
     * Baseado na imagem: o nome aparece após "Nome:" e antes de "Matrícula" ou "CPF"
     */
    private String extractNome(String pageText) {
        Matcher matcher = NOME_PATTERN.matcher(pageText);
        if (matcher.find()) {
            String nome = matcher.group(1).trim();
            
            // Remover qualquer coisa após "Matrícula", "Matr" ou "CPF"
            int matrIndex = nome.toLowerCase().indexOf("matr");
            if (matrIndex > 0) {
                nome = nome.substring(0, matrIndex).trim();
            }
            int cpfIndex = nome.toLowerCase().indexOf("cpf");
            if (cpfIndex > 0) {
                nome = nome.substring(0, cpfIndex).trim();
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
            
            log.debug("Nome extraído FUNCEF: '{}'", nome);
            return nome;
        }
        log.debug("Padrão de nome não encontrado no texto");
        return null;
    }

    /**
     * Extrai matrícula FUNCEF.
     * Formato esperado: "Matrícula: 0437412" (sem traço)
     */
    private String extractMatricula(String pageText) {
        Matcher matcher = MATRICULA_FUNCEF_PATTERN.matcher(pageText);
        if (matcher.find()) {
            String matricula = matcher.group(1).trim();
            log.debug("Matrícula extraída FUNCEF: '{}'", matricula);
            return matricula;
        }
        log.debug("Matrícula FUNCEF não encontrada no texto");
        return null;
    }

    /**
     * Classe para armazenar metadados extraídos da FUNCEF.
     */
    public static class FuncefMetadata {
        private final String valorLiquido;
        private final String numeroBeneficio;
        private final String nome;
        private final String matricula;

        public FuncefMetadata(String valorLiquido, String numeroBeneficio, String nome, String matricula) {
            this.valorLiquido = valorLiquido;
            this.numeroBeneficio = numeroBeneficio;
            this.nome = nome;
            this.matricula = matricula;
        }

        public String getValorLiquido() {
            return valorLiquido;
        }

        public String getNumeroBeneficio() {
            return numeroBeneficio;
        }

        public String getNome() {
            return nome;
        }

        public String getMatricula() {
            return matricula;
        }
    }
}

