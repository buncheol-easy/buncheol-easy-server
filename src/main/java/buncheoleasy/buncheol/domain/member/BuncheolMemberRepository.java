package buncheoleasy.buncheol.domain.member;

import java.util.List;
import java.util.Optional;

public interface BuncheolMemberRepository {

  List<BuncheolMember> saveAll(List<BuncheolMember> buncheolMembers);

  void deleteAllByBuncheolId(Long buncheolId);

  Optional<BuncheolMember> findByIdAndBuncheolId(Long id, Long buncheolId);

  List<BuncheolMember> findAllByBuncheolId(Long buncheolId);

  /** 단일 분철의 멤버 슬롯을 등록 순(id ASC)으로 조회. */
  List<BuncheolMember> findAllByBuncheolIdOrderByIdAsc(Long buncheolId);

  List<BuncheolMember> findAllByBuncheolIds(List<Long> buncheolIds);

  /** 전 멤버 슬롯이 0원인 분철 ID (오픈 이벤트 무료 분철 배지 판정용). */
  List<Long> findAllFreeSlotBuncheolIds(List<Long> buncheolIds);

  /**
   * 활성 참여가 없을 때만 전이한다 — 정책을 바꾸는 사이 들어온 참여가 코드 슬롯에 남는 것을 막는다.
   *
   * @return 실제로 전환했으면 true (false = 이미 점유됐거나 슬롯 없음)
   */
  boolean changeAccessTypeIfUnoccupied(
      Long buncheolMemberId, Long buncheolId, BuncheolMemberAccessType accessType);

  void deleteById(Long id);

  /**
   * 자리 하나를 저장한다. 같은 분철에 같은 멤버가 이미 있으면({@code uq_buncheol_members_buncheol_member})
   * {@code BUNCHEOL_MEMBER_ALREADY_EXISTS} 로 바꿔 던진다 — 사전 검사를 빠져나간 경합의 마지막 선이다.
   */
  BuncheolMember save(BuncheolMember buncheolMember);

  Optional<BuncheolMember> findByBuncheolIdAndMemberId(Long buncheolId, Long memberId);

  /**
   * 자리 하나를 공유 잠금 조회(SELECT ... FOR SHARE)한다. 잠금 조회는 트랜잭션 스냅샷이 아니라 최신 커밋을 읽으므로, 분철 락을
   * 기다리는 사이 개최자가 닫은 자리를 놓치지 않는다. 호출 측 트랜잭션이 필수다.
   */
  Optional<BuncheolMember> findByIdAndBuncheolIdForShare(Long id, Long buncheolId);

  /**
   * 닫힌({@code CLOSED}) 자리만 선착순으로 다시 열고 가격을 바꾼다 — 판정과 전이를 한 UPDATE 로 묶는다.
   *
   * @return 다시 열었으면 true (false = 닫힌 자리가 아니거나 그 분철의 자리가 아님)
   */
  boolean reopenIfClosed(Long buncheolMemberId, Long buncheolId, long price);

  /**
   * 분철의 자리를 잠금 조회(SELECT ... FOR UPDATE)한다. 잠금 조회는 트랜잭션 스냅샷이 아니라 최신 커밋을 읽으므로, 분철
   * 락을 기다리는 사이 커밋된 다른 빼기까지 센다. 호출 측 트랜잭션이 필수다.
   */
  List<BuncheolMember> findAllByBuncheolIdForUpdate(Long buncheolId);

  /**
   * 어떤 참여·참여 코드 행도 가리키지 않는 자리만 지운다(상태 무관 — 취소된 참여도 FK 로 자리를 붙잡는다). 판정과 삭제를 한
   * DELETE 로 묶는다. 그래도 자리를 가리키는 FK 가 삭제를 막으면 500 이 아니라 {@code
   * BUNCHEOL_MEMBER_REMOVE_HAS_HISTORY} 로 바꿔 던지고, 그 밖의 무결성 위반은 그대로 올린다.
   *
   * @return 지웠으면 true (false = 참조하는 행이 있거나 그 분철의 자리가 아님)
   */
  boolean deleteIfUnreferenced(Long buncheolMemberId, Long buncheolId);
}
