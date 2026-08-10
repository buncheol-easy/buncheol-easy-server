package buncheoleasy.buncheol.domain.participation;

import buncheoleasy.global.exception.domain.BusinessException;
import buncheoleasy.global.exception.domain.ErrorCode;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ParticipationDomainService {

  private final ParticipationRepository participationRepository;

  public boolean createParticipationIfRecruiting(final Participation participation) {
    return participationRepository.saveIfRecruiting(participation);
  }

  /** C2C 추가 모집 INSERT — 분철이 입금 수집중(PAYMENT_COLLECTING)인지 원자 확인 (docs/46 §4.7-E1). */
  public boolean createParticipationIfCollecting(final Participation participation) {
    return participationRepository.saveIfCollecting(participation);
  }

  /**
   * 분철 내 참여자의 기존 활성 참여 중 첫 건 (C2C 다슬롯 스냅샷 일치용 — docs/46 §4.7-A1). 활성 참여 수는 슬롯 수 이하라 분철 단위 조회 후
   * 필터링으로 충분하다.
   */
  public Optional<Participation> findFirstActiveInBuncheol(
      final Long buncheolId, final Long participantId) {
    return participationRepository.findActiveByBuncheolId(buncheolId).stream()
        .filter(participation -> participation.getParticipantId().equals(participantId))
        .findFirst();
  }

  public Participation getParticipation(final Long id) {
    return participationRepository
        .findById(id)
        .orElseThrow(() -> new BusinessException(ErrorCode.PARTICIPATION_NOT_FOUND));
  }

  /** 분철의 활성 참여(입금확인중·입금확인됨) 전체. 호스트 관리 화면 + 분철 취소 알림 대상. */
  public List<Participation> findActiveByBuncheolId(final Long buncheolId) {
    return participationRepository.findActiveByBuncheolId(buncheolId);
  }

  /** 분철의 입금확인(CONFIRMED) 참여 전체. 진행확정 시 배송 스냅샷·알림 대상. */
  public List<Participation> findConfirmedByBuncheolId(final Long buncheolId) {
    return participationRepository.findConfirmedByBuncheolId(buncheolId);
  }

  // 분철의 입금확인된 참여자 수. 마감 판정은 finalizeExpiredByConfirmedHeadcount CAS 가 서브쿼리로 직접 세도록 바뀌어
  // 현재 production 호출처는 없지만, 관리 화면의 확정 인원 표시 등 재사용 여지가 있어 보존한다.
  public int countConfirmedByBuncheolId(final Long buncheolId) {
    return participationRepository.countConfirmedByBuncheolId(buncheolId);
  }

  /** 참여자에게 아직 끝나지 않은 참여가 있는지 (회원탈퇴 가드용). 판정 기준은 포트 javadoc 참고. */
  public boolean hasUnfinishedParticipationBy(final Long participantId) {
    return participationRepository.existsUnfinishedByParticipantId(participantId);
  }

  /** 해당 분철에 참여자의 활성 참여가 있는지 (분철당 중복 참여 가드용). 취소·만료된 참여는 재참여를 막지 않는다. */
  public boolean hasActiveParticipationInBuncheol(final Long buncheolId, final Long participantId) {
    return participationRepository.existsActiveByBuncheolIdAndParticipantId(
        buncheolId, participantId);
  }

  /** 해당 배송지를 참조하는 활성 참여가 있는지 (배송지 삭제 가드용). */
  public boolean hasActiveParticipationByShippingAddress(final Long shippingAddressId) {
    return participationRepository.existsActiveByShippingAddressId(shippingAddressId);
  }

  /** 입금 만료가 도과한 참여 폴링 (입금 만료 스케줄러용). */
  public List<Participation> findOverduePaymentTargets(final Instant now, final int limit) {
    return participationRepository.findOverduePaymentTargets(now, limit);
  }

  /**
   * 호스트의 수동 입금확인 (AWAITING_PAYMENT → CONFIRMED CAS, 입금 기한 내일 때만). 실패 시 사유를 구분해 예외를 던진다 — 기한이
   * 지났으면 {@link ErrorCode#PARTICIPATION_PAYMENT_DUE_PASSED}, 그 외(이미 취소/확정)는 {@link
   * ErrorCode#PARTICIPATION_STATE_TRANSITION_INVALID}.
   */
  public void confirmPayment(final Long participationId, final Instant now) {
    if (participationRepository.confirmPaymentIfAwaiting(participationId, now)) {
      return;
    }
    // CAS 실패 후 재조회 시점 기준 best-effort 분기. 재조회 찰나에 만료 스케줄러가 취소하면 PAYMENT_DUE_PASSED 대신
    // STATE_TRANSITION_INVALID 로 떨어질 수 있으나, 둘 다 4xx 충돌이라 영향은 없다.
    Participation participation = getParticipation(participationId);
    if (participation.getStatus() == ParticipationStatus.AWAITING_PAYMENT) {
      throw new BusinessException(ErrorCode.PARTICIPATION_PAYMENT_DUE_PASSED);
    }
    throw new BusinessException(ErrorCode.PARTICIPATION_STATE_TRANSITION_INVALID);
  }

  /**
   * 자동 입금확인용 CAS (외부 입금 연동). {@link #confirmPayment} 와 같은 전이지만 실패를 예외로 던지지 않고 성공 여부만 돌려준다 —
   * 입금 웹훅은 재전송되므로 이미 확정된 건을 오류로 처리하면 발신 측이 실패로 간주해 재전송을 반복한다.
   */
  public boolean confirmPaymentIfAwaiting(final Long participationId, final Instant now) {
    return participationRepository.confirmPaymentIfAwaiting(participationId, now);
  }

  /** 입금 만료 처리 (입금 만료 스케줄러용). 멱등하며 실패 시(이미 확정/취소) 예외 없이 false 를 돌려준다. */
  public boolean expirePayment(final Long participationId, final Instant now) {
    return participationRepository.expirePaymentIfOverdue(participationId, now);
  }

  /** 분철 취소 시 활성 참여 전체를 CANCELLED(BUNCHEOL_CANCELLED) 로 일괄 전이. 호출 측 @Transactional 필수. */
  public int cancelActiveByBuncheolId(final Long buncheolId, final Instant now) {
    return participationRepository.cancelActiveByBuncheolId(buncheolId, now);
  }

  /** 분철 취소 cascade 로 실제 전이된 참여 조회 (취소 알림 대상). cancelActiveByBuncheolId 직후 같은 트랜잭션에서 호출. */
  public List<Participation> findCascadeCancelledByBuncheolId(final Long buncheolId) {
    return participationRepository.findCascadeCancelledByBuncheolId(buncheolId);
  }

  /** 같은 후기 트윗 URL 이 다른 참여의 환급 신청에 이미 쓰였는지 (중복 신청 사전 체크). */
  public boolean isPaybackTweetUrlUsedByOther(final String tweetUrl, final Long participationId) {
    return participationRepository.existsPaybackTweetUrlUsedByOther(tweetUrl, participationId);
  }

  /**
   * 배송비 환급 신청 전이 + 즉시 저장. 상태 위반은 엔티티가 {@link
   * ErrorCode#PAYBACK_STATE_TRANSITION_INVALID}, 트윗 URL 중복은 어댑터가 유니크 위반을 {@link
   * ErrorCode#PAYBACK_TWEET_URL_DUPLICATE} 로 던진다. 호출 측 {@code @Transactional} 필수.
   */
  public void requestPayback(
      final Participation participation, final PaybackTweetUrl tweetUrl, final Instant now) {
    participation.requestPayback(tweetUrl, now);
    participationRepository.savePaybackRequest(participation);
  }

  /**
   * 운영진의 환급 입금 완료 (REQUESTED → COMPLETED CAS). 동시 검수(더블클릭·운영자 중복 처리)는 한 요청만 성공한다. 호출 측
   * {@code @Transactional} 필수.
   */
  public void completePayback(final Long participationId, final Instant now) {
    if (participationRepository.completePaybackIfRequested(participationId, now)) {
      return;
    }
    // CAS 실패 분기: 미존재 참여는 404 로 구분하고, 존재하면 신청 전/이미 완료·반려 상태 위반이다.
    getParticipation(participationId);
    throw new BusinessException(ErrorCode.PAYBACK_STATE_TRANSITION_INVALID);
  }

  /** 운영진의 후기 반려 (REQUESTED → REJECTED CAS). 호출 측 {@code @Transactional} 필수. */
  public void rejectPayback(
      final Long participationId, final String rejectReason, final Instant now) {
    if (rejectReason == null || rejectReason.isBlank()) {
      throw new BusinessException(ErrorCode.PAYBACK_REJECT_REASON_REQUIRED);
    }
    if (participationRepository.rejectPaybackIfRequested(participationId, rejectReason, now)) {
      return;
    }
    // CAS 실패 분기: 미존재 참여는 404 로 구분하고, 존재하면 신청 전/이미 완료·반려 상태 위반이다.
    getParticipation(participationId);
    throw new BusinessException(ErrorCode.PAYBACK_STATE_TRANSITION_INVALID);
  }
}
