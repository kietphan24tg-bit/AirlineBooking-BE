package com.example.moduleai.confirmation;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "ai_tool_audit_events")
public class ToolAuditEventEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "proposal_id", nullable = false)
  private UUID proposalId;

  @Column(name = "request_id", length = 100)
  private String requestId;

  @Column(name = "user_id", nullable = false)
  private Long userId;

  @Column(name = "tool_name", nullable = false, length = 100)
  private String toolName;

  @Column(name = "event_type", nullable = false, length = 50)
  private String eventType;

  @Column(name = "status", nullable = false, length = 30)
  private String status;

  @Column(name = "error_code", length = 100)
  private String errorCode;

  @Column(name = "latency_ms")
  private Long latencyMs;

  @Column(name = "created_at", nullable = false)
  private OffsetDateTime createdAt;

  public Long getId() {
    return id;
  }

  public void setId(Long id) {
    this.id = id;
  }

  public UUID getProposalId() {
    return proposalId;
  }

  public void setProposalId(UUID proposalId) {
    this.proposalId = proposalId;
  }

  public String getRequestId() {
    return requestId;
  }

  public void setRequestId(String requestId) {
    this.requestId = requestId;
  }

  public Long getUserId() {
    return userId;
  }

  public void setUserId(Long userId) {
    this.userId = userId;
  }

  public String getToolName() {
    return toolName;
  }

  public void setToolName(String toolName) {
    this.toolName = toolName;
  }

  public String getEventType() {
    return eventType;
  }

  public void setEventType(String eventType) {
    this.eventType = eventType;
  }

  public String getStatus() {
    return status;
  }

  public void setStatus(String status) {
    this.status = status;
  }

  public String getErrorCode() {
    return errorCode;
  }

  public void setErrorCode(String errorCode) {
    this.errorCode = errorCode;
  }

  public Long getLatencyMs() {
    return latencyMs;
  }

  public void setLatencyMs(Long latencyMs) {
    this.latencyMs = latencyMs;
  }

  public OffsetDateTime getCreatedAt() {
    return createdAt;
  }

  public void setCreatedAt(OffsetDateTime createdAt) {
    this.createdAt = createdAt;
  }
}
