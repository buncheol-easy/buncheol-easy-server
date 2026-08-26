package buncheoleasy.buncheol.domain.code;

import buncheoleasy.buncheol.domain.member.BuncheolMember;
import buncheoleasy.global.exception.domain.BusinessException;
import buncheoleasy.global.exception.domain.ErrorCode;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** 참여 코드의 발급·폐기·사용 판정을 단독으로 소유한다. */
@Service
@RequiredArgsConstructor
public class ParticipationCodeDomainService {

  /** 발급 시 기한을 지정하지 않으면 적용하는 기본 유효기간. */
  public static final Duration DEFAULT_VALIDITY = Duration.ofHours(48);

  private final ParticipationCodeRepository participationCodeRepository;
  private final CodeGenerator codeGenerator;

  /**
   * 슬롯 정책과 제출된 코드를 <b>양방향</b>으로 대조해 소모할 코드를 돌려준다 — 필요 없는데 보낸 코드도 거부한다. 조용히 무시하면
   * "코드를 넣었는데 다른 슬롯에 참여됐다" 는 문의를 사후에 재현할 수 없다.
   */
  public Optional<ParticipationCode> validateForParticipation(
      final BuncheolMember member, final String rawCode, final Instant now) {
    boolean submitted = rawCode != null && !rawCode.isBlank();

    if (!member.requiresCode()) {
      if (submitted) {
        throw new BusinessException(ErrorCode.PARTICIPATION_CODE_NOT_APPLICABLE);
      }
      return Optional.empty();
    }
    if (!submitted) {
      throw new BusinessException(ErrorCode.PARTICIPATION_CODE_REQUIRED);
    }

    CodeText normalized = CodeText.parse(rawCode);
    ParticipationCode code =
        participationCodeRepository
            .findByCode(normalized.value())
            .orElseThrow(() -> new BusinessException(ErrorCode.PARTICIPATION_CODE_INVALID));

    requireRedeemable(code.redeemability(member.getId(), now));
    return Optional.of(code);
  }

  /** 실패한 쪽은 참여까지 함께 롤백된다 — 코드 1개로 슬롯 2개가 생기지 않는다. */
  public void consume(final ParticipationCode code, final Long participationId, final Instant now) {
    if (!participationCodeRepository.markUsedIfRedeemable(code.getId(), participationId, now)) {
      throw new BusinessException(ErrorCode.PARTICIPATION_CODE_ALREADY_USED);
    }
  }

  /**
   * 신규 발급. 아직 쓸 수 있는 코드가 있으면 거부한다 (교체는 {@link #reissue}). 만료된 코드는 이미 쓸 수 없으므로 막지 않는다.
   */
  public ParticipationCode issue(
      final BuncheolMember member,
      final String issuedTo,
      final Instant expiresAt,
      final Instant now) {
    requireCodeSlot(member);
    boolean hasUsableCode =
        participationCodeRepository.findOutstandingByBuncheolMemberId(member.getId()).stream()
            .anyMatch(code -> code.isUsable(now));
    if (hasUsableCode) {
      throw new BusinessException(ErrorCode.PARTICIPATION_CODE_SLOT_ALREADY_ISSUED);
    }
    return saveWithFreshCode(member, issuedTo, expiresAt, now);
  }

  /**
   * 재발급 — 남은 코드를 모두 폐기하고 새로 발급한다. 폐기와 발급이 한 트랜잭션이어야 한다 (호출부 {@code @Transactional} 전제)
   * — 나뉘면 폐기만 커밋되고 발급이 실패했을 때 슬롯에 쓸 코드가 없는 채로 남는다.
   */
  public ParticipationCode reissue(
      final BuncheolMember member,
      final String issuedTo,
      final Instant expiresAt,
      final Instant now) {
    requireCodeSlot(member);
    participationCodeRepository.revokeOutstandingByBuncheolMemberId(member.getId(), now);
    return saveWithFreshCode(member, issuedTo, expiresAt, now);
  }

  private static void requireCodeSlot(final BuncheolMember member) {
    if (!member.requiresCode()) {
      throw new BusinessException(ErrorCode.PARTICIPATION_CODE_SLOT_NOT_CODE_ONLY);
    }
  }

  public void revoke(final Long codeId, final Instant now) {
    ParticipationCode code =
        participationCodeRepository
            .findById(codeId)
            .orElseThrow(() -> new BusinessException(ErrorCode.PARTICIPATION_CODE_NOT_FOUND));
    if (!participationCodeRepository.revokeIfActive(code.getId(), now)) {
      throw new BusinessException(ErrorCode.PARTICIPATION_CODE_REVOKE_NOT_ALLOWED);
    }
  }

  public List<ParticipationCode> findAllByBuncheolId(final Long buncheolId) {
    return participationCodeRepository.findAllByBuncheolIdOrderByIdDesc(buncheolId);
  }

  private ParticipationCode saveWithFreshCode(
      final BuncheolMember member,
      final String issuedTo,
      final Instant expiresAt,
      final Instant now) {
    return participationCodeRepository.save(
        ParticipationCode.issue(
            codeGenerator.generate().value(),
            member.getBuncheolId(),
            member.getId(),
            issuedTo,
            expiresAt,
            now));
  }

  // switch "식" 이어야 사유 추가 시 컴파일 에러로 잡힌다 — 문으로 쓰면 새 사유가 조용히 통과한다(fail-open).
  private static void requireRedeemable(final CodeRedeemability redeemability) {
    ErrorCode errorCode =
        switch (redeemability) {
          case REDEEMABLE -> null;
          case SLOT_MISMATCH -> ErrorCode.PARTICIPATION_CODE_INVALID;
          case REVOKED -> ErrorCode.PARTICIPATION_CODE_REVOKED;
          case ALREADY_USED -> ErrorCode.PARTICIPATION_CODE_ALREADY_USED;
          case EXPIRED -> ErrorCode.PARTICIPATION_CODE_EXPIRED;
        };
    if (errorCode != null) {
      throw new BusinessException(errorCode);
    }
  }
}
