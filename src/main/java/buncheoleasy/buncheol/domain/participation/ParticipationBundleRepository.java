package buncheoleasy.buncheol.domain.participation;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * 참여 묶음 저장소 포트 (docs/70 §3).
 *
 * <p>P2-b 부터 참여 생성·취소 경로가 이 포트를 호출한다. 읽기(응답·알림)는 아직 {@code participations} 를 본다.
 */
public interface ParticipationBundleRepository {

  ParticipationBundle save(ParticipationBundle bundle);

  Optional<ParticipationBundle> findById(Long id);

  /**
   * 묶음 여러 개를 한 번에 읽는다 (목록 화면용).
   *
   * <p>정본이 묶음으로 옮겨간 뒤(P2-c) 개최 관리·내 참여·어드민 결제 목록이 참여마다 계좌를 필요로 한다. 건별로 읽으면 참여 수만큼
   * 쿼리가 늘어난다(N+1) — 목록은 반드시 이 메서드로 미리 채운 뒤 맵으로 조회할 것.
   */
  List<ParticipationBundle> findAllByIds(Collection<Long> ids);

  /**
   * 이 사람이 이 분철에서 지금 열고 있는 묶음 ({@code closed_at IS NULL}).
   *
   * <p><b>활성 묶음이 2개일 수 있다</b> — 추가 모집분이 새 묶음이기 때문이다(docs/71 §8-3). 그래서 반환이 단건이 아니라 목록이다.
   * DB 유니크로 1개를 강제하지 않기로 한 결정의 직접적 귀결이므로, 호출부는 "하나뿐" 을 전제하면 안 된다.
   */
  List<ParticipationBundle> findActiveByBuncheolIdAndParticipantId(
      Long buncheolId, Long participantId);

  /** 분철의 묶음 전체 (개최 관리·백필 검증용). */
  List<ParticipationBundle> findAllByBuncheolId(Long buncheolId);

  /**
   * 활성 슬롯이 하나도 남지 않았으면 묶음을 닫는다 (CAS). 이미 닫혔거나 살아 있는 슬롯이 있으면 {@code false}.
   *
   * <p>🔴 <b>"세어 보고 0이면 닫는다" 로 짜면 안 된다.</b> 같은 묶음의 두 슬롯이 동시에 취소되면 두 트랜잭션 모두 REPEATABLE
   * READ 스냅샷에서 <b>상대의 취소를 보지 못해</b> 각자 "아직 하나 남았다" 로 판단하고 <b>둘 다 닫지 않는다</b>. 죽었는데
   * 활성인 묶음이 남고, 그 사람이 재참여하면 <b>시체 묶음을 재사용해 택배가 옛 주소로 나간다</b>. 존재 판정을 UPDATE 의
   * WHERE 서브쿼리(current read)로 묶어야 한다 — {@code Buncheol#confirmIfAllCollected} 가 같은 기법을 쓴다.
   */
  boolean closeIfNoActiveSlots(Long bundleId, Instant now);

  /**
   * 분철의 묶음 중 활성 슬롯이 없는 것을 일괄로 닫는다 (분철 취소 cascade·자동 마감용). 반환은 닫힌 묶음 수.
   *
   * <p>cascade 는 참여를 한 번의 UPDATE 로 전이시켜 어떤 묶음이 비었는지 개별로 알 수 없다. 같은 CAS 조건을 분철 범위로
   * 넓혀 한 번에 판정한다.
   */
  int closeEmptyByBuncheolId(Long buncheolId, Instant now);

  /** 성사 확정 시 분철의 활성 묶음에 입금 기한을 일괄로 채운다 (기한 없이 열린 C2C 신청 묶음 대상). 반환은 채워진 묶음 수. */
  int assignDueAtByBuncheolId(Long buncheolId, Instant dueAt, Instant now);
}
