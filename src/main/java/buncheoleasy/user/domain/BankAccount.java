package buncheoleasy.user.domain;

import buncheoleasy.global.exception.domain.BusinessException;
import buncheoleasy.global.exception.domain.ErrorCode;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

@Embeddable
public record BankAccount(
    @Column(name = "settlement_bank", length = 50) String bank,
    @Column(name = "settlement_account", length = 50) String account,
    @Column(name = "settlement_holder", length = 50) String holder) {

  private static final int BANK_MAX_LENGTH = 50;
  private static final int ACCOUNT_MAX_LENGTH = 50;
  private static final int HOLDER_MAX_LENGTH = 50;

  public BankAccount {
    validateBank(bank);
    validateAccount(account);
    validateHolder(holder);
  }

  public static BankAccount of(final String bank, final String account, final String holder) {
    return new BankAccount(bank, account, holder);
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
