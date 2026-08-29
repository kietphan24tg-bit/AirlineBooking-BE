package com.example.moduleai.conversation;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "ai_chat_messages")
public class ChatMessageEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "conversation_id", nullable = false)
  private UUID conversationId;

  @Column(name = "turn_id", nullable = false)
  private UUID turnId;

  @Column(name = "sequence_number", nullable = false)
  private long sequenceNumber;

  @Enumerated(EnumType.STRING)
  @Column(name = "role", nullable = false, length = 20)
  private ChatMessageRole role;

  @Column(name = "content", nullable = false)
  private String content;

  @Column(name = "safe_metadata", columnDefinition = "jsonb")
  @JdbcTypeCode(SqlTypes.JSON)
  private String safeMetadata;

  @Column(name = "created_at", nullable = false)
  private OffsetDateTime createdAt;

  public Long getId() {
    return id;
  }

  public UUID getConversationId() {
    return conversationId;
  }

  public void setConversationId(UUID conversationId) {
    this.conversationId = conversationId;
  }

  public UUID getTurnId() {
    return turnId;
  }

  public void setTurnId(UUID turnId) {
    this.turnId = turnId;
  }

  public long getSequenceNumber() {
    return sequenceNumber;
  }

  public void setSequenceNumber(long sequenceNumber) {
    this.sequenceNumber = sequenceNumber;
  }

  public ChatMessageRole getRole() {
    return role;
  }

  public void setRole(ChatMessageRole role) {
    this.role = role;
  }

  public String getContent() {
    return content;
  }

  public void setContent(String content) {
    this.content = content;
  }

  public String getSafeMetadata() {
    return safeMetadata;
  }

  public void setSafeMetadata(String safeMetadata) {
    this.safeMetadata = safeMetadata;
  }

  public OffsetDateTime getCreatedAt() {
    return createdAt;
  }

  public void setCreatedAt(OffsetDateTime createdAt) {
    this.createdAt = createdAt;
  }
}
