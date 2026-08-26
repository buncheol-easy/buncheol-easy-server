package buncheoleasy.buncheol.infrastructure.code;

import buncheoleasy.buncheol.domain.code.ParticipationCode;
import buncheoleasy.buncheol.domain.code.ParticipationCodeRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class JpaParticipationCodeRepositoryAdapter implements ParticipationCodeRepository {

  private final JpaParticipationCodeRepository jpaParticipationCodeRepository;

  /**
   * 발급 저장. 남은 유니크는 코드 문자열뿐이고 32^8 공간에서 충돌은 사실상 없으므로, 위반이 나면 잡지 않고 그대로 올려 이상 신호로 남긴다.
   */
  @Override
  public ParticipationCode save(final ParticipationCode code) {
    return jpaParticipationCodeRepository.saveAndFlush(code);
  }

  @Override
  public Optional<ParticipationCode> findByCode(final String code) {
    return jpaParticipationCodeRepository.findByCode(code);
  }

  @Override
  public Optional<ParticipationCode> findById(final Long id) {
    return jpaParticipationCodeRepository.findById(id);
  }

  @Override
  public List<ParticipationCode> findAllByBuncheolIdOrderByIdDesc(final Long buncheolId) {
    return jpaParticipationCodeRepository.findAllByBuncheolIdOrderByIdDesc(buncheolId);
  }

  @Override
  public List<ParticipationCode> findOutstandingByBuncheolMemberId(final Long buncheolMemberId) {
    return jpaParticipationCodeRepository
        .findByBuncheolMemberIdAndUsedAtIsNullAndRevokedAtIsNullOrderByIdDesc(buncheolMemberId);
  }

  @Override
  public int revokeOutstandingByBuncheolMemberId(
      final Long buncheolMemberId, final Instant now) {
    return jpaParticipationCodeRepository.revokeOutstandingByBuncheolMemberId(
        buncheolMemberId, now);
  }

  @Override
  public boolean markUsedIfRedeemable(
      final Long codeId, final Long participationId, final Instant now) {
    return jpaParticipationCodeRepository.markUsedIfRedeemable(codeId, participationId, now) > 0;
  }

  @Override
  public boolean revokeIfActive(final Long codeId, final Instant now) {
    return jpaParticipationCodeRepository.revokeIfActive(codeId, now) > 0;
  }
}
