package com.example.moduleai.conversation;

import com.example.moduleai.dto.AnswerType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "ai_chat_turns")
public class ChatTurnEntity {

  @Id private UUID id;

  @Column(name = "conversation_id", nullable = false)
  private UUID conversationId;

  @Column(name = "client_message_id", nullable = false, length = 100)
  private String clientMessageId;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false, length = 20)
  private ChatTurnStatus status;

  @Enumerated(EnumType.STRING)
  @Column(name = "answer_type", length = 40)
  private AnswerType answerType;

  @Column(name = "safe_response", columnDefinition = "jsonb")
  @JdbcTypeCode(SqlTypes.JSON)
  private String safeResponse;

  @Column(name = "memory_eligible", nullable = false)
  private boolean memoryEligible;

  @Column(name = "created_at", nullable = false)
  private OffsetDateTime createdAt;

  @Column(name = "completed_at")
  private OffsetDateTime completedAt;

  @Column(name = "updated_at", nullable = false)
  private OffsetDateTime updatedAt;

  public UUID getId() {
    return id;
  }

  public void setId(UUID id) {
    this.id = id;
  }

  public UUID getConversationId() {
    return conversationId;
  }

  public void setConversationId(UUID conversationId) {
    this.conversationId = conversationId;
  }

  public String getClientMessageId() {
    return clientMessageId;
  }

  public void setClientMessageId(String clientMessageId) {
    this.clientMessageId = clientMessageId;
  }

  public ChatTurnStatus getStatus() {
    return status;
  }

  public void setStatus(ChatTurnStatus status) {
    this.status = status;
  }

  public AnswerType getAnswerType() {
    return answerType;
  }

  public void setAnswerType(AnswerType answerType) {
    this.answerType = answerType;
  }

  public String getSafeResponse() {
    return safeResponse;
  }

  public void setSafeResponse(String safeResponse) {
    this.safeResponse = safeResponse;
  }

  public boolean isMemoryEligible() {
    return memoryEligible;
  }

  public void setMemoryEligible(boolean memoryEligible) {
    this.memoryEligible = memoryEligible;
  }

  public OffsetDateTime getCreatedAt() {
    return createdAt;
  }

  public void setCreatedAt(OffsetDateTime createdAt) {
    this.createdAt = createdAt;
  }

  public OffsetDateTime getCompletedAt() {
    return completedAt;
  }

  public void setCompletedAt(OffsetDateTime completedAt) {
    this.completedAt = completedAt;
  }

  public OffsetDateTime getUpdatedAt() {
    return updatedAt;
  }

  public void setUpdatedAt(OffsetDateTime updatedAt) {
    this.updatedAt = updatedAt;
  }
}
