package br.com.verticelabs.pdfprocessor.application.conversations;

import br.com.verticelabs.pdfprocessor.domain.model.Conversation;
import br.com.verticelabs.pdfprocessor.domain.repository.ConversationRepository;
import br.com.verticelabs.pdfprocessor.infrastructure.security.ReactiveSecurityContextHelper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ListConversationsUseCase {

    private final ConversationRepository conversationRepository;

    public Mono<ListResult> execute(boolean unreadOnly, int page, int size) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), 100);
        return ReactiveSecurityContextHelper.getUserId()
                .flatMap(userId -> conversationRepository
                        .findByParticipantUserId(userId, false, 0, 500)
                        .collectList()
                        .map(all -> {
                            List<Conversation> filtered = all.stream()
                                    .filter(c -> !unreadOnly || c.isUnreadFor(userId))
                                    .collect(java.util.stream.Collectors.toCollection(ArrayList::new));
                            long unreadCount = all.stream()
                                    .filter(c -> c.isUnreadFor(userId))
                                    .count();
                            int from = Math.min(safePage * safeSize, filtered.size());
                            int to = Math.min(from + safeSize, filtered.size());
                            return new ListResult(
                                    new ArrayList<>(filtered.subList(from, to)),
                                    filtered.size(),
                                    unreadCount,
                                    safePage,
                                    safeSize,
                                    userId);
                        }));
    }

    public record ListResult(
            List<Conversation> items,
            long total,
            long unreadCount,
            int page,
            int size,
            String currentUserId) {
    }
}
