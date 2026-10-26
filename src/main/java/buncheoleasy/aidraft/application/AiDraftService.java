package buncheoleasy.aidraft.application;

import buncheoleasy.aidraft.application.AiDraftGroupMatcher.GroupMatch;
import buncheoleasy.aidraft.domain.AiDraftDay;
import buncheoleasy.aidraft.domain.AiDraftSource;
import buncheoleasy.aidraft.domain.AiDraftText;
import buncheoleasy.aidraft.domain.AiDraftUsage;
import buncheoleasy.aidraft.dto.request.CreateAiDraftRequest;
import buncheoleasy.aidraft.dto.response.AiDraftQuotaResponse;
import buncheoleasy.aidraft.dto.response.AiDraftResponse;
import buncheoleasy.aidraft.infrastructure.AiDraftProperties;
import buncheoleasy.aidraft.infrastructure.AiWorkerClient;
import buncheoleasy.aidraft.infrastructure.AiWorkerDraft;
import buncheoleasy.buncheol.application.BuncheolService;
import buncheoleasy.buncheol.domain.Buncheol;
import buncheoleasy.buncheol.dto.response.HostingEligibilityResponse;
import buncheoleasy.global.exception.domain.BusinessException;
import buncheoleasy.global.exception.domain.ErrorCode;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.Semaphore;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

/**
 * AI 개최글 도우미 — 판매 페이지 주소(또는 붙여 넣은 글)로 엔진에 초안을 받아 개최 폼 모양으로 돌려준다. 요청 스레드가 엔진 응답을 기다리는 동기
 * 처리다(엔진 p50 7초·최대 13초 실측).
 *
 * <p>순서: 기능 스위치 → 개최 자격 → 입력 → 1인 잠금 → 하루 시도·생성 횟수 → 서버 동시 상한 → 기록(RUNNING) → 엔진 → 그룹 확정 → 기록 마감.
 * 엔진까지 가지 못했거나 엔진이 AI 를 부르기 전에 멈춘 실패는 생성 횟수를 돌려준다. 시도 횟수는 돌려주지 않는다.
 *
 * <p>트랜잭션을 걸지 않는다 — 엔진 대기 동안 DB 커넥션을 쥐지 않도록 기록은 {@link AiDraftRequestRecorder} 의 짧은 트랜잭션으로만 쓴다. 이 말이
 * 사실이려면 요청 단위 EntityManager(OSIV)도 꺼져 있어야 한다 — {@code WebConfig} 가 {@code /v1/ai/**} 에서 뺀다.
 */
@Slf4j
@Service
public class AiDraftService {

  /** 엔진 계약(구매처 이름 20자)이자 개최 폼 구매처 칸 길이. */
  private static final int PURCHASE_SITE_MAX_LENGTH = 20;

  private final AiDraftProperties properties;
  private final BuncheolService buncheolService;
  private final AiDraftUsage usage;
  private final AiDraftRequestRecorder recorder;
  private final AiWorkerClient workerClient;
  private final AiDraftGroupMatcher groupMatcher;
  private final ApplicationEventPublisher eventPublisher;
  private final Clock clock;

  /**
   * 이 JVM 이 엔진을 동시에 부르는 상한. 줄 세우지 않고 바로 거절해 톰캣 스레드를 묶지 않는다. 블루-그린 전환 창(약 30초)에는 두 JVM 이 각자 상한까지
   * 부를 수 있어 합이 두 배가 된다 — 엔진 동시 처리 상한을 넘는 몫은 엔진이 503 으로 돌려보내고, 그건 AI-005 로 나간다.
   */
  private final Semaphore engineSlots;

  public AiDraftService(
      final AiDraftProperties properties,
      final BuncheolService buncheolService,
      final AiDraftUsage usage,
      final AiDraftRequestRecorder recorder,
      final AiWorkerClient workerClient,
      final AiDraftGroupMatcher groupMatcher,
      final ApplicationEventPublisher eventPublisher,
      final Clock clock) {
    this.properties = properties;
    this.buncheolService = buncheolService;
    this.usage = usage;
    this.recorder = recorder;
    this.workerClient = workerClient;
    this.groupMatcher = groupMatcher;
    this.eventPublisher = eventPublisher;
    this.clock = clock;
    this.engineSlots = new Semaphore(properties.maxConcurrent());
  }

  public AiDraftQuotaResponse getQuota(final Long userId) {
    if (!properties.usable()) {
      return new AiDraftQuotaResponse(false, properties.dailyLimitPerUser(), 0);
    }
    long used = usage.countGenerations(userId, AiDraftDay.of(clock.instant()));
    return new AiDraftQuotaResponse(true, properties.dailyLimitPerUser(), remaining(used));
  }

  public AiDraftResponse createDraft(final Long userId, final CreateAiDraftRequest request) {
    if (!properties.usable()) {
      throw new BusinessException(ErrorCode.AI_DRAFT_DISABLED);
    }
    requireHostingEligible(userId);
    AiDraftSource source = AiDraftSource.of(request.url(), request.rawText());

    String lockToken =
        usage
            .lock(userId, properties.lockTtl())
            .orElseThrow(() -> new BusinessException(ErrorCode.AI_DRAFT_IN_PROGRESS));
    try {
      return createDraftLocked(userId, source);
    } finally {
      usage.unlock(userId, lockToken);
    }
  }

