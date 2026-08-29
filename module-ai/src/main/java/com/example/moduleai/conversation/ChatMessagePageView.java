package com.example.moduleai.conversation;

import java.util.List;

public record ChatMessagePageView(List<ChatMessageView> items, Long nextBeforeSequence) {}
