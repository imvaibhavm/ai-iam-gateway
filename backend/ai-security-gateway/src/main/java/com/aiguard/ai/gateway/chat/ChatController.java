package com.aiguard.ai.gateway.chat;

import com.aiguard.ai.gateway.chat.dto.ChatRequestDto;
import com.aiguard.ai.gateway.chat.dto.ChatResponseDto;
import com.aiguard.ai.gateway.chat.service.ChatService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import com.aiguard.ai.gateway.identity.IdentityResolver;
import org.springframework.security.core.Authentication;

@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
public class ChatController {

    private final ChatService chatService;
    private final IdentityResolver identityResolver;

    // Non-streaming endpoint (normal JSON reply)
    @PostMapping
    public ChatResponseDto chat(@RequestBody ChatRequestDto request, Authentication auth) {

        String lastUserMsg = (request.messages() == null || request.messages().isEmpty())
                ? ""
                : request.messages().get(request.messages().size() - 1).content();

        String reply = chatService.reply(identityResolver.require(auth), lastUserMsg);

        return new ChatResponseDto(request.sessionId(), reply);
    }

    // Streaming SSE endpoint
    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter chatStream(@RequestBody ChatRequestDto request, Authentication auth) {

        SseEmitter emitter = new SseEmitter(0L); // no timeout

        String lastUserMsg = (request.messages() == null || request.messages().isEmpty())
                ? ""
                : request.messages().get(request.messages().size() - 1).content();

        var identity = identityResolver.require(auth);
        Thread.startVirtualThread(() -> {
            try {
                chatService.streamReply(identity, lastUserMsg, token -> {
                    try {
                        emitter.send(SseEmitter.event().name("token").data(token));
                    } catch (IOException e) {
                        emitter.completeWithError(e);
                    }
                });

                emitter.send(SseEmitter.event().name("done").data("[DONE]"));
                emitter.complete();

            } catch (Exception ex) {
                emitter.completeWithError(ex);
            }
        });

        return emitter;
    }
}