  /**
   * 개최할 수 없는 사람에게는 AI 도 열지 않는다. 사유마다 개최 요청({@code POST /v1/buncheols})이 같은 상황에 던지는 오류를 그대로 던진다 — 화면이
   * 이미 그 코드로 안내한다. switch 식이라 사유가 늘면 컴파일이 깨진다(새 사유가 조용히 통과되지 않게).
   */
  private void requireHostingEligible(final Long userId) {
    HostingEligibilityResponse eligibility = buncheolService.getHostingEligibility(userId);
    if (eligibility.eligible()) {
      return;
    }
    throw new BusinessException(
        switch (eligibility.reason()) {
          case PHONE_REQUIRED -> ErrorCode.USER_PROFILE_IS_NOT_COMPLETE;
          case AGE_UNVERIFIED -> ErrorCode.USER_AGE_NOT_VERIFIED;
          case NOT_ADULT -> ErrorCode.USER_NOT_ADULT;
          case LIMIT_EXCEEDED -> ErrorCode.BUNCHEOL_ACTIVE_HOST_LIMIT_EXCEEDED;
          case BANK_ACCOUNT_REQUIRED -> ErrorCode.USER_BANK_ACCOUNT_NOT_REGISTERED;
        });
  }

  private AiDraftResponse createDraftLocked(final Long userId, final AiDraftSource source) {
    AiDraftDay day = AiDraftDay.of(clock.instant());
    if (usage.addAttempt(userId, day) > properties.dailyAttemptLimitPerUser()) {
      throw new BusinessException(ErrorCode.AI_DRAFT_DAILY_LIMIT_EXCEEDED);
    }
    // 한도를 넘은 증가는 돌려주지 않는다 — 남은 횟수는 0 아래로 내려가지 않게 계산한다.
    long used = usage.addGeneration(userId, day);
    if (used > properties.dailyLimitPerUser()) {
      throw new BusinessException(ErrorCode.AI_DRAFT_DAILY_LIMIT_EXCEEDED);
    }

    if (!engineSlots.tryAcquire()) {
      usage.refundGeneration(userId, day);
      throw new BusinessException(ErrorCode.AI_DRAFT_UNAVAILABLE);
    }
    try {
      return generate(userId, source, day, used);
    } finally {
      engineSlots.release();
    }
  }

  private AiDraftResponse generate(
      final Long userId, final AiDraftSource source, final AiDraftDay day, final long used) {
    final Long requestId;
    try {
      requestId = recorder.start(userId, source);
    } catch (RuntimeException e) {
      usage.refundGeneration(userId, day);
      throw e;
    }

    Instant startedAt = clock.instant();
    final AiWorkerDraft draft;
    try {
      draft = workerClient.createDraft(source);
    } catch (BusinessException e) {
      settleFailedGeneration(userId, day, e.getErrorCode());
      recorder.fail(requestId, e.getErrorCode(), elapsedSince(startedAt));
      throw e;
    }
    countDailyTotal(day);

    GroupMatch match = groupMatcher.match(draft.groupName(), draft.groupMatchKeywords());
    recorder.succeed(
        requestId,
        draft.cost(),
        match.group() == null ? null : match.group().id(),
        elapsedSince(startedAt));
    return toResponse(draft, match, used);
  }

  /** AI-004 만 엔진이 AI 를 이미 불렀을 수 있다 — 그 경우는 쓴 것으로 치고 나머지는 돌려준다. */
  private void settleFailedGeneration(
      final Long userId, final AiDraftDay day, final ErrorCode errorCode) {
    if (errorCode == ErrorCode.AI_DRAFT_GENERATION_FAILED) {
      countDailyTotal(day);
      return;
    }
    usage.refundGeneration(userId, day);
  }

  /** 정확히 기준에 닿는 순간 한 번만 알린다. 합계 카운터는 막지 않으므로 그 뒤로도 계속 오른다. */
  private void countDailyTotal(final AiDraftDay day) {
    usage
        .addDailyTotal(day)
        .ifPresent(
            total -> {
              if (total == properties.dailyAlertThreshold()) {
                log.warn("AI 개최글 하루 합계가 알림 기준에 닿음 - date={} total={}", day.date(), total);
                eventPublisher.publishEvent(new AiDraftDailyTotalReachedEvent(day.date(), total));
              }
            });
  }

  private AiDraftResponse toResponse(
      final AiWorkerDraft draft, final GroupMatch match, final long used) {
    AiDraftResponse.Group group =
        match.group() == null
            ? null
            : new AiDraftResponse.Group(match.group().id(), match.group().name());
    return new AiDraftResponse(
        AiDraftText.fit(draft.title(), Buncheol.TITLE_MAX_LENGTH),
        AiDraftText.fit(draft.description(), Buncheol.DESCRIPTION_MAX_LENGTH),
        AiDraftText.fit(draft.purchaseSite(), PURCHASE_SITE_MAX_LENGTH),
        draft.purchaseUrl(),
        group,
        match.keywords(),
        draft.listPrice(),
        draft.priceCurrency(),
        draft.releaseDate(),
        draft.images(),
        draft.thumbnailIndex(),
        remaining(used));
  }

  private int remaining(final long used) {
    return (int) Math.max(0, properties.dailyLimitPerUser() - used);
  }

  private Duration elapsedSince(final Instant startedAt) {
    return Duration.between(startedAt, clock.instant());
  }
}
