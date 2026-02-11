package br.com.verticelabs.pdfprocessor.infrastructure.pdf;

import br.com.verticelabs.pdfprocessor.domain.service.MonthYearDetectionService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
public class MonthYearDetectionServiceImpl implements MonthYearDetectionService {

    // Mapeamento de meses em português
    private static final Map<String, String> MONTH_MAP = new HashMap<>();
    
    static {
        MONTH_MAP.put("JANEIRO", "01");
        MONTH_MAP.put("FEVEREIRO", "02");
        MONTH_MAP.put("MARÇO", "03");
        MONTH_MAP.put("MARCO", "03");
        MONTH_MAP.put("ABRIL", "04");
        MONTH_MAP.put("MAIO", "05");
        MONTH_MAP.put("JUNHO", "06");
        MONTH_MAP.put("JULHO", "07");
        MONTH_MAP.put("AGOSTO", "08");
        MONTH_MAP.put("SETEMBRO", "09");
        MONTH_MAP.put("OUTUBRO", "10");
        MONTH_MAP.put("NOVEMBRO", "11");
        MONTH_MAP.put("DEZEMBRO", "12");
    }

    // Padrão para CAIXA: "Mês/Ano de Pagamento" seguido de mês em português e ano
    // Exemplos: "JANEIRO / 2016", "Mês/Ano de Pagamento: JANEIRO / 2016"
    private static final Pattern CAIXA_MONTH_YEAR_PATTERN = Pattern.compile(
        "(?:M[êe]s/Ano\\s+de\\s+Pagamento[\\s:]*)?([A-ZÇÁÉÍÓÚÃÊÔ]+)\\s*/\\s*(\\d{4})",
        Pattern.CASE_INSENSITIVE | Pattern.MULTILINE
    );

    // Padrão para FUNCEF: "Ano Pagamento / Mês" seguido de ano e mês numérico
    // Exemplos: "2018/01", "Ano Pagamento / Mês: 2018/01"
    private static final Pattern FUNCEF_MONTH_YEAR_PATTERN = Pattern.compile(
        "(?:Ano\\s+Pagamento\\s*/\\s*M[êe]s[\\s:]*)?(\\d{4})\\s*/\\s*(\\d{1,2})",
        Pattern.CASE_INSENSITIVE | Pattern.MULTILINE
    );

    @Override
    public Mono<Optional<String>> detectMonthYear(String pageText) {
        if (pageText == null || pageText.trim().isEmpty()) {
            return Mono.just(Optional.empty());
        }

        return Mono.<Optional<String>>fromCallable(() -> {
            // Primeiro tenta formato CAIXA (mês em português / ano)
            Matcher caixaMatcher = CAIXA_MONTH_YEAR_PATTERN.matcher(pageText);
            
            if (caixaMatcher.find()) {
                String monthName = caixaMatcher.group(1).toUpperCase().trim();
                String year = caixaMatcher.group(2).trim();
                
                // Normalizar mês (remover acentos se necessário)
                monthName = normalizeMonthName(monthName);
                
                String monthNumber = MONTH_MAP.get(monthName);
                
                if (monthNumber != null) {
                    String monthYear = year + "-" + monthNumber;
                    log.debug("Mês/Ano detectado (CAIXA): {} -> {}", caixaMatcher.group(0), monthYear);
                    return Optional.of(monthYear);
                } else {
                    log.warn("Mês não reconhecido (CAIXA): {}", monthName);
                }
            }
            
            // Se não encontrou formato CAIXA, tenta formato FUNCEF (ano / mês numérico)
            Matcher funcefMatcher = FUNCEF_MONTH_YEAR_PATTERN.matcher(pageText);
            
            if (funcefMatcher.find()) {
                String year = funcefMatcher.group(1).trim();
                String monthNumber = funcefMatcher.group(2).trim();
                
                // Validar mês (deve ser entre 01 e 12)
                int month = Integer.parseInt(monthNumber);
                if (month >= 1 && month <= 12) {
                    // Formatar mês com zero à esquerda se necessário
                    String monthFormatted = String.format("%02d", month);
                    String monthYear = year + "-" + monthFormatted;
                    log.debug("Mês/Ano detectado (FUNCEF): {} -> {}", funcefMatcher.group(0), monthYear);
                    return Optional.of(monthYear);
                } else {
                    log.warn("Mês inválido (FUNCEF): {}", monthNumber);
                }
            }
            
            return Optional.<String>empty();
        }).subscribeOn(reactor.core.scheduler.Schedulers.boundedElastic());
    }

    private String normalizeMonthName(String monthName) {
        // Remover acentos e normalizar
        monthName = monthName.toUpperCase().trim();
        
        // Se já está no mapa, retorna
        if (MONTH_MAP.containsKey(monthName)) {
            return monthName;
        }
        
        // Tentar normalizar variações comuns
        monthName = monthName.replace("Ç", "C");
        monthName = monthName.replace("Á", "A");
        monthName = monthName.replace("É", "E");
        monthName = monthName.replace("Í", "I");
        monthName = monthName.replace("Ó", "O");
        monthName = monthName.replace("Ú", "U");
        monthName = monthName.replace("Ã", "A");
        monthName = monthName.replace("Ê", "E");
        monthName = monthName.replace("Ô", "O");
        
        return monthName;
    }
}

