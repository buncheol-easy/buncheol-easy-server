package buncheoleasy.buncheol.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import buncheoleasy.global.exception.domain.BusinessException;
import buncheoleasy.global.exception.domain.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

@DisplayName("SettlementInfo 도메인 테스트")
class SettlementInfoTest {

  private static final String VALID_BANK = "국민은행";
  private static final String VALID_ACCOUNT = "123-456-789012";
  private static final String VALID_HOLDER = "홍길동";

  @Nested
  @DisplayName("SettlementInfo 생성 테스트")
  class CreateTest {

    @Test
    void 유효한_정산_정보로_생성에_성공한다() {
      // when
      SettlementInfo info = SettlementInfo.of(VALID_BANK, VALID_ACCOUNT, VALID_HOLDER);

      // then
      assertThat(info.bank()).isEqualTo(VALID_BANK);
      assertThat(info.account()).isEqualTo(VALID_ACCOUNT);
      assertThat(info.holder()).isEqualTo(VALID_HOLDER);
    }
  }

  @Nested
  @DisplayName("은행명 검증 테스트")
  class ValidateBankTest {

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    void 은행명이_null이거나_빈_값이면_예외가_발생한다(String bank) {
      // when & then
      assertThatThrownBy(() -> SettlementInfo.of(bank, VALID_ACCOUNT, VALID_HOLDER))
          .isInstanceOf(BusinessException.class)
          .extracting("errorCode")
          .isEqualTo(ErrorCode.BUNCHEOL_REQUIRED_FIELD_MISSING);
    }

    @Test
    void 은행명이_최대_길이를_초과하면_예외가_발생한다() {
      // given
      String longBank = "가".repeat(51);

      // when & then
      assertThatThrownBy(() -> SettlementInfo.of(longBank, VALID_ACCOUNT, VALID_HOLDER))
          .isInstanceOf(BusinessException.class)
          .extracting("errorCode")
          .isEqualTo(ErrorCode.BUNCHEOL_SETTLEMENT_INFO_LENGTH_INVALID);
    }

    @Test
    void 은행명이_최대_길이_이하면_유효하다() {
      // given
      String maxLengthBank = "가".repeat(50);

      // when & then
      assertThatCode(() -> SettlementInfo.of(maxLengthBank, VALID_ACCOUNT, VALID_HOLDER))
          .doesNotThrowAnyException();
    }
  }

  @Nested
  @DisplayName("계좌번호 검증 테스트")
  class ValidateAccountTest {

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    void 계좌번호가_null이거나_빈_값이면_예외가_발생한다(String account) {
      // when & then
      assertThatThrownBy(() -> SettlementInfo.of(VALID_BANK, account, VALID_HOLDER))
          .isInstanceOf(BusinessException.class)
          .extracting("errorCode")
          .isEqualTo(ErrorCode.BUNCHEOL_REQUIRED_FIELD_MISSING);
    }

    @Test
    void 계좌번호가_최대_길이를_초과하면_예외가_발생한다() {
      // given
      String longAccount = "1".repeat(51);

      // when & then
      assertThatThrownBy(() -> SettlementInfo.of(VALID_BANK, longAccount, VALID_HOLDER))
          .isInstanceOf(BusinessException.class)
          .extracting("errorCode")
          .isEqualTo(ErrorCode.BUNCHEOL_SETTLEMENT_INFO_LENGTH_INVALID);
    }
  }

  @Nested
  @DisplayName("예금주 검증 테스트")
  class ValidateHolderTest {

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    void 예금주가_null이거나_빈_값이면_예외가_발생한다(String holder) {
      // when & then
      assertThatThrownBy(() -> SettlementInfo.of(VALID_BANK, VALID_ACCOUNT, holder))
          .isInstanceOf(BusinessException.class)
          .extracting("errorCode")
          .isEqualTo(ErrorCode.BUNCHEOL_REQUIRED_FIELD_MISSING);
    }

    @Test
    void 예금주가_최대_길이를_초과하면_예외가_발생한다() {
      // given
      String longHolder = "가".repeat(51);

      // when & then
      assertThatThrownBy(() -> SettlementInfo.of(VALID_BANK, VALID_ACCOUNT, longHolder))
          .isInstanceOf(BusinessException.class)
          .extracting("errorCode")
          .isEqualTo(ErrorCode.BUNCHEOL_SETTLEMENT_INFO_LENGTH_INVALID);
    }
  }
}
