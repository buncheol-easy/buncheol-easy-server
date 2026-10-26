package buncheoleasy.aidraft.domain;

import buncheoleasy.global.domain.TimestampedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * AI 개최글 요청 한 건의 기록이자 비용 장부. 엔진을 부르기 전에 RUNNING 으로 만들고, 끝나면 결과·비용으로 마감한다.
 *
 * <p>붙여 넣은 글은 저장하지 않는다(길이만) — 개최자가 남의 글을 붙여 넣을 수 있고, 장부에는 필요 없다.
 */
@Entity
@Table(name = "ai_draft_requests")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AiDraftRequest extends TimestampedEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "user_id", nullable = false, updatable = false)
  private Long userId;

  @Enumerated(EnumType.STRING)
  @Column(name = "source_type", nullable = false, length = 10, updatable = false)
  private AiDraftSourceType sourceType;

  @Column(name = "source_host", length = 255, updatable = false)
  private String sourceHost;

  @Column(name = "source_url", length = AiDraftSource.URL_MAX_LENGTH, updatable = false)
  private String sourceUrl;

  @Column(name = "raw_text_length", updatable = false)
  private Integer rawTextLength;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private AiDraftStatus status;

  @Column(name = "error_code", length = 20)
  private String errorCode;

  @Column(name = "cost_usd", precision = 12, scale = 6)
  private BigDecimal costUsd;

  @Column(name = "input_tokens")
  private Integer inputTokens;

  @Column(name = "output_tokens")
  private Integer outputTokens;

  @Column(name = "ai_call_count")
  private Integer aiCallCount;

  @Column(name = "duration_ms")
  private Long durationMs;

  @Column(name = "matched_group_id")
  private Long matchedGroupId;

  @Column(name = "finished_at")
  private Instant finishedAt;

  public static AiDraftRequest start(final Long userId, final AiDraftSource source) {
    AiDraftRequest request = new AiDraftRequest();
    request.userId = userId;
    request.sourceType = source.type();
    request.sourceHost = source.host();
    request.sourceUrl = source.url();
    request.rawTextLength = source.rawTextLength();
    request.status = AiDraftStatus.RUNNING;
    return request;
  }

  public void succeed(
      final AiDraftCost cost,
      final Long matchedGroupId,
      final Duration elapsed,
      final Instant finishedAt) {
    this.status = AiDraftStatus.SUCCEEDED;
    this.costUsd = cost.costUsd();
    this.inputTokens = cost.inputTokens();
    this.outputTokens = cost.outputTokens();
    this.aiCallCount = cost.aiCallCount();
    this.matchedGroupId = matchedGroupId;
    finish(elapsed, finishedAt);
  }

  /** 엔진이 오류로 답하면 비용 장부가 오지 않는다 — 비용 칸은 비워 둔다. */
  public void fail(final String errorCode, final Duration elapsed, final Instant finishedAt) {
    this.status = AiDraftStatus.FAILED;
    this.errorCode = errorCode;
    finish(elapsed, finishedAt);
  }

  private void finish(final Duration elapsed, final Instant finishedAt) {
    this.durationMs = elapsed.toMillis();
    this.finishedAt = finishedAt;
  }
}
