package br.com.verticelabs.pdfprocessor.interfaces.api;

import br.com.verticelabs.pdfprocessor.application.tributacao.IrTributacaoService;
import br.com.verticelabs.pdfprocessor.domain.model.IrParametrosAnuais;
import br.com.verticelabs.pdfprocessor.domain.model.IrTabelaTributacao;
import br.com.verticelabs.pdfprocessor.interfaces.api.dto.FaixaTributacaoDTO;
import br.com.verticelabs.pdfprocessor.interfaces.api.dto.ParametrosAnuaisDTO;
import br.com.verticelabs.pdfprocessor.interfaces.api.dto.TributacaoAnoDTO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Controller para gestão de tabelas de tributação IRPF.
 */
@Slf4j
@RestController
@RequestMapping("/tributacao")
@RequiredArgsConstructor
@Tag(name = "Tributação IRPF", description = "Gestão de tabelas de tributação (Receita Federal)")
public class IrTributacaoController {

    private final IrTributacaoService tributacaoService;

    /**
     * Lista anos disponíveis para um tipo de incidência.
     */
    @GetMapping("/anos")
    @Operation(summary = "Listar anos disponíveis")
    public Flux<Integer> listarAnos(
            @RequestParam(defaultValue = "ANUAL") @Parameter(description = "Tipo: ANUAL, MENSAL ou PLR") String tipo) {
        return tributacaoService.buscarAnosDisponiveis(tipo.toUpperCase());
    }

    /**
     * Busca tributação completa de um ano (faixas + parâmetros).
     */
    @GetMapping("/{ano}")
    @Operation(summary = "Buscar tributação de um ano")
    public Mono<ResponseEntity<TributacaoAnoDTO>> buscarAno(
            @PathVariable Integer ano,
            @RequestParam(defaultValue = "ANUAL") String tipo) {

        String tipoUpper = tipo.toUpperCase();

        Mono<List<FaixaTributacaoDTO>> faixasMono = tributacaoService.buscarFaixas(ano, tipoUpper)
                .map(this::toFaixaDTO)
                .collectList();

        Mono<ParametrosAnuaisDTO> parametrosMono = tributacaoService.buscarParametros(ano, tipoUpper)
                .map(this::toParametrosDTO)
                .defaultIfEmpty(ParametrosAnuaisDTO.builder().build());

        return Mono.zip(faixasMono, parametrosMono)
                .map(tuple -> {
                    List<FaixaTributacaoDTO> faixas = tuple.getT1();
                    ParametrosAnuaisDTO params = tuple.getT2();

                    if (faixas.isEmpty()) {
                        return ResponseEntity.notFound().<TributacaoAnoDTO>build();
                    }

                    TributacaoAnoDTO dto = TributacaoAnoDTO.builder()
                            .anoCalendario(ano)
                            .tipoIncidencia(tipoUpper)
                            .faixas(faixas)
                            .parametros(params)
                            .build();

                    return ResponseEntity.ok(dto);
                });
    }

    /**
     * Cria ou atualiza tributação de um ano.
     */
    @PostMapping("/{ano}")
    @Operation(summary = "Criar ou atualizar tributação de um ano")
    public Mono<ResponseEntity<TributacaoAnoDTO>> salvarAno(
            @PathVariable Integer ano,
            @RequestParam(defaultValue = "ANUAL") String tipo,
            @RequestBody TributacaoAnoDTO request) {

        String tipoUpper = tipo.toUpperCase();

        log.info("Salvando tributação: ano={}, tipo={}, faixas={}",
                ano, tipoUpper, request.getFaixas().size());

        // Primeiro, remover dados existentes do ano
        return tributacaoService.removerAno(ano, tipoUpper)
                .then(Mono.defer(() -> {
                    // Salvar faixas
                    List<IrTabelaTributacao> faixas = request.getFaixas().stream()
                            .map(dto -> toFaixaEntity(dto, ano, tipoUpper))
                            .collect(Collectors.toList());

                    return tributacaoService.salvarFaixas(faixas).collectList();
                }))
                .flatMap(savedFaixas -> {
                    // Salvar parâmetros (se fornecidos)
                    if (request.getParametros() != null) {
                        IrParametrosAnuais params = toParametrosEntity(request.getParametros(), ano, tipoUpper);
                        return tributacaoService.salvarParametros(params).thenReturn(savedFaixas);
                    }
                    return Mono.just(savedFaixas);
                })
                .then(buscarAno(ano, tipo));
    }

    /**
     * Remove tributação de um ano.
     */
    @DeleteMapping("/{ano}")
    @Operation(summary = "Remover tributação de um ano")
    public Mono<ResponseEntity<Void>> removerAno(
            @PathVariable Integer ano,
            @RequestParam(defaultValue = "ANUAL") String tipo) {

        String tipoUpper = tipo.toUpperCase();

        log.info("Removendo tributação: ano={}, tipo={}", ano, tipoUpper);

        return tributacaoService.removerAno(ano, tipoUpper)
                .then(Mono.just(ResponseEntity.noContent().<Void>build()));
    }

    // ========== Mapeamentos ==========

    private FaixaTributacaoDTO toFaixaDTO(IrTabelaTributacao entity) {
        return FaixaTributacaoDTO.builder()
                .faixa(entity.getFaixa())
                .limiteInferior(entity.getLimiteInferior())
                .limiteSuperior(entity.getLimiteSuperior())
                .aliquota(entity.getAliquota())
                .deducao(entity.getDeducao())
                .descricao(entity.getDescricao())
                .build();
    }

    private IrTabelaTributacao toFaixaEntity(FaixaTributacaoDTO dto, Integer ano, String tipo) {
        return IrTabelaTributacao.builder()
                .anoCalendario(ano)
                .tipoIncidencia(tipo)
                .faixa(dto.getFaixa())
                .limiteInferior(dto.getLimiteInferior())
                .limiteSuperior(dto.getLimiteSuperior())
                .aliquota(dto.getAliquota())
                .deducao(dto.getDeducao())
                .descricao(dto.getDescricao())
                .build();
    }

    private ParametrosAnuaisDTO toParametrosDTO(IrParametrosAnuais entity) {
        return ParametrosAnuaisDTO.builder()
                .deducaoDependente(entity.getDeducaoDependente())
                .limiteInstrucao(entity.getLimiteInstrucao())
                .limiteDescontoSimplificado(entity.getLimiteDescontoSimplificado())
                .isencao65Anos(entity.getIsencao65Anos())
                .build();
    }

    private IrParametrosAnuais toParametrosEntity(ParametrosAnuaisDTO dto, Integer ano, String tipo) {
        return IrParametrosAnuais.builder()
                .anoCalendario(ano)
                .tipoIncidencia(tipo)
                .deducaoDependente(dto.getDeducaoDependente())
                .limiteInstrucao(dto.getLimiteInstrucao())
                .limiteDescontoSimplificado(dto.getLimiteDescontoSimplificado())
                .isencao65Anos(dto.getIsencao65Anos())
                .build();
    }
}
