package com.example.moduleai.conversation;

import java.util.List;

public record ChatConversationListView(List<ChatConversationView> items, String nextCursor) {}
