package buncheoleasy.buncheol.domain;

import buncheoleasy.global.exception.domain.BusinessException;
import buncheoleasy.global.exception.domain.ErrorCode;
import buncheoleasy.user.domain.BankAccount;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class BuncheolDomainService {

  private final BuncheolRepository buncheolRepository;
  private final Clock clock;

  public Buncheol createBuncheol(final Long hostId, final BuncheolParams params) {
    return buncheolRepository.save(Buncheol.create(hostId, params, Instant.now(clock)));
  }

  public Buncheol getBuncheol(final Long id) {
    return buncheolRepository
        .findById(id)
        .orElseThrow(() -> new BusinessException(ErrorCode.BUNCHEOL_NOT_FOUND));
  }

  // C2C 오픈으로 can_host 게이트가 사라진 자리의 남용 방지(무제한 개최·건당 이미지 5장 업로드) —
  // 일반 유저의 활성(모집중·입금 수집중) 개최 수 상한. 운영진(can_host)은 이벤트 운영을 위해 적용하지 않는다.
  private static final int MAX_ACTIVE_HOSTED = 20;

  public void validateActiveHostedLimit(final Long hostId) {
    if (isActiveHostedLimitExceeded(hostId)) {
      throw new BusinessException(ErrorCode.BUNCHEOL_ACTIVE_HOST_LIMIT_EXCEEDED);
    }
  }

  /** 던지지 않는 상한 판정 — 개최 폼 진입 전 사전 조회용(docs/53 Q-07). 제출 게이트와 같은 카운트를 공유한다. */
  public boolean isActiveHostedLimitExceeded(final Long hostId) {
    return buncheolRepository.countActiveByHostId(hostId) >= MAX_ACTIVE_HOSTED;
  }

  public void updateBuncheolContent(
      final Buncheol buncheol, final String title, final String description) {
    buncheol.updateContent(title, description);
  }

  public void updateBuncheolOpenChatUrl(final Buncheol buncheol, final String openChatUrl) {
    buncheol.updateOpenChatUrl(openChatUrl);
  }

  /**
   * 링크 전용 수정 — 공백을 제거로 본다. null 은 요청 DTO 의 {@code @NotNull} 이 웹에서 차단하고 도메인은 방어적으로만 받는다
   * ({@link Buncheol#replaceOpenChatUrl}).
   */
  public void replaceBuncheolOpenChatUrl(final Buncheol buncheol, final String openChatUrl) {
    buncheol.replaceOpenChatUrl(openChatUrl);
  }

  /**
   * 마감 판정(진행확정/취소)을 단일 CAS 로 원자 전이한다. 입금확인 인원이 최소 인원 이상이면 CONFIRMED, 미만이면 CANCELLED. 카운트·비교·전이를 한
   * UPDATE 로 묶어, 카운트 조회와 전이 사이에 입금확인이 커밋돼 발생하는 오판(stale count)을 방지한다.
   *
   * @return 전이에 성공하면 true, 이미 마감 판정됐거나 RECRUITING 이 아니면 false (실제 전이 상태는 호출 측이 재조회로 판별)
   */
  public boolean finalizeExpiredByConfirmedHeadcount(final Long buncheolId, final Instant now) {
    return buncheolRepository.finalizeExpiredByConfirmedHeadcount(buncheolId, now) > 0;
  }

  /**
   * 전 슬롯 입금확인 시 조기 진행확정 CAS (RECRUITING → CONFIRMED). 입금확인 수·최소인원 판정을 UPDATE WHERE 서브쿼리로 원자화해,
   * 마지막 슬롯을 동시에 입금확인하는 경합에서도 조기 확정을 놓치지 않는다. 호출 측 {@code @Transactional} 필수.
   *
   * @return 조기 확정에 성공하면 true, 아직 매진 아님/최소인원 미달/이미 마감이면 false
   */
  public boolean confirmIfAllSlotsConfirmed(
      final Long buncheolId, final long totalSlots, final Instant now) {
    return buncheolRepository.confirmIfAllSlotsConfirmed(buncheolId, totalSlots, now) > 0;
  }

  /**
   * 호스트의 분철 취소 (RECRUITING/CANCELLED → HOST_CANCELLED CAS). 진행확정(CONFIRMED)·이미 개최자 취소된 분철은 상태 위반으로
   * 막는다. 입금 수집중은 개최자 혼자 접지 않는다 — 호출 측이 BCH-126 으로 먼저 거르고, 여기 와도 어느 CAS 에도 걸리지 않아 상태
   * 위반이다. 목록·상세에서 숨겨지며(하드 삭제 대신 소프트 숨김), 활성 참여 cascade 취소·알림은 호출 측이 반환된 직전 상태로 판단해 처리한다.
   *
   * <p>상태별 CAS 를 순차 시도해 마감 스케줄러와 경합해도 성공한 시도로 직전 상태가 확정된다 — 허용 상태 IN 단일 CAS 로는 어느 상태에서 전이됐는지 알 수 없어,
   * 자동취소 직후 케스케이드를 중복 실행(취소 알림 재발송)할 수 있다.
   *
   * @return 전이 직전 상태 (RECRUITING 이면 케스케이드 필요, CANCELLED 면 자동취소 시 이미 완료됨)
   */
  public BuncheolStatus cancelBuncheol(final Long buncheolId, final Instant now) {
    if (buncheolRepository.finalizeIfStatus(
            buncheolId, BuncheolStatus.RECRUITING, BuncheolStatus.HOST_CANCELLED, now)
        > 0) {
      return BuncheolStatus.RECRUITING;
    }
    if (buncheolRepository.finalizeIfStatus(
            buncheolId, BuncheolStatus.CANCELLED, BuncheolStatus.HOST_CANCELLED, now)
        > 0) {
      return BuncheolStatus.CANCELLED;
    }
    throw new BusinessException(ErrorCode.BUNCHEOL_CANCEL_NOT_ALLOWED);
  }

  /**
   * 회원 개최 마감 미달 취소 CAS (RECRUITING → CANCELLED). 마감이 아직 안 지났으면(그 사이 연장됨) 취소하지 않는다 — 포트 javadoc
   * 참고. 호출 측 {@code @Transactional} 필수.
   */
  public boolean cancelBelowMinHeadcount(final Long buncheolId, final Instant now) {
    return buncheolRepository.cancelExpiredC2cIfRecruiting(buncheolId, now) > 0;
  }

  /** 호스트에게 아직 끝나지 않은 분철이 있는지 (회원탈퇴 가드용). 판정 기준은 포트 javadoc 참고. */
  public boolean hasUnfinishedBuncheolHostedBy(final Long hostId) {
    return buncheolRepository.existsUnfinishedByHostId(hostId);
  }

  // --- C2C 플로우 전이 (docs/46 §4) ---

  /**
   * 회원 개최 입금 단계 시작 CAS (RECRUITING → PAYMENT_COLLECTING). 일괄 입금 기한과 그 시점 개최자 계좌 스냅샷을 함께 기록한다.
   * 호출 측 {@code @Transactional} 필수.
   *
   * @return 전이에 성공하면 true (false 면 RECRUITING 이 아니거나 C2C 분철이 아님)
   */
  public boolean startCollecting(
      final Long buncheolId,
      final Instant paymentDueAt,
      final BankAccount hostAccount,
      final Instant now) {
    return buncheolRepository.startCollectingIfRecruiting(
            buncheolId,
            paymentDueAt,
            hostAccount.bank(),
            hostAccount.account(),
            hostAccount.holder(),
            now)
        > 0;
  }

  /**
   * 운영진 파기 CAS (회원 개최 {@code expectedStatus} → HOST_CANCELLED) — 포트 javadoc 참고.
   * 호출 측 {@code @Transactional} 필수.
   */
  public boolean staffCancelIfStatus(
      final Long buncheolId, final BuncheolStatus expectedStatus, final Instant now) {
    return buncheolRepository.staffCancelIfStatus(buncheolId, expectedStatus, now) > 0;
  }

  /** 회원 개최 진행 확정 CAS (PAYMENT_COLLECTING → CONFIRMED) — 포트 javadoc 참고. 호출 측 {@code @Transactional} 필수. */
  public boolean confirmIfCollecting(final Long buncheolId, final Instant now) {
    return buncheolRepository.confirmIfCollecting(buncheolId, now) > 0;
  }

  /**
   * 최소 인원 ≤ 자리 수. 입금 단계 시작 판정은 참여 행 수를 세고 활성 참여는 자리당 1건이라, 최소 인원이 자리 수를 넘으면 시작될 수
   * 없는 분철이 열린다. 개최(·수정) 요청 검사용이다 — 엔티티 불변식으로 두지 않는 이유는 옛 입금 수집중 분철(옛 자리 빼기는 최소
   * 인원을 낮추지 않았다)을 읽어야 하기 때문이다({@link #lowerMinHeadcountToSlotCount}).
   */
  public void validateMinHeadcountWithinSlots(final int minHeadcount, final int slotCount) {
    if (minHeadcount > slotCount) {
      throw new BusinessException(ErrorCode.BUNCHEOL_MIN_HEADCOUNT_EXCEEDS_SLOTS);
    }
  }

  /**
   * 개최자가 자리를 뺀 뒤 모집중 분철의 최소 인원을 남은 자리 수로 낮춘다 — 포트 javadoc 참고. 모집중이 아니거나 이미 자리 수
   * 이하면 그대로 둔다. 호출 측 {@code @Transactional} 필수.
   *
   * @return 낮췄으면 true
   */
  public boolean lowerMinHeadcountToSlotCount(
      final Long buncheolId, final int slotCount, final Instant now) {
    // 엔티티 검증(Buncheol#validateMinHeadcount)을 거치지 않는 UPDATE 라 「최소 인원 ≥ 1」을 여기서 지킨다.
    if (slotCount < 1) {
      throw new BusinessException(ErrorCode.BUNCHEOL_MIN_HEADCOUNT_INVALID);
    }
    return buncheolRepository.lowerMinHeadcountIfRecruiting(buncheolId, slotCount, now) > 0;
  }

  /**
   * 분철 행 잠금 조회. 호출 측 {@code @Transactional} 필수.
   *
   * <p><b>분철 단위 쓰기는 이 잠금을 첫 잠금으로 잡는다.</b> 대상은 회원 개최 분철의 상태·자리·참여·묶음을 바꾸는 쓰기 전부(추가 모집
   * 스위치, 추가 신청 수락·거절, 「제외」 포함)와 운영진 슬롯 입금 확인({@code ParticipationService#lockBuncheolBeforeConfirm})이다. 참여·묶음·자리 잠금이 모두 분철 행
   * 앞에서 한 줄로 선 뒤에 잡히므로 경로끼리 잠금 순서가 엇갈리지 않는다. 상태 보장은 CAS 가 하고 이 잠금은 순서만 맡는다.
   *
   * <p>트랜잭션의 첫 분철 조회여야 한다 — 앞에서 {@link #getBuncheol} 로 읽었으면 영속성 컨텍스트가 그 낡은 인스턴스를
   * 돌려준다(그때는 {@link #getLatestBuncheolForUpdate}). 묶음·참여에서 분철 id 를 읽는 조회는 먼저 해도 된다. 잠금 뒤의
   * 판단은 CAS 나 잠금 읽기로만 한다 — 잠금 전 일반 조회가 만든 스냅샷에는 기다리는 사이 커밋된 변화가 보이지 않는다.
   */
  public Buncheol getBuncheolForUpdate(final Long id) {
    return buncheolRepository
        .findByIdForUpdate(id)
        .orElseThrow(() -> new BusinessException(ErrorCode.BUNCHEOL_NOT_FOUND));
  }

  /**
   * 진행 확정 분철이 「종료」(끝나지 않은 일 없음, 모든 배송 도착)인가. 개최 목록과 같은 판정이다({@link
   * BuncheolRepository#findEndedIds}). 일반 조회다. 잠금 앞에 일반 조회가 있는 트랜잭션(추가 신청)에서는 잠금 전 스냅샷이라 「종료」를
   * 놓칠 수 있다 — 한 방향이라 "아직 종료 아님" 쪽으로만 틀린다. 첫 문장이 잠금 읽기인 경로(스위치, 자리 편집)는 잠금 뒤 스냅샷을 본다.
   */
  public boolean isEnded(final Long buncheolId) {
    return buncheolRepository.findEndedIds(List.of(buncheolId)).contains(buncheolId);
  }

  /**
   * 지금 추가 신청을 받는가 — 진행 확정 회원 개최 + 스위치 켜짐 + 「종료」 전({@link Buncheol#isExtraRecruiting}). 「종료」 조회는
   * {@link #isEndedForExtraRecruit} 와 같다.
   */
  public boolean isExtraRecruiting(final Buncheol buncheol) {
    return buncheol.isExtraRecruiting(isEndedForExtraRecruit(buncheol));
  }

  /**
   * 추가 모집 판정에 넘길 「종료」 여부. 판정에 필요할 때({@link Buncheol#needsEndedCheck}, 스위치가 켜진 진행 확정 회원 개최)만 조회하고
   * 아니면 조회 없이 false 다 — 결과에 쓰이지 않는다. 잠금 전 스냅샷 주의는 {@link #isEnded} 와 같다.
   */
  public boolean isEndedForExtraRecruit(final Buncheol buncheol) {
    return buncheol.needsEndedCheck() && isEnded(buncheol.getId());
  }

  /**
   * 잠금 조회 + 잠금 시점 최신 값으로 다시 채우기 — 같은 트랜잭션에서 잠금 전에 이미 분철을 읽은 경로용(포트 javadoc).
   * 호출 측 {@code @Transactional} 필수.
   */
  public Buncheol getLatestBuncheolForUpdate(final Long id) {
    return buncheolRepository
        .findLatestByIdForUpdate(id)
        .orElseThrow(() -> new BusinessException(ErrorCode.BUNCHEOL_NOT_FOUND));
  }

  /**
   * 활성 신청이 없는 모집중 C2C 분철의 거래 조건을 바꾼다 — 포트 javadoc 참고. 값은 {@code Buncheol#applyTermsChange} 로 검증을
   * 마친 것이어야 한다(UPDATE 는 엔티티 검증을 거치지 않는다). 호출 측 {@code @Transactional} 필수.
   *
   * @return 바꿨으면 true (false = 그 사이 신청이 들어왔거나 모집중·마감 전이 아님)
   */
  public boolean changeTermsIfNoApplicant(
      final Long buncheolId, final BuncheolTerms terms, final Instant now) {
    return buncheolRepository.changeTermsIfNoApplicant(buncheolId, terms, now) > 0;
  }
}
