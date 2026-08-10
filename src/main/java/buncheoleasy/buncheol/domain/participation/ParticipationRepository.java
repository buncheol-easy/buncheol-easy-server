package buncheoleasy.buncheol.domain.participation;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface ParticipationRepository {

  /**
   * 분철이 모집중이고 마감 전일 때만 참여를 INSERT 한다 (원자적). 멤버 슬롯이 이미 점유돼 있으면(활성 참여 존재) DB unique 제약에 걸려 {@link
   * buncheoleasy.global.exception.domain.ErrorCode#PARTICIPATION_ALREADY_EXISTS} 로 분기한다.
   *
   * @return 모집중이라 INSERT 된 경우 true, 분철이 모집중이 아니거나 마감돼 INSERT 되지 않으면 false
   */
  boolean saveIfRecruiting(Participation participation);

  /**
   * C2C 추가 모집: 분철이 입금 수집중({@code PAYMENT_COLLECTING})일 때만 참여를 INSERT 한다 (원자적 — docs/46 §4.7-E1).
   * 진행확정(CONFIRMED) 이후에는 삽입되지 않는다. 슬롯 점유 충돌 처리는 {@link #saveIfRecruiting} 과 동일.
   *
   * @return 입금 수집중이라 INSERT 된 경우 true, 아니면 false
   */
  boolean saveIfCollecting(Participation participation);

  Optional<Participation> findById(Long id);

  /** 내 참여 목록 (참여자별 최신순). */
  List<Participation> findAllByParticipantIdOrderByCreatedAtDesc(Long participantId);

  /**
   * 참여자에게 아직 끝나지 않은 참여가 하나라도 있는지 (회원탈퇴 가드용). 입금 확인 중({@link
   * ParticipationStatus#AWAITING_PAYMENT})이거나, 입금확인({@link ParticipationStatus#CONFIRMED})됐지만 배송이
   * 끝나지 않았거나({@link buncheoleasy.delivery.domain.DeliveryStatus#finished()} 이전), 배송비 환급 신청이 검수
   * 대기({@link PaybackStatus#REQUESTED}) 중이면 끝나지 않은 참여로 본다. 취소·만료된 참여와 배송·환급 검수까지 끝난 참여는 탈퇴를 막지
   * 않는다.
   *
   * <p>환급 미신청({@link PaybackStatus#NONE})·반려({@link PaybackStatus#REJECTED})는 신청/재신청 창이 남아 있어도
   * 탈퇴를 막지 않는다 — 검수 대기만 차단하는 운영 정책 결정이며, 탈퇴로 환급 자격을 포기하게 된다는 안내는 프론트 탈퇴 확인 모달이 담당한다.
   * 환급 상태는 저장값만 보고 파생 판정(ELIGIBLE/EXPIRED, {@code ShippingFeePaybackPolicy} 소유)은 여기서 재계산하지 않는다.
   */
  boolean existsUnfinishedByParticipantId(Long participantId);

  /**
   * 해당 분철에 참여자의 활성({@link ParticipationStatus#active()}) 참여가 있는지 (분철당 중복 참여 가드용). 취소·만료된 참여는
   * 재참여를 막지 않는다.
   */
  boolean existsActiveByBuncheolIdAndParticipantId(Long buncheolId, Long participantId);

  /**
   * 해당 배송지를 참조하는 활성({@link ParticipationStatus#active()}) 참여가 있는지 (배송지 삭제 가드용). 종료된 참여는 FK ON
   * DELETE SET NULL 로 정리되므로 삭제를 막지 않는다.
   */
  boolean existsActiveByShippingAddressId(Long shippingAddressId);

  /** 여러 분철의 활성 참여 수 집계 (공개 목록의 참여자 수 표시용). */
  List<BuncheolActiveParticipationCount> countActiveByBuncheolIds(List<Long> buncheolIds);

  /**
   * 여러 분철에서 활성({@link ParticipationStatus#active()}) 참여가 점유한 멤버 슬롯 ID({@code buncheol_member_id})
   * 전체 (공개 목록의 "안 팔린 멤버" 계산용). 슬롯 ID 는 분철 간에도 유일하므로 분철별 그룹핑 없이 평면 리스트로 돌려준다.
   */
  List<Long> findActiveBuncheolMemberIds(List<Long> buncheolIds);

  /** 단일 분철의 활성 참여 전체 (호스트 관리 화면 + 분철 취소 시 알림 대상). */
  List<Participation> findActiveByBuncheolId(Long buncheolId);

  /** 단일 분철의 입금확인(CONFIRMED) 참여 전체 (진행확정 시 배송 스냅샷 생성·알림 대상). */
  List<Participation> findConfirmedByBuncheolId(Long buncheolId);

  /** 단일 분철의 입금확인(CONFIRMED) 참여 수 (마감 시 최소 인원 판정용). */
  int countConfirmedByBuncheolId(Long buncheolId);

  /** 입금 만료가 임박/도과한 참여 폴링 (입금 만료 스케줄러용). status=AWAITING_PAYMENT, due_at <= now. */
  List<Participation> findOverduePaymentTargets(Instant now, int limit);

  /**
   * 호스트의 수동 입금확인. AWAITING_PAYMENT 이고 입금 기한(dueAt) 내일 때만 CONFIRMED 로 전이하는 CAS.
   *
   * @return 전이에 성공하면 true, 이미 취소/확정됐거나 기한이 지났으면 false
   */
  boolean confirmPaymentIfAwaiting(Long participationId, Instant now);

  /**
   * 입금 만료 처리. AWAITING_PAYMENT 이고 기한이 지났을 때만 CANCELLED({@link
   * ParticipationCancelReason#PAYMENT_TIMEOUT}) 로 전이하는 CAS (입금 만료 스케줄러용).
   *
   * @return 전이에 성공하면 true
   */
  boolean expirePaymentIfOverdue(Long participationId, Instant now);

  // --- C2C 플로우 CAS (docs/46 §4) ---

  /** C2C "보냈어요" 마킹 CAS (AWAITING_PAYMENT → PAYMENT_SENT, 기한 경과 검사 없음). */
  boolean markPaymentSentIfAwaiting(Long participationId, Instant now);

  /**
   * C2C 마킹 해제 CAS (PAYMENT_SENT → AWAITING_PAYMENT). 참여자 철회(기한 유지)·개최자 반려(기한 연장)가 공용하며 {@code
   * dueAt} 을 함께 세팅한다. {@code paymentSentAt} 은 보존.
   */
  boolean revertPaymentSentIfSent(Long participationId, Instant dueAt, Instant now);

  /** C2C 참여자 자발 취소 CAS — APPLIED·AWAITING_PAYMENT 에서만 USER_CANCELLED 로 전이 (docs/46 §5). */
  boolean cancelByUserIfCancellable(Long participationId, Instant now);

  /** C2C 개최자 수동 입금확인 CAS — AWAITING_PAYMENT·PAYMENT_SENT 에서 기한 경과와 무관하게 CONFIRMED 로 전이. */
  boolean confirmPaymentIfPayable(Long participationId, Instant now);

  /**
   * C2C 성사 확정: 분철의 APPLIED 전건을 일괄 입금 기한과 함께 AWAITING_PAYMENT 로 전이 (docs/46 §4.1).
   *
   * @return 전이된 참여 수
   */
  int startPaymentCollecting(Long buncheolId, Instant dueAt, Instant now);

  /**
   * 분철의 활성 참여를 모두 CANCELLED({@link ParticipationCancelReason#BUNCHEOL_CANCELLED}) 로 일괄 전이한다. 호스트
   * 취소·최소 인원 미달로 분철이 취소될 때 호출된다. 입금확인된 참여도 함께 취소되며 환불은 운영자가 오프라인으로 처리한다.
   *
   * <p>{@code @Modifying} bulk UPDATE 이므로 호출 측 트랜잭션({@code @Transactional}) 이 필수다.
   *
   * @return 취소된 참여 행 수
   */
  int cancelActiveByBuncheolId(Long buncheolId, Instant now);

  /**
   * 분철 취소 cascade({@link ParticipationCancelReason#BUNCHEOL_CANCELLED})로 전이된 참여를 조회한다. {@link
   * #cancelActiveByBuncheolId} 직후 같은 트랜잭션에서 호출해, 그 사이 자발취소·만료된 참여(다른 cancelReason)를 제외한 "실제로 이번에
   * 취소된" 참여에만 알림을 발행하는 데 쓴다.
   */
  List<Participation> findCascadeCancelledByBuncheolId(Long buncheolId);

  /** 같은 후기 트윗 URL 이 다른 참여의 환급 신청에 이미 쓰였는지 (중복 신청 사전 체크용). */
  boolean existsPaybackTweetUrlUsedByOther(String tweetUrl, Long participationId);

  /**
   * 환급 신청({@link Participation#requestPayback}) 변경분을 즉시 flush 해 저장한다. 사전 중복 체크의 check-then-update
   * 갭에서 같은 트윗 URL 이 먼저 커밋됐으면 {@code payback_tweet_url} 유니크 위반을 {@link
   * buncheoleasy.global.exception.domain.ErrorCode#PAYBACK_TWEET_URL_DUPLICATE} 로 변환해 던진다.
   */
  void savePaybackRequest(Participation participation);

  /**
   * 운영진의 환급 입금 완료. REQUESTED 일 때만 COMPLETED 로 전이하는 CAS — 동시 검수(더블클릭·운영자 중복 처리)에서 한 요청만
   * 성공시켜 완료 알림이 중복 발송되지 않게 한다.
   *
   * @return 전이에 성공하면 true, 이미 완료/반려됐거나 신청 전이면 false
   */
  boolean completePaybackIfRequested(Long participationId, Instant now);

  /**
   * 운영진의 후기 반려. REQUESTED 일 때만 사유와 함께 REJECTED 로 전이하는 CAS.
   *
   * @return 전이에 성공하면 true
   */
  boolean rejectPaybackIfRequested(Long participationId, String rejectReason, Instant now);
}
