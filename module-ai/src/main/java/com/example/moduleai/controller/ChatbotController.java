package com.example.moduleai.controller;

import com.example.moduleai.chat.ChatService;
import com.example.moduleai.dto.ChatRequest;
import com.example.moduleai.dto.RagChatResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Validated
@RequestMapping("/api/chatbot")
@Tag(name = "Chatbot", description = "RAG chatbot endpoints")
public class ChatbotController {

  private final ChatService chatService;

  public ChatbotController(ChatService chatService) {
    this.chatService = chatService;
  }

  @PostMapping("/messages")
  @Operation(
      summary = "Ask the AirlineBooking chatbot",
      description = "Answers general airline policy questions using the production RAG index.")
  @SecurityRequirements
  @ApiResponses({
    @ApiResponse(
        responseCode = "200",
        description = "Chatbot answer returned",
        content = @Content(schema = @Schema(implementation = RagChatResponse.class))),
    @ApiResponse(responseCode = "400", description = "Validation failed", content = @Content)
  })
  RagChatResponse chat(@Valid @RequestBody ChatRequest request) {
    return chatService.chat(request);
  }
}
