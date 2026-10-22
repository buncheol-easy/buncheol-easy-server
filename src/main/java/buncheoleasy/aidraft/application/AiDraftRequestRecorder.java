package buncheoleasy.aidraft.application;

import buncheoleasy.aidraft.domain.AiDraftCost;
import buncheoleasy.aidraft.domain.AiDraftRequest;
import buncheoleasy.aidraft.domain.AiDraftRequestRepository;
import buncheoleasy.aidraft.domain.AiDraftSource;
import buncheoleasy.global.exception.domain.ErrorCode;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 요청 기록을 엔진 호출 앞뒤의 <b>짧은 트랜잭션 두 개</b>로 나눠 쓴다. 엔진 대기(최대 25초)를 트랜잭션 안에 넣으면 그동안 DB 커넥션을 쥐고 있게
 * 된다 — 그래서 서비스가 아니라 별도 빈에 두어 트랜잭션 경계가 프록시를 지나게 한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AiDraftRequestRecorder {

  private final AiDraftRequestRepository aiDraftRequestRepository;
  private final Clock clock;

  /** RUNNING 으로 남긴다. 단일 INSERT 라 저장소 자체 트랜잭션으로 충분하다. */
  public Long start(final Long userId, final AiDraftSource source) {
    return aiDraftRequestRepository.save(AiDraftRequest.start(userId, source)).getId();
  }

  @Transactional
  public void succeed(
      final Long requestId,
      final AiDraftCost cost,
      final Long matchedGroupId,
      final Duration elapsed) {
    Instant now = Instant.now(clock);
    aiDraftRequestRepository
        .findById(requestId)
        .ifPresentOrElse(
            request -> request.succeed(cost, matchedGroupId, elapsed, now),
            () -> logMissing(requestId));
  }

  @Transactional
  public void fail(final Long requestId, final ErrorCode errorCode, final Duration elapsed) {
    Instant now = Instant.now(clock);
    aiDraftRequestRepository
        .findById(requestId)
        .ifPresentOrElse(
            request -> request.fail(errorCode.getCode(), elapsed, now),
            () -> logMissing(requestId));
  }

  // 회원 행이 지워지면(ON DELETE CASCADE) 기록도 사라진다. 기록 마감 때문에 응답을 실패시키지 않는다.
  private static void logMissing(final Long requestId) {
    log.warn("AI 개최글 기록을 찾지 못해 마감하지 않음 - requestId={}", requestId);
  }
}
