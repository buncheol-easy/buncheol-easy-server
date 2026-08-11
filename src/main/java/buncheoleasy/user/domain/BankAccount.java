package buncheoleasy.user.domain;

import buncheoleasy.global.exception.domain.BusinessException;
import buncheoleasy.global.exception.domain.ErrorCode;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.util.regex.Pattern;

// TODO: 정산 계좌번호는 민감 정보. 로깅·응답·외부 노출 시점에 마스킹 처리 필요 (예: 끝 4자리만 노출).
@Embeddable
public record BankAccount(
    @Column(name = "settlement_bank", length = 50) String bank,
    @Column(name = "settlement_account", length = 50) String account,
    @Column(name = "settlement_holder", length = 50) String holder) {

  private static final int BANK_MAX_LENGTH = 50;
  private static final int ACCOUNT_MAX_LENGTH = 50;
  private static final int HOLDER_MAX_LENGTH = 50;
  private static final int ACCOUNT_MIN_DIGITS = 8;
  private static final Pattern ACCOUNT_PATTERN = Pattern.compile("^\\d+(-\\d+)*$");

  public BankAccount {
    validate(bank, account, holder);
  }

  public static BankAccount of(final String bank, final String account, final String holder) {
    return new BankAccount(bank, account, holder);
  }

  /**
   * 은행·계좌번호·예금주 형식·길이 검증. 환불 계좌({@code RefundAccount}) 등 동일 규칙을 쓰는 VO 에서 재사용한다.
   *
   * <p>이 VO 는 record 라 JPA 가 조회 시에도 생성자를 태운다 — 즉 여기 규칙을 조이면 <b>기존 저장 행을 읽는 것 자체가 깨진다</b>. 그래서
   * 신규 입력에만 적용할 규칙은 {@link #validateForRegistration} 에 둔다 (docs/53 Q-02, 2026-08-12: 기존 계좌 마이그레이션 없이
   * 신규만 적용 결정).
   */
  public static void validate(final String bank, final String account, final String holder) {
    validateBank(bank);
    validateAccount(account);
    validateHolder(holder);
  }

  /**
   * 신규 등록·수정 입력에만 적용하는 강화 검증 (docs/53 Q-02). {@link #validate} 에 계좌번호 최소 자릿수를 더한다.
   *
   * <p>기존 저장 행에는 적용하지 않는다 — staging 의 {@code 오 / 111 / 아아아} 같은 값이 이미 있고, 조회 경로에서 터지면 안 된다. 은행명은
   * 자유 입력을 유지한다(선택형 전환은 보류).
   */
  public static void validateForRegistration(
      final String bank, final String account, final String holder) {
    validate(bank, account, holder);
    validateAccountMinDigits(account);
  }

  private static void validateAccountMinDigits(final String value) {
    // 하이픈은 은행마다 표기가 달라 자릿수 판정에서 제외한다.
    if (value.replace("-", "").length() < ACCOUNT_MIN_DIGITS) {
      throw new BusinessException(ErrorCode.USER_BANK_ACCOUNT_TOO_SHORT);
    }
  }

  private static void validateBank(final String value) {
    if (value == null || value.isBlank()) {
      throw new BusinessException(ErrorCode.USER_BANK_ACCOUNT_REQUIRED);
    }
    if (value.length() > BANK_MAX_LENGTH) {
      throw new BusinessException(ErrorCode.USER_BANK_ACCOUNT_LENGTH_INVALID);
    }
  }

  private static void validateAccount(final String value) {
    if (value == null || value.isBlank()) {
      throw new BusinessException(ErrorCode.USER_BANK_ACCOUNT_REQUIRED);
    }
    if (value.length() > ACCOUNT_MAX_LENGTH) {
      throw new BusinessException(ErrorCode.USER_BANK_ACCOUNT_LENGTH_INVALID);
    }
    if (!ACCOUNT_PATTERN.matcher(value).matches()) {
      throw new BusinessException(ErrorCode.USER_BANK_ACCOUNT_FORMAT_INVALID);
    }
  }

  private static void validateHolder(final String value) {
    if (value == null || value.isBlank()) {
      throw new BusinessException(ErrorCode.USER_BANK_ACCOUNT_REQUIRED);
    }
    if (value.length() > HOLDER_MAX_LENGTH) {
      throw new BusinessException(ErrorCode.USER_BANK_ACCOUNT_LENGTH_INVALID);
    }
  }
}
