package buncheoleasy.buncheol.domain.code;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface ParticipationCodeRepository {

  ParticipationCode save(ParticipationCode code);

  Optional<ParticipationCode> findByCode(String code);

  Optional<ParticipationCode> findById(Long id);

  /** 분철의 발급 이력 전체 (최신순). 발급 건수가 슬롯 수 남짓이라 페이지네이션 없이 내려준다. */
  List<ParticipationCode> findAllByBuncheolIdOrderByIdDesc(Long buncheolId);

  /** 슬롯의 미사용·미폐기 코드 (만료 포함, 최신순). */
  List<ParticipationCode> findOutstandingByBuncheolMemberId(Long buncheolMemberId);

  /** @return 폐기된 건수 */
  int revokeOutstandingByBuncheolMemberId(Long buncheolMemberId, Instant now);

  /**
   * 코드 소모 CAS. 사전 검증과 이 사이에 같은 코드로 동시 참여가 들어와도 한쪽만 성공한다.
   *
   * @return 실제로 소모했으면 true
   */
  boolean markUsedIfRedeemable(Long codeId, Long participationId, Instant now);

  /** @return 실제로 폐기했으면 true (이미 사용·폐기된 코드는 false) */
  boolean revokeIfActive(Long codeId, Instant now);
}
