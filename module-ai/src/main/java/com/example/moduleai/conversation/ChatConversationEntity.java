package com.example.moduleai.conversation;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "ai_chat_conversations")
public class ChatConversationEntity {

  @Id private UUID id;

  @Column(name = "user_id")
  private Long userId;

  @Column(name = "guest_token_hash", length = 128)
  private String guestTokenHash;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false, length = 20)
  private ChatConversationStatus status;

  @Column(name = "title", length = 255)
  private String title;

  @Column(name = "summary")
  private String summary;

  @Column(name = "structured_state", columnDefinition = "jsonb")
  @JdbcTypeCode(SqlTypes.JSON)
  private String structuredState;

  @Enumerated(EnumType.STRING)
  @Column(name = "summary_status", nullable = false, length = 20)
  private ChatSummaryStatus summaryStatus;

  @Column(name = "summarized_through_sequence", nullable = false)
  private long summarizedThroughSequence;

  @Column(name = "summary_claimed_at")
  private OffsetDateTime summaryClaimedAt;

  @Column(name = "summary_attempts", nullable = false)
  private int summaryAttempts;

  @Column(name = "next_sequence", nullable = false)
  private long nextSequence;

  @Column(name = "last_active_at", nullable = false)
  private OffsetDateTime lastActiveAt;

  @Column(name = "archived_at")
  private OffsetDateTime archivedAt;

  @Enumerated(EnumType.STRING)
  @Column(name = "archive_reason", length = 40)
  private ChatArchiveReason archiveReason;

  @Column(name = "created_at", nullable = false)
  private OffsetDateTime createdAt;

  @Column(name = "updated_at", nullable = false)
  private OffsetDateTime updatedAt;

  @Version
  @Column(name = "version", nullable = false)
  private long version;

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

  public String getGuestTokenHash() {
    return guestTokenHash;
  }

  public void setGuestTokenHash(String guestTokenHash) {
    this.guestTokenHash = guestTokenHash;
  }

  public ChatConversationStatus getStatus() {
    return status;
  }

  public void setStatus(ChatConversationStatus status) {
    this.status = status;
  }

  public String getTitle() {
    return title;
  }

  public void setTitle(String title) {
    this.title = title;
  }

  public String getSummary() {
    return summary;
  }

  public void setSummary(String summary) {
    this.summary = summary;
  }

  public String getStructuredState() {
    return structuredState;
  }

  public void setStructuredState(String structuredState) {
    this.structuredState = structuredState;
  }

  public ChatSummaryStatus getSummaryStatus() {
    return summaryStatus;
  }

  public void setSummaryStatus(ChatSummaryStatus summaryStatus) {
    this.summaryStatus = summaryStatus;
  }

  public long getSummarizedThroughSequence() {
    return summarizedThroughSequence;
  }

  public void setSummarizedThroughSequence(long summarizedThroughSequence) {
    this.summarizedThroughSequence = summarizedThroughSequence;
  }

  public OffsetDateTime getSummaryClaimedAt() {
    return summaryClaimedAt;
  }

  public void setSummaryClaimedAt(OffsetDateTime summaryClaimedAt) {
    this.summaryClaimedAt = summaryClaimedAt;
  }

  public int getSummaryAttempts() {
    return summaryAttempts;
  }

  public void setSummaryAttempts(int summaryAttempts) {
    this.summaryAttempts = summaryAttempts;
  }

  public long getNextSequence() {
    return nextSequence;
  }

  public void setNextSequence(long nextSequence) {
    this.nextSequence = nextSequence;
  }

  public OffsetDateTime getLastActiveAt() {
    return lastActiveAt;
  }

  public void setLastActiveAt(OffsetDateTime lastActiveAt) {
    this.lastActiveAt = lastActiveAt;
  }

  public OffsetDateTime getArchivedAt() {
    return archivedAt;
  }

  public void setArchivedAt(OffsetDateTime archivedAt) {
    this.archivedAt = archivedAt;
  }

  public ChatArchiveReason getArchiveReason() {
    return archiveReason;
  }

  public void setArchiveReason(ChatArchiveReason archiveReason) {
    this.archiveReason = archiveReason;
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
