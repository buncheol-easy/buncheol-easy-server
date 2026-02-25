package buncheoleasy.buncheol.domain;

import buncheoleasy.global.exception.domain.BusinessException;
import buncheoleasy.global.exception.domain.ErrorCode;

public record SettlementInfo(String bank, String account, String holder) {

    private static final int BANK_MAX_LENGTH = 50;
    private static final int ACCOUNT_MAX_LENGTH = 50;
    private static final int HOLDER_MAX_LENGTH = 50;

    public SettlementInfo {
        validateBank(bank);
        validateAccount(account);
        validateHolder(holder);
    }

    public static SettlementInfo of(final String bank, final String account, final String holder) {
        return new SettlementInfo(bank, account, holder);
    }

    private void validateBank(final String value) {
        if (value == null || value.isBlank()) {
            throw new BusinessException(ErrorCode.BUNCHEOL_REQUIRED_FIELD_MISSING);
        }
        if (value.length() > BANK_MAX_LENGTH) {
            throw new BusinessException(ErrorCode.BUNCHEOL_SETTLEMENT_INFO_LENGTH_INVALID);
        }
    }

    private void validateAccount(final String value) {
        if (value == null || value.isBlank()) {
            throw new BusinessException(ErrorCode.BUNCHEOL_REQUIRED_FIELD_MISSING);
        }
        if (value.length() > ACCOUNT_MAX_LENGTH) {
            throw new BusinessException(ErrorCode.BUNCHEOL_SETTLEMENT_INFO_LENGTH_INVALID);
        }
    }

    private void validateHolder(final String value) {
        if (value == null || value.isBlank()) {
            throw new BusinessException(ErrorCode.BUNCHEOL_REQUIRED_FIELD_MISSING);
        }
        if (value.length() > HOLDER_MAX_LENGTH) {
            throw new BusinessException(ErrorCode.BUNCHEOL_SETTLEMENT_INFO_LENGTH_INVALID);
        }
    }
}
