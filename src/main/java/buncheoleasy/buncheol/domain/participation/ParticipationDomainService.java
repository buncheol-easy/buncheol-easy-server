package buncheoleasy.buncheol.domain.participation;

import buncheoleasy.global.exception.domain.BusinessException;
import buncheoleasy.global.exception.domain.ErrorCode;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ParticipationDomainService {

  private final ParticipationRepository participationRepository;

  public boolean createParticipationIfRecruiting(final Participation participation) {
    return participationRepository.saveIfRecruiting(participation);
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

  /** 분철의 입금확인된 참여자 수. 마감 시 최소 인원 판정용. */
  public int countConfirmedByBuncheolId(final Long buncheolId) {
    return participationRepository.countConfirmedByBuncheolId(buncheolId);
  }

  public boolean hasActiveParticipationBy(final Long participantId) {
    return participationRepository.existsActiveByParticipantId(participantId);
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
   * 참여자 본인의 자발적 취소 (AWAITING_PAYMENT → CANCELLED CAS). 이미 확정/취소됐으면 상태 위반.
   *
   * <p>입금확인(confirmPayment)과 달리 dueAt 가드를 두지 않는다 — 입금확인은 30분 칼컷 대상이지만, 참여 포기는 시점과 무관하게 항상
   * 허용한다. 만료 시각이 지났지만 스케줄러가 아직 안 돈 창에서 유저가 직접 취소하면 사유는 (만료가 아니라) 명시적 사용자 액션인 SELF_CANCELLED 로
   * 기록되는 것이 정확하다.
   */
  public void cancelByParticipant(final Long participationId, final Instant now) {
    if (!participationRepository.cancelByParticipantIfAwaiting(participationId, now)) {
      throw new BusinessException(ErrorCode.PARTICIPATION_STATE_TRANSITION_INVALID);
    }
  }

  /** 입금 만료 처리 (입금 만료 스케줄러용). 멱등하며 실패 시(이미 확정/취소) 예외 없이 false 를 돌려준다. */
  public boolean expirePayment(final Long participationId, final Instant now) {
    return participationRepository.expirePaymentIfOverdue(participationId, now);
  }

  /** 분철 취소 시 활성 참여 전체를 CANCELLED(BUNCHEOL_CANCELLED) 로 일괄 전이. 호출 측 @Transactional 필수. */
  public int cancelActiveByBuncheolId(final Long buncheolId, final Instant now) {
    return participationRepository.cancelActiveByBuncheolId(buncheolId, now);
  }
}
