package com.aiguard.ai.gateway.chat.dto;

import java.util.List;

public record ChatRequestDto(
        String sessionId,
        String userId,
        List<ChatMessageDto> messages
) {}