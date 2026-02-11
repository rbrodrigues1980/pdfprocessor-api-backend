package br.com.verticelabs.pdfprocessor.interfaces.rubricas;

import br.com.verticelabs.pdfprocessor.application.rubricas.RubricaUseCase;
import br.com.verticelabs.pdfprocessor.domain.exceptions.RubricaDuplicadaException;
import br.com.verticelabs.pdfprocessor.domain.exceptions.RubricaNotFoundException;
import br.com.verticelabs.pdfprocessor.domain.model.Rubrica;
import br.com.verticelabs.pdfprocessor.interfaces.rubricas.dto.CreateRubricaRequest;
import br.com.verticelabs.pdfprocessor.interfaces.rubricas.dto.UpdateRubricaRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Arrays;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("RubricaController - Testes Unitários")
class RubricaControllerTest {

    @Mock
    private RubricaUseCase rubricaUseCase;

    @InjectMocks
    private RubricaController rubricaController;

    private WebTestClient webTestClient;

    @BeforeEach
    void setUp() {
        webTestClient = WebTestClient.bindToController(rubricaController).build();
    }

    @Test
    @DisplayName("Deve criar uma rubrica com sucesso - 201 CREATED")
    void deveCriarRubricaComSucesso() throws Exception {
        // Arrange
        CreateRubricaRequest request = new CreateRubricaRequest();
        request.setCodigo("001");
        request.setDescricao("Salário Base");
        request.setCategoria("Administrativa");

        Rubrica rubricaCriada = Rubrica.builder()
                .id("123")
                .codigo("001")
                .descricao("Salário Base")
                .categoria("Administrativa")
                .ativo(true)
                .build();

        when(rubricaUseCase.criar(any(CreateRubricaRequest.class)))
                .thenReturn(Mono.just(rubricaCriada));

        // Act & Assert
        webTestClient.post()
                .uri("/rubricas")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus().isCreated()
                .expectHeader().contentType(MediaType.APPLICATION_JSON)
                .expectBody()
                .jsonPath("$.id").isEqualTo("123")
                .jsonPath("$.codigo").isEqualTo("001")
                .jsonPath("$.descricao").isEqualTo("Salário Base")
                .jsonPath("$.categoria").isEqualTo("Administrativa")
                .jsonPath("$.ativo").isEqualTo(true);
    }

    @Test
    @DisplayName("Deve retornar 409 CONFLICT quando tentar criar rubrica duplicada")
    void deveRetornar409QuandoRubricaDuplicada() throws Exception {
        // Arrange
        CreateRubricaRequest request = new CreateRubricaRequest();
        request.setCodigo("001");
        request.setDescricao("Salário Base");

        when(rubricaUseCase.criar(any(CreateRubricaRequest.class)))
                .thenReturn(Mono.error(new RubricaDuplicadaException("001")));

        // Act & Assert
        webTestClient.post()
                .uri("/rubricas")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus().isEqualTo(HttpStatus.CONFLICT)
                .expectHeader().contentType(MediaType.APPLICATION_JSON)
                .expectBody()
                .jsonPath("$.status").isEqualTo(409)
                .jsonPath("$.error").exists();
    }

    @Test
    @DisplayName("Deve listar todas as rubricas quando apenasAtivas=false")
    void deveListarTodasRubricas() {
        // Arrange
        Rubrica rubrica1 = Rubrica.builder()
                .id("1")
                .codigo("001")
                .descricao("Salário Base")
                .ativo(true)
                .build();

        Rubrica rubrica2 = Rubrica.builder()
                .id("2")
                .codigo("002")
                .descricao("Vale Transporte")
                .ativo(false)
                .build();

        List<Rubrica> rubricas = Arrays.asList(rubrica1, rubrica2);

        when(rubricaUseCase.listarTodas())
                .thenReturn(Flux.fromIterable(rubricas));

        // Act & Assert
        webTestClient.get()
                .uri("/rubricas?apenasAtivas=false")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentType(MediaType.APPLICATION_JSON)
                .expectBodyList(Rubrica.class)
                .hasSize(2)
                .contains(rubrica1, rubrica2);
    }

    @Test
    @DisplayName("Deve listar apenas rubricas ativas quando apenasAtivas=true")
    void deveListarApenasRubricasAtivas() {
        // Arrange
        Rubrica rubrica1 = Rubrica.builder()
                .id("1")
                .codigo("001")
                .descricao("Salário Base")
                .ativo(true)
                .build();

        Rubrica rubrica2 = Rubrica.builder()
                .id("2")
                .codigo("002")
                .descricao("Vale Transporte")
                .ativo(true)
                .build();

        List<Rubrica> rubricasAtivas = Arrays.asList(rubrica1, rubrica2);

        when(rubricaUseCase.listarAtivas())
                .thenReturn(Flux.fromIterable(rubricasAtivas));

        // Act & Assert
        webTestClient.get()
                .uri("/rubricas?apenasAtivas=true")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentType(MediaType.APPLICATION_JSON)
                .expectBodyList(Rubrica.class)
                .hasSize(2)
                .contains(rubrica1, rubrica2);
    }

    @Test
    @DisplayName("Deve listar todas as rubricas quando apenasAtivas não for informado (default=false)")
    void deveListarTodasQuandoParametroNaoInformado() {
        // Arrange
        Rubrica rubrica1 = Rubrica.builder()
                .id("1")
                .codigo("001")
                .descricao("Salário Base")
                .ativo(true)
                .build();

        when(rubricaUseCase.listarTodas())
                .thenReturn(Flux.just(rubrica1));

        // Act & Assert
        webTestClient.get()
                .uri("/rubricas")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentType(MediaType.APPLICATION_JSON)
                .expectBodyList(Rubrica.class)
                .hasSize(1)
                .contains(rubrica1);
    }

