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
  private static final Pattern ACCOUNT_PATTERN = Pattern.compile("^\\d+$");

  public BankAccount {
    validate(bank, account, holder);
  }

  public static BankAccount of(final String bank, final String account, final String holder) {
    return new BankAccount(bank, account, holder);
  }

  /** 은행·계좌번호·예금주 형식·길이 검증. 환불 계좌({@code RefundAccount}) 등 동일 규칙을 쓰는 VO 에서 재사용한다. */
  public static void validate(final String bank, final String account, final String holder) {
    validateBank(bank);
    validateAccount(account);
    validateHolder(holder);
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
