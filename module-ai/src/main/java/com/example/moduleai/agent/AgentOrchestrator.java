package com.example.moduleai.agent;

import com.example.moduleai.chat.ChatResponseMapper;
import com.example.moduleai.chat.ChatService;
import com.example.moduleai.dto.ChatRequest;
import com.example.moduleai.dto.RagChatResponse;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

@Service
public class AgentOrchestrator {

  private final IntentRouter intentRouter;
  private final ChatService chatService;
  private final ChatResponseMapper chatResponseMapper;
  private final AgentToolOrchestrator agentToolOrchestrator;

  public AgentOrchestrator(
      IntentRouter intentRouter,
      ChatService chatService,
      ChatResponseMapper chatResponseMapper,
      AgentToolOrchestrator agentToolOrchestrator) {
    this.intentRouter = intentRouter;
    this.chatService = chatService;
    this.chatResponseMapper = chatResponseMapper;
    this.agentToolOrchestrator = agentToolOrchestrator;
  }

  public RagChatResponse handle(ChatRequest request, Authentication authentication) {
    RoutingDecision decision = intentRouter.route(request.message());
    return switch (decision.category()) {
      case STATIC_POLICY_RAG -> chatService.chat(request);
      case DYNAMIC_API_REQUIRED -> routeToAgent(request, authentication, decision);
      case UNSAFE_OR_UNCONFIRMED_FALLBACK -> unsafeFallback();
    };
  }

  private RagChatResponse routeToAgent(
      ChatRequest request, Authentication authentication, RoutingDecision decision) {
    if (decision.missingRequiredContext()) {
      return chatResponseMapper.fallback(
          missingContextMessage(decision), "MISSING_REQUIRED_CONTEXT");
    }
    return agentToolOrchestrator.handle(request, decision, authentication);
  }

  private RagChatResponse unsafeFallback() {
    return chatResponseMapper.fallback(
        "Xin lỗi, tôi chưa có thông tin chính thức để xác nhận vấn đề này. "
            + "Bạn nên kiểm tra nguồn chính thức hoặc liên hệ nhân viên hỗ trợ.",
        "UNSAFE_OR_UNCONFIRMED");
  }

  private String missingContextMessage(RoutingDecision decision) {
    if (decision.requiredContext().contains(RequiredContext.AUTHENTICATED_ACCOUNT)) {
      return "Yeu cau nay can du lieu tai khoan. Ban vui long dang nhap de toi tra cuu chuyen di cua ban.";
    }
    if (decision.requiredContext().contains(RequiredContext.PAYMENT_ID)) {
      return "Yeu cau nay can ma thanh toan hoac ma booking de tra cuu. Ban gui them payment id hoac PNR giup toi.";
    }
    if (decision.requiredContext().contains(RequiredContext.FLIGHT_ID)) {
      return "Yeu cau nay can ma chuyen bay va thong tin fare de tra cuu chinh xac.";
    }
    return "Yeu cau nay can ma booking va thong tin xac minh de toi goi dung API tra cuu.";
  }
}