    @Test
    @DisplayName("Deve buscar rubrica por código com sucesso - 200 OK")
    void deveBuscarRubricaPorCodigoComSucesso() {
        // Arrange
        Rubrica rubrica = Rubrica.builder()
                .id("123")
                .codigo("001")
                .descricao("Salário Base")
                .categoria("Administrativa")
                .ativo(true)
                .build();

        when(rubricaUseCase.buscarPorCodigo("001"))
                .thenReturn(Mono.just(rubrica));

        // Act & Assert
        webTestClient.get()
                .uri("/rubricas/001")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentType(MediaType.APPLICATION_JSON)
                .expectBody()
                .jsonPath("$.id").isEqualTo("123")
                .jsonPath("$.codigo").isEqualTo("001")
                .jsonPath("$.descricao").isEqualTo("Salário Base")
                .jsonPath("$.categoria").isEqualTo("Administrativa")
                .jsonPath("$.ativo").isEqualTo(true);
    }

    @Test
    @DisplayName("Deve retornar 404 NOT_FOUND quando rubrica não encontrada por código")
    void deveRetornar404QuandoRubricaNaoEncontrada() {
        // Arrange
        when(rubricaUseCase.buscarPorCodigo("999"))
                .thenReturn(Mono.error(new RubricaNotFoundException("999")));

        // Act & Assert
        webTestClient.get()
                .uri("/rubricas/999")
                .exchange()
                .expectStatus().isNotFound()
                .expectHeader().contentType(MediaType.APPLICATION_JSON)
                .expectBody()
                .jsonPath("$.status").isEqualTo(404)
                .jsonPath("$.error").exists();
    }

    @Test
    @DisplayName("Deve atualizar rubrica com sucesso - 200 OK")
    void deveAtualizarRubricaComSucesso() throws Exception {
        // Arrange
        UpdateRubricaRequest request = new UpdateRubricaRequest();
        request.setDescricao("Salário Base Atualizado");
        request.setCategoria("Administrativa");

        Rubrica rubricaAtualizada = Rubrica.builder()
                .id("123")
                .codigo("001")
                .descricao("Salário Base Atualizado")
                .categoria("Administrativa")
                .ativo(true)
                .build();

        when(rubricaUseCase.atualizar(anyString(), any(UpdateRubricaRequest.class)))
                .thenReturn(Mono.just(rubricaAtualizada));

        // Act & Assert
        webTestClient.put()
                .uri("/rubricas/001")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentType(MediaType.APPLICATION_JSON)
                .expectBody()
                .jsonPath("$.id").isEqualTo("123")
                .jsonPath("$.codigo").isEqualTo("001")
                .jsonPath("$.descricao").isEqualTo("Salário Base Atualizado")
                .jsonPath("$.categoria").isEqualTo("Administrativa");
    }

    @Test
    @DisplayName("Deve retornar 404 NOT_FOUND ao tentar atualizar rubrica inexistente")
    void deveRetornar404AoAtualizarRubricaInexistente() throws Exception {
        // Arrange
        UpdateRubricaRequest request = new UpdateRubricaRequest();
        request.setDescricao("Salário Base Atualizado");

        when(rubricaUseCase.atualizar(anyString(), any(UpdateRubricaRequest.class)))
                .thenReturn(Mono.error(new RubricaNotFoundException("999")));

        // Act & Assert
        webTestClient.put()
                .uri("/rubricas/999")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus().isNotFound()
                .expectHeader().contentType(MediaType.APPLICATION_JSON)
                .expectBody()
                .jsonPath("$.status").isEqualTo(404)
                .jsonPath("$.error").exists();
    }

    @Test
    @DisplayName("Deve desativar rubrica com sucesso - 200 OK")
    void deveDesativarRubricaComSucesso() {
        // Arrange
        when(rubricaUseCase.desativar("001"))
                .thenReturn(Mono.empty());

        // Act & Assert
        webTestClient.delete()
                .uri("/rubricas/001")
                .exchange()
                .expectStatus().isOk();
    }

    @Test
    @DisplayName("Deve retornar 404 NOT_FOUND ao tentar desativar rubrica inexistente")
    void deveRetornar404AoDesativarRubricaInexistente() {
        // Arrange
        when(rubricaUseCase.desativar("999"))
                .thenReturn(Mono.error(new RubricaNotFoundException("999")));

        // Act & Assert
        webTestClient.delete()
                .uri("/rubricas/999")
                .exchange()
                .expectStatus().isNotFound()
                .expectHeader().contentType(MediaType.APPLICATION_JSON)
                .expectBody()
                .jsonPath("$.status").isEqualTo(404)
                .jsonPath("$.error").exists();
    }

    @Test
    @DisplayName("Deve retornar lista vazia quando não houver rubricas")
    void deveRetornarListaVaziaQuandoNaoHouverRubricas() {
        // Arrange
        when(rubricaUseCase.listarTodas())
                .thenReturn(Flux.empty());

        // Act & Assert
        webTestClient.get()
                .uri("/rubricas")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentType(MediaType.APPLICATION_JSON)
                .expectBodyList(Rubrica.class)
                .hasSize(0);
    }

    @Test
    @DisplayName("Deve retornar lista vazia quando não houver rubricas ativas")
    void deveRetornarListaVaziaQuandoNaoHouverRubricasAtivas() {
        // Arrange
        when(rubricaUseCase.listarAtivas())
                .thenReturn(Flux.empty());

        // Act & Assert
        webTestClient.get()
                .uri("/rubricas?apenasAtivas=true")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentType(MediaType.APPLICATION_JSON)
                .expectBodyList(Rubrica.class)
                .hasSize(0);
    }
}

