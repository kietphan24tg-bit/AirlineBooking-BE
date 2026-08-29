package com.example.moduleai.confirmation;

import com.example.moduleai.tool.core.ToolAccess;
import com.example.moduleai.tool.core.ToolOperation;
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
@Table(name = "ai_tool_proposals")
public class ToolProposalEntity {

  @Id private UUID id;

  @Column(name = "user_id", nullable = false)
  private Long userId;

  @Column(name = "verified_booking_id")
  private Long verifiedBookingId;

  @Column(name = "conversation_id")
  private UUID conversationId;

  @Column(name = "tool_name", nullable = false, length = 100)
  private String toolName;

  @Enumerated(EnumType.STRING)
  @Column(name = "operation", nullable = false, length = 20)
  private ToolOperation operation;

  @Enumerated(EnumType.STRING)
  @Column(name = "access_level", nullable = false, length = 30)
  private ToolAccess access;

  @Column(name = "canonical_arguments", nullable = false, columnDefinition = "jsonb")
  @JdbcTypeCode(SqlTypes.JSON)
  private String canonicalArguments;

  @Column(name = "confirmation_hmac", nullable = false, length = 255)
  private String confirmationHmac;

  @Column(name = "confirmation_snapshot", nullable = false, columnDefinition = "jsonb")
  @JdbcTypeCode(SqlTypes.JSON)
  private String confirmationSnapshot;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false, length = 30)
  private ToolProposalStatus status;

  @Column(name = "idempotency_key", nullable = false, length = 255, unique = true)
  private String idempotencyKey;

  @Column(name = "safe_result", columnDefinition = "jsonb")
  @JdbcTypeCode(SqlTypes.JSON)
  private String safeResult;

  @Column(name = "safe_error", columnDefinition = "jsonb")
  @JdbcTypeCode(SqlTypes.JSON)
  private String safeError;

  @Column(name = "expires_at", nullable = false)
  private OffsetDateTime expiresAt;

  @Column(name = "claimed_at")
  private OffsetDateTime claimedAt;

  @Column(name = "completed_at")
  private OffsetDateTime completedAt;

  @Column(name = "created_at", nullable = false)
  private OffsetDateTime createdAt;

  @Column(name = "updated_at", nullable = false)
  private OffsetDateTime updatedAt;

  public UUID getId() {
    return id;
  }

  public void setId(UUID id) {
    this.id = id;
  }

  public Long getUserId() {
    return userId;
  }

  public void setUserId(Long userId) {
    this.userId = userId;
  }

  public Long getVerifiedBookingId() {
    return verifiedBookingId;
  }

  public void setVerifiedBookingId(Long verifiedBookingId) {
    this.verifiedBookingId = verifiedBookingId;
  }

  public UUID getConversationId() {
    return conversationId;
  }

  public void setConversationId(UUID conversationId) {
    this.conversationId = conversationId;
  }

  public String getToolName() {
    return toolName;
  }

  public void setToolName(String toolName) {
    this.toolName = toolName;
  }

  public ToolOperation getOperation() {
    return operation;
  }

  public void setOperation(ToolOperation operation) {
    this.operation = operation;
  }

  public ToolAccess getAccess() {
    return access;
  }

  public void setAccess(ToolAccess access) {
    this.access = access;
  }

  public String getCanonicalArguments() {
    return canonicalArguments;
  }

  public void setCanonicalArguments(String canonicalArguments) {
    this.canonicalArguments = canonicalArguments;
  }

  public String getConfirmationHmac() {
    return confirmationHmac;
  }

  public void setConfirmationHmac(String confirmationHmac) {
    this.confirmationHmac = confirmationHmac;
  }

  public String getConfirmationSnapshot() {
    return confirmationSnapshot;
  }

  public void setConfirmationSnapshot(String confirmationSnapshot) {
    this.confirmationSnapshot = confirmationSnapshot;
  }

  public ToolProposalStatus getStatus() {
    return status;
  }

  public void setStatus(ToolProposalStatus status) {
    this.status = status;
  }

  public String getIdempotencyKey() {
    return idempotencyKey;
  }

  public void setIdempotencyKey(String idempotencyKey) {
    this.idempotencyKey = idempotencyKey;
  }

  public String getSafeResult() {
    return safeResult;
  }

  public void setSafeResult(String safeResult) {
    this.safeResult = safeResult;
  }

  public String getSafeError() {
    return safeError;
  }

  public void setSafeError(String safeError) {
    this.safeError = safeError;
  }

  public OffsetDateTime getExpiresAt() {
    return expiresAt;
  }

  public void setExpiresAt(OffsetDateTime expiresAt) {
    this.expiresAt = expiresAt;
  }

  public OffsetDateTime getClaimedAt() {
    return claimedAt;
  }

  public void setClaimedAt(OffsetDateTime claimedAt) {
    this.claimedAt = claimedAt;
  }

  public OffsetDateTime getCompletedAt() {
    return completedAt;
  }

  public void setCompletedAt(OffsetDateTime completedAt) {
    this.completedAt = completedAt;
  }

  public OffsetDateTime getCreatedAt() {
    return createdAt;
  }

  public void setCreatedAt(OffsetDateTime createdAt) {
    this.createdAt = createdAt;
  }

  public OffsetDateTime getUpdatedAt() {
    return updatedAt;
  }

  public void setUpdatedAt(OffsetDateTime updatedAt) {
    this.updatedAt = updatedAt;
  }
}
