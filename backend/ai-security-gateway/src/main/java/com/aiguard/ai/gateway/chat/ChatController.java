package com.aiguard.ai.gateway.chat;

import com.aiguard.ai.gateway.chat.dto.ChatRequestDto;
import com.aiguard.ai.gateway.chat.dto.ChatResponseDto;
import com.aiguard.ai.gateway.chat.service.ChatService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;

@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
public class ChatController {

    private final ChatService chatService;

    // Non-streaming endpoint (normal JSON reply)
    @PostMapping
    public ChatResponseDto chat(@RequestBody ChatRequestDto request) {

        String lastUserMsg = (request.messages() == null || request.messages().isEmpty())
                ? ""
                : request.messages().get(request.messages().size() - 1).content();

        String reply = chatService.reply(request.userId(), lastUserMsg);

        return new ChatResponseDto(request.sessionId(), reply);
    }

    // Streaming SSE endpoint
    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter chatStream(@RequestBody ChatRequestDto request) {

        SseEmitter emitter = new SseEmitter(0L); // no timeout

        String lastUserMsg = (request.messages() == null || request.messages().isEmpty())
                ? ""
                : request.messages().get(request.messages().size() - 1).content();

        new Thread(() -> {
            try {
                chatService.streamReply(request.userId(), lastUserMsg, token -> {
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
        }).start();

        return emitter;
    }
}
