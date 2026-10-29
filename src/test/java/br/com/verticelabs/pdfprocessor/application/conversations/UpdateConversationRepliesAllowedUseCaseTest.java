package br.com.verticelabs.pdfprocessor.application.conversations;

import br.com.verticelabs.pdfprocessor.domain.model.Conversation;
import br.com.verticelabs.pdfprocessor.domain.repository.ConversationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import reactor.util.context.Context;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("UpdateConversationRepliesAllowedUseCase")
class UpdateConversationRepliesAllowedUseCaseTest {

    @Mock
    ConversationRepository conversationRepository;

    UpdateConversationRepliesAllowedUseCase useCase;

    Conversation conversation;

    @BeforeEach
    void setUp() {
        useCase = new UpdateConversationRepliesAllowedUseCase(conversationRepository);
        conversation = Conversation.builder()
                .id("conv-1")
                .title("Aviso")
                .createdByUserId("sender-1")
                .participantUserIds(new ArrayList<>(List.of("sender-1", "recipient-1")))
                .repliesAllowed(true)
                .build();
    }

    @Test
    @DisplayName("autor pode desabilitar respostas")
    void authorCanDisable() {
        when(conversationRepository.findById("conv-1")).thenReturn(Mono.just(conversation));
        when(conversationRepository.save(any())).thenAnswer(inv -> Mono.just(inv.getArgument(0)));

        StepVerifier.create(useCase.execute("conv-1", false)
                        .contextWrite(securityContext("sender-1")))
                .assertNext(c -> assertFalse(c.areRepliesAllowed()))
                .verifyComplete();
    }

    @Test
    @DisplayName("autor pode reabilitar respostas")
    void authorCanEnable() {
        conversation.setRepliesAllowed(false);
        when(conversationRepository.findById("conv-1")).thenReturn(Mono.just(conversation));
        when(conversationRepository.save(any())).thenAnswer(inv -> Mono.just(inv.getArgument(0)));

        StepVerifier.create(useCase.execute("conv-1", true)
                        .contextWrite(securityContext("sender-1")))
                .assertNext(c -> assertTrue(c.areRepliesAllowed()))
                .verifyComplete();
    }

    @Test
    @DisplayName("não-autor recebe SecurityException")
    void nonAuthorForbidden() {
        when(conversationRepository.findById("conv-1")).thenReturn(Mono.just(conversation));

        StepVerifier.create(useCase.execute("conv-1", false)
                        .contextWrite(securityContext("recipient-1")))
                .expectError(SecurityException.class)
                .verify();

        verify(conversationRepository, never()).save(any());
    }

    private static Context securityContext(String userId) {
        var auth = new UsernamePasswordAuthenticationToken(
                userId, "n/a", List.of(new SimpleGrantedAuthority("ROLE_TENANT_USER")));
        return ReactiveSecurityContextHolder.withAuthentication(auth);
    }
}
