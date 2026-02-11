package br.com.verticelabs.pdfprocessor.infrastructure.mongodb;

import br.com.verticelabs.pdfprocessor.domain.model.IrParametrosAnuais;
import br.com.verticelabs.pdfprocessor.domain.model.IrTabelaTributacao;
import br.com.verticelabs.pdfprocessor.domain.repository.IrTributacaoRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class MongoIrTributacaoRepositoryAdapter implements IrTributacaoRepository {

    private final SpringDataIrTabelaTributacaoRepository faixaRepository;
    private final SpringDataIrParametrosAnuaisRepository parametrosRepository;
    private final ReactiveMongoTemplate mongoTemplate;

    // ========== Faixas de Tributação ==========

    @Override
    public Mono<IrTabelaTributacao> saveFaixa(IrTabelaTributacao faixa) {
        if (faixa.getId() == null) {
            faixa.setId(UUID.randomUUID().toString());
            faixa.setCreatedAt(LocalDateTime.now());
        }
        faixa.setUpdatedAt(LocalDateTime.now());
        return faixaRepository.save(faixa);
    }

    @Override
    public Flux<IrTabelaTributacao> findFaixas(Integer anoCalendario, String tipoIncidencia) {
        return faixaRepository.findByAnoCalendarioAndTipoIncidenciaOrderByFaixa(
                anoCalendario, tipoIncidencia);
    }

    @Override
    public Flux<Integer> findAnosDisponiveis(String tipoIncidencia) {
        Query query = new Query(Criteria.where("tipoIncidencia").is(tipoIncidencia));

        return mongoTemplate.findDistinct(query, "anoCalendario",
                IrTabelaTributacao.class, Integer.class)
                .sort();
    }

    @Override
    public Mono<Void> deleteFaixas(Integer anoCalendario, String tipoIncidencia) {
        return faixaRepository.deleteByAnoCalendarioAndTipoIncidencia(
                anoCalendario, tipoIncidencia);
    }

    // ========== Parâmetros Anuais ==========

    @Override
    public Mono<IrParametrosAnuais> saveParametros(IrParametrosAnuais parametros) {
        if (parametros.getId() == null) {
            parametros.setId(UUID.randomUUID().toString());
            parametros.setCreatedAt(LocalDateTime.now());
        }
        parametros.setUpdatedAt(LocalDateTime.now());
        return parametrosRepository.save(parametros);
    }

    @Override
    public Mono<IrParametrosAnuais> findParametros(Integer anoCalendario, String tipoIncidencia) {
        return parametrosRepository.findByAnoCalendarioAndTipoIncidencia(
                anoCalendario, tipoIncidencia);
    }

    @Override
    public Mono<Void> deleteParametros(Integer anoCalendario, String tipoIncidencia) {
        return parametrosRepository.deleteByAnoCalendarioAndTipoIncidencia(
                anoCalendario, tipoIncidencia);
    }
}
