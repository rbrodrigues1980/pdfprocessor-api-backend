package br.com.verticelabs.pdfprocessor.application.consolidation;

import br.com.verticelabs.pdfprocessor.application.entries.EntryQueryUseCase;
import br.com.verticelabs.pdfprocessor.application.rubricas.RubricaUseCase;
import br.com.verticelabs.pdfprocessor.domain.exceptions.*;
import br.com.verticelabs.pdfprocessor.domain.model.PayrollDocument;
import br.com.verticelabs.pdfprocessor.domain.model.PayrollEntry;
import br.com.verticelabs.pdfprocessor.domain.model.Person;
import br.com.verticelabs.pdfprocessor.domain.model.Rubrica;
import br.com.verticelabs.pdfprocessor.domain.repository.PayrollDocumentRepository;
import br.com.verticelabs.pdfprocessor.domain.repository.PersonRepository;
import br.com.verticelabs.pdfprocessor.infrastructure.security.ReactiveSecurityContextHelper;
import br.com.verticelabs.pdfprocessor.interfaces.consolidation.dto.ConsolidatedResponse;
import br.com.verticelabs.pdfprocessor.interfaces.consolidation.dto.ConsolidationRow;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ConsolidationUseCase {

    private final PersonRepository personRepository;
    private final EntryQueryUseCase entryQueryUseCase;
    private final RubricaUseCase rubricaUseCase;
    private final ReferenceNormalizer referenceNormalizer;
    private final PayrollDocumentRepository documentRepository;

    /**
     * Consolida todas as rubricas de uma pessoa em uma matriz ano/mês por rubrica.
     * 
     * @param cpf    CPF da pessoa
     * @param ano    Ano opcional para filtrar (formato: "2017")
     * @param origem Origem opcional para filtrar ("CAIXA" ou "FUNCEF")
     * @return Mono com a resposta consolidada
     */
    public Mono<ConsolidatedResponse> consolidate(String cpf, String ano, String origem) {
        return consolidate(cpf, null, ano, origem);
    }

    /**
     * Consolida todas as rubricas de uma pessoa em uma matriz ano/mês por rubrica.
     * Versão que aceita tenantId explicitamente para evitar problemas com múltiplas
     * pessoas com mesmo CPF.
     * 
     * @param cpf      CPF da pessoa
     * @param tenantId ID do tenant da pessoa (opcional, se null usa multi-tenancy
     *                 do contexto)
     * @param ano      Ano opcional para filtrar (formato: "2017")
     * @param origem   Origem opcional para filtrar ("CAIXA" ou "FUNCEF")
     * @return Mono com a resposta consolidada
     */
    public Mono<ConsolidatedResponse> consolidate(String cpf, String tenantId, String ano, String origem) {
        log.info("=== ConsolidationUseCase.consolidate() INICIADO ===");
        log.info("CPF: {}, Ano: {}, Origem: {}", cpf, ano, origem);

        // Validar origem se fornecida
        if (origem != null && !origem.isEmpty()) {
            log.debug("Validando origem: {}", origem);
            if (!origem.equals("CAIXA") && !origem.equals("FUNCEF")) {
                log.warn("Origem inválida recebida: {}", origem);
                return Mono.error(new InvalidOriginException(origem));
            }
            log.debug("Origem válida: {}", origem);
        }

        // Validar ano se fornecido
        if (ano != null && !ano.isEmpty()) {
            log.debug("Validando ano: {}", ano);
            try {
                int yearInt = Integer.parseInt(ano);
                if (yearInt < 2000 || yearInt > 2100) {
                    log.warn("Ano fora do range válido (2000-2100): {}", yearInt);
                    return Mono.error(new InvalidYearException(ano));
                }
                log.debug("Ano válido: {}", yearInt);
            } catch (NumberFormatException e) {
                log.warn("Ano com formato inválido: {}", ano);
                return Mono.error(new InvalidYearException(ano));
            }
        }

        // 1. Buscar pessoa (com multi-tenancy)
        log.info("Passo 1: Buscando pessoa no repositório para CPF: {}", cpf);
        Mono<Person> personMono;

        if (tenantId != null && !tenantId.isEmpty()) {
            // Se tenantId foi fornecido explicitamente, usar ele diretamente
            log.debug("Buscando pessoa com tenantId explícito: {}", tenantId);
            personMono = personRepository.findByTenantIdAndCpf(tenantId, cpf);
        } else {
            // Se não, usar multi-tenancy do contexto de segurança
            personMono = ReactiveSecurityContextHelper.isSuperAdmin()
                    .flatMap(isSuperAdmin -> {
                        if (isSuperAdmin) {
                            // SUPER_ADMIN: buscar pessoa sem filtrar por tenantId
                            log.debug("SUPER_ADMIN: buscando pessoa sem filtro de tenantId");
                            return personRepository.findByCpf(cpf);
                        } else {
                            // Outros usuários: buscar pessoa filtrando por tenantId
                            log.debug("Usuário regular: buscando pessoa com filtro de tenantId");
                            return ReactiveSecurityContextHelper.getTenantId()
                                    .flatMap(ctxTenantId -> personRepository.findByTenantIdAndCpf(ctxTenantId, cpf));
                        }
                    });
        }

        return personMono
                .switchIfEmpty(Mono.defer(() -> {
                    log.error("Pessoa não encontrada no repositório para CPF: {}", cpf);
                    return Mono.error(new PersonNotFoundException(cpf));
                }))
                .flatMap(person -> {
                    log.info("✓ Pessoa encontrada: {} ({})", person.getNome(), person.getCpf());
                    log.debug("Total de documentos da pessoa: {}",
                            person.getDocumentos() != null ? person.getDocumentos().size() : 0);

                    // 2. Buscar todas as rubricas ativas (para filtrar entries válidas)
                    log.info("Passo 2: Buscando rubricas ativas no sistema");
                    Mono<Set<String>> validRubricasCodes = rubricaUseCase.listarAtivas()
                            .map(Rubrica::getCodigo)
                            .collect(Collectors.toSet())
                            .doOnNext(codes -> {
                                log.info("✓ Total de rubricas ativas encontradas: {}", codes.size());
                                log.debug("Códigos de rubricas ativas: {}", codes);
                            });

                    // 3. Buscar todas as entries da pessoa (usando tenantId da pessoa encontrada)
                    log.info("Passo 3: Buscando entries da pessoa no repositório");
                    Flux<PayrollEntry> entriesFlux = entryQueryUseCase.findByCpf(cpf, person.getTenantId());

                    return Mono.zip(validRubricasCodes, entriesFlux.collectList())
                            .flatMap(tuple -> {
                                Set<String> validCodes = tuple.getT1();
                                List<PayrollEntry> allEntries = tuple.getT2();

                                if (allEntries.isEmpty()) {
                                    log.warn("Nenhuma entry encontrada para CPF: {}", cpf);
                                    return Mono.error(new NoEntriesFoundException(cpf));
                                }

                                log.info("✓ Total de entries encontradas: {}", allEntries.size());

                                // 4. Filtrar entries válidas (rubricas ativas)
                                log.info("Passo 4: Filtrando entries por rubricas válidas/ativas");
                                List<PayrollEntry> validEntries = allEntries.stream()
                                        .filter(entry -> validCodes.contains(entry.getRubricaCodigo()))
                                        .collect(Collectors.toList());

                                log.info("✓ Entries após filtrar rubricas válidas: {} (removidas: {})",
                                        validEntries.size(), allEntries.size() - validEntries.size());

                                // 5. Aplicar filtros opcionais
                                log.info("Passo 5: Aplicando filtros opcionais (ano: {}, origem: {})", ano, origem);
                                List<PayrollEntry> filteredEntries = applyFilters(validEntries, ano, origem);

                                log.info("✓ Entries após aplicar filtros: {} (removidas: {})",
                                        filteredEntries.size(), validEntries.size() - filteredEntries.size());

                                if (filteredEntries.isEmpty()) {
                                    log.warn("Nenhuma entry restou após aplicar filtros");
                                    return Mono.error(new NoEntriesFoundException(cpf));
                                }

                                // 6. Buscar documentos para identificar meses de origem
                                log.info("Passo 6: Buscando documentos para identificar meses de origem");
                                Set<String> documentoIds = filteredEntries.stream()
                                        .map(PayrollEntry::getDocumentoId)
                                        .filter(Objects::nonNull)
                                        .collect(Collectors.toSet());

                                log.info("Total de documentoIds únicos para buscar: {}", documentoIds.size());

                                return Flux.fromIterable(documentoIds)
                                        .flatMap(docId -> {
                                            log.debug("Buscando documento: {}", docId);
                                            return documentRepository.findByTenantIdAndId(person.getTenantId(), docId)
                                                    .doOnNext(doc -> {
                                                        log.debug("Documento {} encontrado. Meses detectados: {}",
                                                                docId, doc.getMesesDetectados());
                                                    })
                                                    .doOnError(e -> log.warn("Erro ao buscar documento {}: {}", docId,
                                                            e.getMessage()));
                                        })
                                        .collectMap(PayrollDocument::getId)
                                        .doOnNext(map -> log.info("Total de documentos encontrados: {}", map.size()))
                                        .flatMap(documentosMap -> {
                                            // 7. Construir resposta consolidada
                                            log.info("Passo 7: Construindo resposta consolidada");
                                            ConsolidatedResponse response = buildConsolidatedResponse(person,
                                                    filteredEntries, ano, documentosMap);

                                            log.info(
                                                    "=== ConsolidationUseCase.consolidate() CONCLUÍDO COM SUCESSO ===");
                                            log.info("Total de rubricas consolidadas: {}",
                                                    response.getRubricas().size());
                                            log.info("Anos processados: {}", response.getAnos());
                                            log.info("Total geral: R$ {}", response.getTotalGeral());

                                            return Mono.just(response);
                                        });
                            });
                });
    }

    /**
     * Aplica filtros opcionais às entries.
     */
    private List<PayrollEntry> applyFilters(List<PayrollEntry> entries, String ano, String origem) {
        log.debug("Aplicando filtros - Entrada: {} entries, Ano: {}, Origem: {}", entries.size(), ano, origem);

        int totalInicial = entries.size();

        List<PayrollEntry> filtered = entries.stream()
                .filter(entry -> {
                    // Filtro por ano
                    if (ano != null && !ano.isEmpty()) {
                        String normalizedRef = referenceNormalizer.normalize(entry.getReferencia());
                        if (normalizedRef == null) {
                            log.debug("Entry ignorada: referência não normalizável - {}", entry.getReferencia());
                            return false;
                        }
                        String entryYear = referenceNormalizer.extractYear(normalizedRef);
                        if (!ano.equals(entryYear)) {
                            log.trace("Entry ignorada por filtro de ano: {} != {} - Referência: {}",
                                    entryYear, ano, normalizedRef);
                            return false;
                        }
                    }

                    // Filtro por origem
                    if (origem != null && !origem.isEmpty()) {
                        if (!origem.equals(entry.getOrigem())) {
                            log.trace("Entry ignorada por filtro de origem: {} != {} - Entry origem: {}",
                                    entry.getOrigem(), origem, entry.getRubricaCodigo());
                            return false;
                        }
                    }

                    return true;
                })
                .collect(Collectors.toList());

        int removidas = totalInicial - filtered.size();
        if (ano != null && !ano.isEmpty() || origem != null && !origem.isEmpty()) {
            log.debug("Filtros aplicados - Removidas: {} entries, Restantes: {} entries", removidas, filtered.size());
        }
        log.debug("Resultado após filtros: {} entries", filtered.size());

        return filtered;
    }

    /**
     * Constrói a resposta consolidada a partir das entries filtradas.
     */
    private ConsolidatedResponse buildConsolidatedResponse(Person person, List<PayrollEntry> entries, String anoFiltro,
            Map<String, PayrollDocument> documentosMap) {
        log.debug("=== buildConsolidatedResponse() INICIADO ===");
        log.debug("Total de entries para processar: {}", entries.size());

        // Normalizar todas as referências
        log.debug("Normalizando referências das entries");
        Map<PayrollEntry, String> entryToNormalizedRef = new HashMap<>();
        Set<String> allYears = new TreeSet<>();
        int referenciasNaoNormalizadas = 0;

        for (PayrollEntry entry : entries) {
            String originalRef = entry.getReferencia();
            if (originalRef == null) {
                referenciasNaoNormalizadas++;
                log.trace("Entry sem referência: rubrica {}", entry.getRubricaCodigo());
                continue;
            }

            String trimmedRef = originalRef.trim();

            // Verificar se já está no formato YYYY-MM
            String normalized;
            if (referenceNormalizer.isValid(trimmedRef)) {
                normalized = trimmedRef;
                log.trace("Referência já normalizada: {}", normalized);
            } else {
                // Tentar normalizar (formatos: MM/YYYY, YYYY/MM)
                normalized = referenceNormalizer.normalize(trimmedRef);
                if (normalized != null) {
                    log.trace("Referência normalizada: {} -> {}", originalRef, normalized);
                }
            }

            if (normalized != null && referenceNormalizer.isValid(normalized)) {
                entryToNormalizedRef.put(entry, normalized);
                String year = referenceNormalizer.extractYear(normalized);
                if (year != null) {
                    allYears.add(year);
                    log.trace("Ano detectado: {}", year);
                }
            } else {
                referenciasNaoNormalizadas++;
                log.debug("Não foi possível normalizar referência: {} (rubrica: {})",
                        originalRef, entry.getRubricaCodigo());
            }
        }

        log.info("Referências normalizadas: {} / {} ({} ignoradas)",
                entryToNormalizedRef.size(), entries.size(), referenciasNaoNormalizadas);

        // Se filtrou por ano, usar apenas esse ano
        if (anoFiltro != null && !anoFiltro.isEmpty()) {
            log.debug("Aplicando filtro de ano na resposta: {}", anoFiltro);
            allYears.clear();
            allYears.add(anoFiltro);
        }

        log.info("Anos detectados para consolidação: {}", allYears);

        // Meses padrão de 01 a 12 (NÃO incluir mês 13 - valores com ref "YYYY-13" vão
        // para o mês do documento)
        List<String> meses = Arrays.asList("01", "02", "03", "04", "05", "06", "07", "08", "09", "10", "11", "12");
        log.debug("Meses configurados: {} (mês 13 não é uma coluna separada)", meses);

        // Mapear referências "YYYY-13" para o mês do documento usando o índice da página
        // no array mesesDetectados
        Map<PayrollEntry, String> entryToAdjustedRef = new HashMap<>();
        int entriesMonth13Count = 0;
        int entriesMonth13Ajustadas = 0;

        for (Map.Entry<PayrollEntry, String> entry : entryToNormalizedRef.entrySet()) {
            PayrollEntry payrollEntry = entry.getKey();
            String normalizedRef = entry.getValue();
            String adjustedRef = null;

            // PRIORIDADE 1: Sempre usar mesPagamento quando disponível (independente da referência)
            // Se a entry tem mesPagamento, SEMPRE usar ele em vez da referencia
            String mesPagamento = payrollEntry.getMesPagamento();
            if (mesPagamento != null && mesPagamento.matches("\\d{4}-\\d{2}")) {
                adjustedRef = mesPagamento;
                log.info("Entry {} (rubrica {}) - Ref original: {}, mesPagamento: {} -> usando mesPagamento (ignorando referencia)",
                        payrollEntry.getId(), payrollEntry.getRubricaCodigo(), normalizedRef, mesPagamento);
            }

            // Se não tem mesPagamento e a referência é "YYYY-13", usar lógica especial
            if (adjustedRef == null && normalizedRef != null && normalizedRef.matches("\\d{4}-13")) {
                entriesMonth13Count++;
                String ano = normalizedRef.split("-")[0];

                // PRIORIDADE 2: Tentar descobrir de outras entries da mesma página
                Integer pagina = payrollEntry.getPagina();
                String documentoId = payrollEntry.getDocumentoId();
                
                if (pagina != null && documentoId != null) {
                    // Buscar todas as entries da mesma página e documento
                    List<PayrollEntry> entriesMesmaPagina = entries.stream()
                            .filter(e -> documentoId.equals(e.getDocumentoId()))
                            .filter(e -> pagina.equals(e.getPagina()))
                            .filter(e -> e.getReferencia() != null)
                            .collect(Collectors.toList());
                    
                    log.debug("Entry {} (rubrica {}) - Página {}: encontradas {} entries na mesma página", 
                            payrollEntry.getId(), payrollEntry.getRubricaCodigo(), pagina, entriesMesmaPagina.size());
                    
                    // Tentar encontrar o mês a partir de entries com referência normal (não "XX-13")
                    for (PayrollEntry e : entriesMesmaPagina) {
                        String refOriginal = e.getReferencia();
                        if (refOriginal != null && !refOriginal.matches("\\d{4}-13")) {
                            String refParaUsar = null;
                            
                            // Primeiro, tentar usar a referência original se já estiver no formato YYYY-MM
                            if (refOriginal.matches("\\d{4}-\\d{2}")) {
                                refParaUsar = refOriginal;
                            } else {
                                // Se não, normalizar
                                refParaUsar = referenceNormalizer.normalize(refOriginal);
                            }
                            
                            if (refParaUsar != null && refParaUsar.matches("\\d{4}-\\d{2}")) {
                                String[] partes = refParaUsar.split("-");
                                String anoExtraido = partes[0];
                                String mesExtraido = partes[1];
                                
                                // Verificar se o ano corresponde e o mês não é 13
                                if (ano.equals(anoExtraido) && !"13".equals(mesExtraido)) {
                                    adjustedRef = ano + "-" + mesExtraido;
                                    log.info("Entry {} (rubrica {}) - Ref {} -> {} (Página: {}, Mês descoberto de outras entries: {})",
                                            payrollEntry.getId(), payrollEntry.getRubricaCodigo(), normalizedRef, adjustedRef,
                                            pagina, mesExtraido);
                                    break;
                                }
                            }
                        }
                    }
                }

                // Fallback: usar mês 11 (novembro)
                if (adjustedRef == null) {
                    adjustedRef = ano + "-11";
                    log.warn("Entry {} (rubrica {}) - Fallback para Novembro: Ref {} -> {} (mesPagamento não disponível)",
                            payrollEntry.getId(), payrollEntry.getRubricaCodigo(), normalizedRef, adjustedRef);
                }
                entriesMonth13Ajustadas++;
            }

            // Se ainda não tem adjustedRef, usar a referência normalizada (fallback quando não tem mesPagamento)
            if (adjustedRef == null) {
                adjustedRef = normalizedRef;
                log.debug("Entry {} (rubrica {}) - Usando referencia normalizada: {} (mesPagamento não disponível)",
                        payrollEntry.getId(), payrollEntry.getRubricaCodigo(), normalizedRef);
            }

            entryToAdjustedRef.put(payrollEntry, adjustedRef);
        }

        log.info("Entries com referência 'YYYY-13' encontradas: {}, ajustadas: {}", entriesMonth13Count,
                entriesMonth13Ajustadas);

        // Agrupar por rubrica
        log.debug("Agrupando entries por rubrica");
        Map<String, List<PayrollEntry>> entriesByRubrica = entries.stream()
                .filter(entryToAdjustedRef::containsKey)
                .collect(Collectors.groupingBy(PayrollEntry::getRubricaCodigo));

        log.info("Total de rubricas únicas para consolidar: {}", entriesByRubrica.size());
        log.debug("Códigos das rubricas: {}", entriesByRubrica.keySet());

        // Construir linhas de consolidação
        log.debug("Construindo linhas de consolidação (ConsolidationRow)");
        List<ConsolidationRow> rubricas = new ArrayList<>();

        for (Map.Entry<String, List<PayrollEntry>> rubricaEntry : entriesByRubrica.entrySet()) {
            String codigo = rubricaEntry.getKey();
            List<PayrollEntry> rubricaEntries = rubricaEntry.getValue();

            log.debug("Processando rubrica {} - {} entries", codigo, rubricaEntries.size());

            // Pegar descrição da primeira entry
            String descricao = rubricaEntries.isEmpty() ? "" : rubricaEntries.get(0).getRubricaDescricao();

            // Mapa para somar valores por mês
            Map<String, Double> valoresPorReferencia = new TreeMap<>();

            // Processar TODAS as entries (já ajustadas)
            for (PayrollEntry entry : rubricaEntries) {
                String adjustedRef = entryToAdjustedRef.get(entry);
                if (adjustedRef != null) {
                    double valor = entry.getValor() != null ? entry.getValor() : 0.0;
                    double valorAnterior = valoresPorReferencia.getOrDefault(adjustedRef, 0.0);
                    double valorNovo = valorAnterior + valor;
                    valoresPorReferencia.put(adjustedRef, valorNovo);
                    
                    // Log detalhado para rubricas problemáticas ou para ano 2016 mês 04
                    if (codigo.equals("4364") || codigo.equals("4459") || codigo.equals("3362") || codigo.equals("3394")) {
                        log.info("Rubrica {} - Entry {} (ref original: {}, ref ajustada: {}) - Valor: {} -> Total acumulado: {}",
                                codigo, entry.getId(), entry.getReferencia(), adjustedRef, valor, valorNovo);
                    } else if (adjustedRef != null && adjustedRef.startsWith("2016-04")) {
                        log.info("Rubrica {} - Entry {} (ref original: {}, ref ajustada: {}) - Valor: {} -> Total acumulado: {}",
                                codigo, entry.getId(), entry.getReferencia(), adjustedRef, valor, valorNovo);
                    } else {
                        log.trace("Rubrica {} - Ref {} recebe valor {}", codigo, adjustedRef, valor);
                    }
                } else {
                    log.warn("Rubrica {} - Entry {} não tem referência ajustada! Ref original: {}",
                            codigo, entry.getId(), entry.getReferencia());
                }
            }

            log.info("Rubrica {} possui valores em {} referências diferentes",
                    codigo, valoresPorReferencia.size());
            log.info("Rubrica {} - Valores por referência: {}", codigo, valoresPorReferencia);

            // Verificar se há entries com referência original "YYYY-13" em múltiplos meses
            boolean temReferencia13Multipla = false;
            Set<String> anosCom13 = new HashSet<>();
            for (PayrollEntry entry : rubricaEntries) {
                String refOriginal = entry.getReferencia();
                if (refOriginal != null && refOriginal.matches("\\d{4}-13")) {
                    String ano = refOriginal.split("-")[0];
                    anosCom13.add(ano);
                }
            }
            
            // Contar quantos meses diferentes têm valores para cada ano com "YYYY-13"
            for (String ano : anosCom13) {
                long mesesComValor13 = valoresPorReferencia.keySet().stream()
                        .filter(ref -> ref.startsWith(ano + "-"))
                        .filter(ref -> {
                            // Verificar se há entry original com "YYYY-13" que foi mapeada para este mês
                            return rubricaEntries.stream()
                                    .anyMatch(e -> {
                                        String refOrig = e.getReferencia();
                                        if (refOrig != null && refOrig.equals(ano + "-13")) {
                                            String adjustedRef = entryToAdjustedRef.get(e);
                                            return adjustedRef != null && adjustedRef.equals(ref);
                                        }
                                        return false;
                                    });
                        })
                        .count();
                
                if (mesesComValor13 > 1) {
                    temReferencia13Multipla = true;
                    log.info("Rubrica {} - Ano {} tem referência 'YYYY-13' em {} meses diferentes", 
                            codigo, ano, mesesComValor13);
                    break;
                } else if (mesesComValor13 == 1) {
                    log.info("Rubrica {} - Ano {} tem referência 'YYYY-13' em apenas 1 mês (será usado no total)", 
                            codigo, ano);
                }
            }

            // Preencher meses faltantes com zero para cada ano
            log.debug("Preenchendo meses faltantes com zero para rubrica {}", codigo);
            Map<String, Double> valoresCompletos = new TreeMap<>();
            int mesesComValor = 0;
            for (String year : allYears) {
                for (String month : meses) {
                    String referencia = year + "-" + month;
                    Double valor = valoresPorReferencia.getOrDefault(referencia, 0.0);
                    valoresCompletos.put(referencia, valor);
                    if (valor > 0) {
                        mesesComValor++;
                        log.debug("Rubrica {} - Mês {} tem valor: {}", codigo, referencia, valor);
                    }
                }
            }
            log.info("Rubrica {} - Meses com valor > 0: {} de {} possíveis. Valores completos: {}",
                    codigo, mesesComValor, allYears.size() * meses.size(), valoresCompletos);

            // Calcular total da rubrica
            // Primeiro, calcular o total normalmente (soma de todos os valores)
            double totalRubrica = valoresCompletos.values().stream()
                    .mapToDouble(Double::doubleValue)
                    .sum();
            log.info("Rubrica {} - Total inicial (soma de todos os valores): R$ {}", codigo, totalRubrica);
            
            // Se tem referência "YYYY-13" em múltiplos meses, subtrair os valores dos meses que não são o último
            if (temReferencia13Multipla) {
                log.info("Rubrica {} - Tem referência YYYY-13 múltipla, ajustando total", codigo);
                
                // Para cada ano com "YYYY-13" múltiplo, encontrar o último mês e subtrair os outros
                for (String year : anosCom13) {
                    log.info("Rubrica {} - Processando ano {} com referência YYYY-13 múltipla", codigo, year);
                    
                    // Encontrar todos os meses que têm valores de "YYYY-13" para este ano
                    List<String> mesesCom13 = new ArrayList<>();
                    for (PayrollEntry entry : rubricaEntries) {
                        String refOrig = entry.getReferencia();
                        if (refOrig != null && refOrig.equals(year + "-13")) {
                            String adjustedRef = entryToAdjustedRef.get(entry);
                            if (adjustedRef != null && adjustedRef.startsWith(year + "-")) {
                                if (!mesesCom13.contains(adjustedRef)) {
                                    mesesCom13.add(adjustedRef);
                                    log.info("Rubrica {} - Entry {} mapeada de {} para {} - Valor: {}", 
                                            codigo, entry.getId(), refOrig, adjustedRef, entry.getValor());
                                }
                            }
                        }
                    }
                    
                    log.info("Rubrica {} - Ano {}: meses encontrados com YYYY-13: {}", codigo, year, mesesCom13);
                    
                    // Se encontrou múltiplos meses com "YYYY-13", identificar o último e subtrair os outros
                    if (mesesCom13.size() > 1) {
                        // Ordenar por mês (maior primeiro) e pegar o primeiro (último mês)
                        String ultimoMes = mesesCom13.stream()
                                .sorted((a, b) -> {
                                    int mesA = Integer.parseInt(a.split("-")[1]);
                                    int mesB = Integer.parseInt(b.split("-")[1]);
                                    return Integer.compare(mesB, mesA); // Ordem decrescente
                                })
                                .findFirst()
                                .orElse(null);
                        
                        if (ultimoMes != null) {
                            log.info("Rubrica {} - Último mês identificado para ano {}: {}", codigo, year, ultimoMes);
                            
                            // Subtrair os valores dos meses que não são o último
                            for (String mes : mesesCom13) {
                                if (!mes.equals(ultimoMes)) {
                                    Double valorMes = valoresCompletos.getOrDefault(mes, 0.0);
                                    if (valorMes > 0) {
                                        totalRubrica -= valorMes;
                                        log.info("Rubrica {} - Subtraindo valor do mês {} (não é o último): R$ {} -> Total parcial: R$ {}", 
                                                codigo, mes, valorMes, totalRubrica);
                                    }
                                }
                            }
                        }
                    } else if (mesesCom13.size() == 1) {
                        // Se só tem um mês com "YYYY-13", não precisa subtrair nada
                        log.info("Rubrica {} - Ano {} tem apenas 1 mês com YYYY-13, não precisa ajustar", codigo, year);
                    }
                }
                
                log.info("Rubrica {} - Total final após ajuste (referência 13 múltipla): R$ {}", codigo, totalRubrica);
            } else {
                log.debug("Rubrica {} - Total calculado (sem referência 13 múltipla): R$ {}", codigo, totalRubrica);
            }

            ConsolidationRow row = ConsolidationRow.builder()
                    .codigo(codigo)
                    .descricao(descricao)
                    .valores(valoresCompletos)
                    .total(totalRubrica)
                    .build();

            rubricas.add(row);
        }

        // Ordenar rubricas por código
        log.debug("Ordenando rubricas por código");
        rubricas.sort(Comparator.comparing(ConsolidationRow::getCodigo));
        log.debug("Rubricas ordenadas: {}",
                rubricas.stream().map(ConsolidationRow::getCodigo).collect(Collectors.toList()));

        // Calcular totais mensais
        log.debug("Calculando totais mensais");
        Map<String, Double> totaisMensais = new TreeMap<>();
        for (String year : allYears) {
            for (String month : meses) {
                String referencia = year + "-" + month;
                double totalMes = rubricas.stream()
                        .mapToDouble(row -> row.getValores().getOrDefault(referencia, 0.0))
                        .sum();
                totaisMensais.put(referencia, totalMes);
                if (totalMes > 0) {
                    log.trace("Total mensal {}: R$ {}", referencia, totalMes);
                }
            }
        }
        log.debug("Totais mensais calculados para {} referências", totaisMensais.size());

        // Calcular total geral
        log.debug("Calculando total geral");
        double totalGeral = rubricas.stream()
                .mapToDouble(row -> row.getTotal() != null ? row.getTotal() : 0.0)
                .sum();
        log.info("Total geral calculado: R$ {}", totalGeral);

        ConsolidatedResponse response = ConsolidatedResponse.builder()
                .cpf(person.getCpf())
                .nome(person.getNome())
                .anos(new TreeSet<>(allYears))
                .meses(meses)
                .rubricas(rubricas)
                .totaisMensais(totaisMensais)
                .totalGeral(totalGeral)
                .build();

        log.info("=== buildConsolidatedResponse() CONCLUÍDO ===");
        log.info("Resposta consolidada criada com sucesso:");
        log.info("  - CPF: {}", response.getCpf());
        log.info("  - Nome: {}", response.getNome());
        log.info("  - Anos: {}", response.getAnos());
        log.info("  - Rubricas: {}", response.getRubricas().size());
        log.info("  - Total geral: R$ {}", response.getTotalGeral());

        return response;
    }
}
