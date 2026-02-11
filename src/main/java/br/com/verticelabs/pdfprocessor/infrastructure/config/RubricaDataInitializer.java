package br.com.verticelabs.pdfprocessor.infrastructure.config;

import br.com.verticelabs.pdfprocessor.domain.model.Rubrica;
import br.com.verticelabs.pdfprocessor.domain.repository.RubricaRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Arrays;
import java.util.List;

@Slf4j
@Component
@Order(2) // Executa depois do DatabaseInitializer
@RequiredArgsConstructor
public class RubricaDataInitializer implements CommandLineRunner {

    private final RubricaRepository rubricaRepository;

    private static final List<Rubrica> RUBRICAS_INICIAIS = Arrays.asList(
            Rubrica.builder().tenantId("GLOBAL").codigo("3362").descricao("REP. TAXA ADMINISTRATIVA - SALDADO").categoria("Administrativa").ativo(true).build(),
            Rubrica.builder().tenantId("GLOBAL").codigo("3394").descricao("REP TAXA ADMINISTRATIVA BUA").categoria("Administrativa").ativo(true).build(),
            Rubrica.builder().tenantId("GLOBAL").codigo("3396").descricao("REP TAXA ADMINISTRATIVA BUA NOVO PLANO").categoria("Administrativa").ativo(true).build(),
            Rubrica.builder().tenantId("GLOBAL").codigo("3430").descricao("REP CONTRIBUIÇÃO EXTRAORDINÁRIA 2014").categoria("Extraordinária").ativo(true).build(),
            Rubrica.builder().tenantId("GLOBAL").codigo("3477").descricao("REP CONTRIBUIÇÃO EXTRAORDINÁRIA 2015").categoria("Extraordinária").ativo(true).build(),
            Rubrica.builder().tenantId("GLOBAL").codigo("3513").descricao("REP CONTRIBUIÇÃO EXTRAORDINÁRIA 2016").categoria("Extraordinária").ativo(true).build(),
            Rubrica.builder().tenantId("GLOBAL").codigo("3961").descricao("REP. TAXA ADMINISTRATIVA - NP").categoria("Administrativa").ativo(true).build(),
            Rubrica.builder().tenantId("GLOBAL").codigo("4236").descricao("FUNCEF NOVO PLANO").categoria("Contribuição").ativo(true).build(),
            Rubrica.builder().tenantId("GLOBAL").codigo("4362").descricao("TAXA ADMINISTRATIVA SALDADO").categoria("Administrativa").ativo(true).build(),
            Rubrica.builder().tenantId("GLOBAL").codigo("4364").descricao("TAXA ADMINISTRATIVA SALDADO 13º SAL").categoria("Administrativa").ativo(true).build(),
            Rubrica.builder().tenantId("GLOBAL").codigo("4369").descricao("FUNCEF NOVO PLANO GRAT NATAL").categoria("Contribuição").ativo(true).build(),
            Rubrica.builder().tenantId("GLOBAL").codigo("4412").descricao("FUNCEF CONTRIB EQU SALDADO 01").categoria("Contribuição").ativo(true).build(),
            Rubrica.builder().tenantId("GLOBAL").codigo("4416").descricao("FUNCEF CONTRIB EQU SALDADO 01 GRT NATAL").categoria("Contribuição").ativo(true).build(),
            Rubrica.builder().tenantId("GLOBAL").codigo("4430").descricao("CONTRIBUIÇÃO EXTRAORDINÁRIA 2014").categoria("Extraordinária").ativo(true).build(),
            Rubrica.builder().tenantId("GLOBAL").codigo("4432").descricao("FUNCEF CONTRIB EQU SALDADO 02").categoria("Contribuição").ativo(true).build(),
            Rubrica.builder().tenantId("GLOBAL").codigo("4436").descricao("FUNCEF CONTRIB EQU SALDADO 02 GRT NATAL").categoria("Contribuição").ativo(true).build(),
            Rubrica.builder().tenantId("GLOBAL").codigo("4443").descricao("FUNCEF CONTRIB EQU SALDADO 03").categoria("Contribuição").ativo(true).build(),
            Rubrica.builder().tenantId("GLOBAL").codigo("4444").descricao("FUNCEF CONTRIB EQU SALDADO 03 GRT NATAL").categoria("Contribuição").ativo(true).build(),
            Rubrica.builder().tenantId("GLOBAL").codigo("4459").descricao("CONTRIBUIÇÃO EXTRAORDINÁRIA ABONO ANUAL 2014").categoria("Extraordinária").ativo(true).build(),
            Rubrica.builder().tenantId("GLOBAL").codigo("4477").descricao("CONTRIBUIÇÃO EXTRAORDINÁRIA 2015").categoria("Extraordinária").ativo(true).build(),
            Rubrica.builder().tenantId("GLOBAL").codigo("4482").descricao("CONTRIBUIÇÃO EXTRAORDINÁRIA ABONO ANUAL 2015").categoria("Extraordinária").ativo(true).build(),
            Rubrica.builder().tenantId("GLOBAL").codigo("4513").descricao("CONTRIBUIÇÃO EXTRAORDINÁRIA 2016").categoria("Extraordinária").ativo(true).build(),
            Rubrica.builder().tenantId("GLOBAL").codigo("4514").descricao("CONTRIBUIÇÃO EXTRAORDINÁRIA ABONO ANUAL 2016").categoria("Extraordinária").ativo(true).build(),
            Rubrica.builder().tenantId("GLOBAL").codigo("4961").descricao("TAXA ADMINISTRATIVA NOVO PLANO").categoria("Administrativa").ativo(true).build()
    );

    @Override
    public void run(String... args) {
        log.info("Inicializando rubricas padrão...");

        Flux.fromIterable(RUBRICAS_INICIAIS)
                .flatMap(rubrica ->
                        rubricaRepository.existsByCodigo(rubrica.getCodigo(), "GLOBAL")
                                .flatMap(exists -> {
                                    if (Boolean.TRUE.equals(exists)) {
                                        log.debug("Rubrica {} já existe, pulando...", rubrica.getCodigo());
                                        return Mono.empty();
                                    }
                                    log.info("Criando rubrica: {} - {}", rubrica.getCodigo(), rubrica.getDescricao());
                                    return rubricaRepository.save(rubrica);
                                })
                )
                .then()
                .doOnSuccess(v -> log.info("Inicialização de rubricas concluída. Total: {}", RUBRICAS_INICIAIS.size()))
                .doOnError(error -> log.error("Erro ao inicializar rubricas", error))
                .subscribe();
    }
}

