package buncheoleasy.admin.dto.response;

import buncheoleasy.buncheol.domain.code.ParticipationCode;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * 발급된 참여 코드 1건.
 *
 * @param status 저장 컬럼이 아니라 조합으로 파생한 값 ({@link ParticipationCode} 참고)
 * @param issuedAtText 운영자가 DM 문안에 그대로 옮겨 적는 KST 표기
 */
public record AdminParticipationCodeResponse(
    Long codeId,
    String code,
    Long buncheolId,
    Long buncheolMemberId,
    String memberName,
    String issuedTo,
    Status status,
    Instant issuedAt,
    Instant expiresAt,
    String issuedAtText,
    String expiresAtText,
    Instant usedAt,
    Long usedParticipationId,
    Instant revokedAt) {

  public enum Status {
    ACTIVE,
    EXPIRED,
    USED,
    REVOKED
  }

  private static final DateTimeFormatter KST_FORMAT =
      DateTimeFormatter.ofPattern("M월 d일(E) HH:mm", Locale.KOREAN)
          .withZone(ZoneId.of("Asia/Seoul"));

  public static AdminParticipationCodeResponse of(
      final ParticipationCode code, final String memberName, final Instant now) {
    return new AdminParticipationCodeResponse(
        code.getId(),
        code.getCode(),
        code.getBuncheolId(),
        code.getBuncheolMemberId(),
        memberName,
        code.getIssuedTo(),
        statusOf(code, now),
        code.getCreatedAt(),
        code.getExpiresAt(),
        format(code.getCreatedAt()),
        format(code.getExpiresAt()),
        code.getUsedAt(),
        code.getUsedParticipationId(),
        code.getRevokedAt());
  }

  private static Status statusOf(final ParticipationCode code, final Instant now) {
    if (code.getUsedAt() != null) {
      return Status.USED;
    }
    if (code.getRevokedAt() != null) {
      return Status.REVOKED;
    }
    return code.getExpiresAt().isAfter(now) ? Status.ACTIVE : Status.EXPIRED;
  }

  private static String format(final Instant instant) {
    return instant == null ? null : KST_FORMAT.format(instant);
  }
}
