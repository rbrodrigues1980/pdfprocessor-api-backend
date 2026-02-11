package br.com.verticelabs.pdfprocessor.interfaces.users;

import br.com.verticelabs.pdfprocessor.application.users.*;
import br.com.verticelabs.pdfprocessor.domain.model.User;
import br.com.verticelabs.pdfprocessor.domain.repository.TenantRepository;
import br.com.verticelabs.pdfprocessor.interfaces.users.UserMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;

import java.util.Collections;
import java.util.List;
import java.util.Set;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserControllerTest {

        @Mock
        private CreateUserUseCase createUserUseCase;
        @Mock
        private ListUsersUseCase listUsersUseCase;
        @Mock
        private GetUserByIdUseCase getUserByIdUseCase;
        @Mock
        private UpdateUserUseCase updateUserUseCase;
        @Mock
        private DeactivateUserUseCase deactivateUserUseCase;
        @Mock
        private ActivateUserUseCase activateUserUseCase;
        @Mock
        private ChangePasswordUseCase changePasswordUseCase;
        @Mock
        private TenantRepository tenantRepository;
        @Spy
        private UserMapper userMapper = new UserMapper();

        @InjectMocks
        private UserController userController;

        private WebTestClient webTestClient;

        @BeforeEach
        void setUp() {
                webTestClient = WebTestClient.bindToController(userController).build();
        }

        @Test
        @DisplayName("Deve reproduzir UnsupportedOperationException ao listar usuários")
        void deveReproduzirErroAoListarUsuarios() {
                // Arrange
                User user = User.builder()
                                .id("1")
                                .nome("Test User")
                                .email("test@example.com")
                                .roles(Set.of("TENANT_USER")) // Set imutável
                                .ativo(true)
                                .build();

                // Simular o retorno do UseCase com uma lista contendo o usuário
                ListUsersUseCase.ListUsersResult result = new ListUsersUseCase.ListUsersResult(
                                List.of(user), // Lista imutável
                                1L,
                                1,
                                0,
                                20);

                when(listUsersUseCase.execute(any(), any(), any(), any(), any(), anyInt(), anyInt()))
                                .thenReturn(Mono.just(result));

                // Act & Assert
                webTestClient.get()
                                .uri("/users?page=0&size=20")
                                .exchange()
                                .expectStatus().isOk()
                                .expectBody()
                                .jsonPath("$.content[0].nome").isEqualTo("Test User");
        }

        @Test
        @DisplayName("Deve serializar UserResponse com roles imutáveis sem erro")
        void deveSerializarUserResponseComRolesImutaveis() throws Exception {
                // Arrange
                br.com.verticelabs.pdfprocessor.interfaces.users.dto.UserResponse response = br.com.verticelabs.pdfprocessor.interfaces.users.dto.UserResponse
                                .builder()
                                .id("1")
                                .nome("Test User")
                                .roles(Set.of("TENANT_USER")) // Set imutável
                                .build();

                com.fasterxml.jackson.databind.ObjectMapper objectMapper = new com.fasterxml.jackson.databind.ObjectMapper();
                objectMapper.registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());

                // Act
                String json = objectMapper.writeValueAsString(response);

                // Assert
                org.junit.jupiter.api.Assertions.assertTrue(json.contains("TENANT_USER"));
        }
}
