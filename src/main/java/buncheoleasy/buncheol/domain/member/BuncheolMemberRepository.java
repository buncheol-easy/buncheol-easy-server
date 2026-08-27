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
}
